package com.vanter.ember.session.service;

import com.vanter.ember.catalog.model.MenuItem;
import com.vanter.ember.catalog.model.dto.MenuItemResponse;
import com.vanter.ember.catalog.repository.MenuItemRepository;
import com.vanter.ember.catalog.service.MenuItemService;
import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.kitchen.event.KitchenItemUpdated;
import com.vanter.ember.session.dto.OrderItemDto;
import com.vanter.ember.session.dto.ParticipantDto;
import com.vanter.ember.session.dto.SessionDetailResponseDto;
import com.vanter.ember.session.event.*;
import com.vanter.ember.session.exception.TooManyParticipantsException;
import com.vanter.ember.session.model.OrderItem;
import com.vanter.ember.session.model.OrderItemStatus;
import com.vanter.ember.session.model.Participant;
import com.vanter.ember.session.model.Session;
import com.vanter.ember.session.model.SessionStatus;
import com.vanter.ember.session.repository.SessionRepository;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.vanter.ember.settings.repository.DiningTableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final SessionRepository sessionRepository;
    private final DiningTableRepository diningTableRepository;
    private final MenuItemService menuItemService;
    private final ApplicationEventPublisher eventPublisher;
    private final QrTokenService qrTokenService;
    private final UserRepository userRepository;
    private final MenuItemRepository menuItemRepository;

    public Session findById(String sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));
    }

    public SessionDetailResponseDto getSessionDetails(String sessionId) {
        var session = findById(sessionId);

        var table = diningTableRepository.findById(session.getTableId()).orElseThrow(() -> new ResourceNotFoundException("Table not found: " + sessionId));
        Integer tableNumber = table.getTableNumber();

        var ParticipantList = session.getParticipants().stream().map(
                (p -> new ParticipantDto(
                        p.getUserId(),
                        p.getName()
                ))
        ).toList();

        var ItemList = session.getItems().stream().map(
                (i -> new OrderItemDto(
                        i.getId(),
                        i.getName(),
                        i.getPrice(),
                        i.getParticipantName(),
                        i.getParticipantId(),
                        i.getStatus(),
                        i.getAddedAt()
                ))
        ).toList();

        return new SessionDetailResponseDto(
                session.getId(),
                session.getTableId(),
                tableNumber,
                session.getStatus() != SessionStatus.CLOSED,
                session.getWaiterId(),
                session.getStatus(),
                session.getMaxParticipants(),
                ParticipantList,
                ItemList,
                session.getCreatedAt()
        );
    }

    public Session createSession(UUID tableId, String waiterId, int maxParticipants) {
        var table = diningTableRepository.findById(tableId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + tableId));

        var OpenSession = sessionRepository.findByTableIdAndStatus(tableId, SessionStatus.OPEN);

        if (!OpenSession.isEmpty()) {
            throw new IllegalStateException(
                    "Table " + table.getTableNumber() + " is already occupied");
        }

        Session session = sessionRepository.save(Session.builder()
                .tableId(tableId)
                .waiterId(waiterId)
                .status(SessionStatus.OPEN)
                .maxParticipants(maxParticipants)
                .createdAt(LocalDateTime.now())
                .joinCode(generateJoinCode())
                .build());

        eventPublisher.publishEvent(
                new SessionOpened(session.getId(), tableId, table.getTableNumber()));

        return session;
    }

    public Session joinSession(String qrToken, String userId, String userName) {
        String sessionId = qrTokenService.validateQrToken(qrToken);
        int maxParticipants = qrTokenService.extractMaxParticipants(qrToken);

        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));

        if (session.getParticipants().size() >= maxParticipants) {
            throw new TooManyParticipantsException(
                    "Session " + sessionId + " is at full capacity (" + maxParticipants + ")");
        }

        boolean alreadyJoined = session.getParticipants().stream()
                .anyMatch(p -> p.getUserId().equals(userId));
        if (alreadyJoined) {
            throw new IllegalStateException(
                    "User " + userId + " has already joined this session");
        }

        session.getParticipants().add(Participant.builder().userId(userId).name(userName).build());
        Session saved = sessionRepository.save(session);

        eventPublisher.publishEvent(new ParticipantJoined(saved.getId(), userId, userName));
        return saved;
    }

    public Session joinSessionCode(String joinCode, String userId ) {

        var user = userRepository.findByEmail(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        Session session = sessionRepository.findByJoinCodeAndStatus(joinCode, SessionStatus.OPEN)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,  "Code not found" + joinCode));


        boolean alreadyJoin = session.getParticipants().stream().anyMatch(p -> p.getUserId().equals(user.getId()));
        if (alreadyJoin) {
            return session;
        }

        session.getParticipants().add(Participant.builder().userId(user.getId()).name(user.getName()).build());
        Session saved = sessionRepository.save(session);
        eventPublisher.publishEvent(new ParticipantJoined(saved.getId(), user.getId(), user.getName()));
        return saved;
    }

    private String generateJoinCode(){
        String characters = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder();
        SecureRandom random = new SecureRandom();

        for (int i = 0; i < 5; i++) {
            int index  = random.nextInt(characters.length());
            sb.append(characters.charAt(index));
        }

        return sb.toString();
    }

    public Session expandCapacity(String sessionId, String requestingWaiter, int additional) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));

        if (!session.getWaiterId().equals(requestingWaiter)) {
            throw new IllegalStateException(
                    "Only the assigned waiter is authorized to expand capacity");
        }

        session.setMaxParticipants(session.getMaxParticipants() + additional);
        return sessionRepository.save(session);
    }

    private static final int SESSION_TIMEOUT_HOURS = 8;

    public Session addItem(String sessionId, String participantId, Long menuItemId) {
        Session session = findById(sessionId);

        if (session.getStatus() == SessionStatus.CLOSED) {
            throw new IllegalStateException("Cannot add items to a closed session");
        }

        if (session.getCreatedAt() != null &&
                session.getCreatedAt().isBefore(LocalDateTime.now().minusHours(SESSION_TIMEOUT_HOURS))) {
            throw new IllegalStateException("Session " + sessionId + " has expired");
        }

        var user = userRepository.findByEmail(participantId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + participantId));

        Participant participant = session.getParticipants().stream()
                .filter(p -> p.getUserId().equals(user.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        participantId + " is not a participant of session " + sessionId));

        MenuItemResponse menuItem = menuItemService.findById(menuItemId);
        if (!menuItem.isAvailable()) {
            throw new IllegalStateException("Menu item " + menuItemId + " is not available");
        }

        OrderItem newItem = OrderItem.builder()
                .id(UUID.randomUUID().toString())
                .itemId(menuItem.getId())
                .name(menuItem.getName())
                .price(menuItem.getPrice())
                .participantId(participant.getUserId())
                .participantName(participant.getName())
                .status(OrderItemStatus.DRAFT)
                .addedAt(LocalDateTime.now())
                .build();
        session.getItems().add(newItem);

        Session saved = sessionRepository.save(session);

        eventPublisher.publishEvent(new ItemAdded(
                saved.getId(),
                newItem.getName(),
                newItem.getPrice(),
                newItem.getParticipantName(),
                newItem.getStatus(),
                saved.getItems()));

        return saved;
    }

    @EventListener
    public void handleKitchenItemUpdated(KitchenItemUpdated event) {
        Session session = findById(event.sessionId());

        OrderItem item = session.getItems().stream()
                .filter(i -> event.itemId().equals(i.getId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Item not found: " + event.itemId()));

        item.setStatus(event.newStatus());
        sessionRepository.save(session);
        eventPublisher.publishEvent(new ItemStatusUpdated(
                event.sessionId(),
                item.getId(),
                item.getName(),
                item.getParticipantName(),
                event.newStatus()));
    }

    public void closeSession(String sessionId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));
        session.setStatus(SessionStatus.CLOSED);
        sessionRepository.save(session);

        eventPublisher.publishEvent(new SessionClosed(
                session.getId(), session.getTableId(), session.getStatus()
        ));
    }

    public void closeEmptySession(String sessionId){
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));


        boolean orderConfirmed = session.getItems().stream().anyMatch((item) -> item.getStatus() != OrderItemStatus.DRAFT);

        if(orderConfirmed){
            throw new IllegalStateException("Cannot cancel session with billable items");
        }

        this.closeSession(sessionId);

    }

    public Session removeItem(String sessionId, String orderItemId, String requesterId) {
        Session session = findById(sessionId);

        OrderItem item = session.getItems().stream()
                .filter(i -> orderItemId.equals(i.getId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Item not found: " + orderItemId));

        if (item.getStatus() == OrderItemStatus.PREPARING) {
            throw new IllegalStateException(
                    "Cannot remove item '" + item.getName() + "' as it is already being prepared");
        }

        var user = userRepository.findByEmail(requesterId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + requesterId));

        boolean isWaiter = session.getWaiterId().equals(user.getEmail());

        boolean isOwner = item.getParticipantId().equals(user.getId());
        if (!isWaiter && !isOwner) {
            throw new IllegalStateException(
                    "Only the item's owner or the session waiter can remove it" );
        }

        session.getItems().remove(item);

        eventPublisher.publishEvent(new DeleteItem(
                session.getId(), item.getId()
        ));

        return sessionRepository.save(session);


    }

    public void confirmDraftsForUser(String sessionId, String userId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));

        List<OrderItem> drafts = session.getItems().stream()
                .filter(item  -> item.getParticipantId().equals(userId))
                .filter(item -> item.getStatus() == OrderItemStatus.DRAFT)
                .toList();

        if(!drafts.isEmpty()){
            drafts.forEach(item -> {
                item.setStatus(OrderItemStatus.PENDING);
                item.setAddedAt(LocalDateTime.now());
            });

            Session savedSession = sessionRepository.save(session);
            eventPublisher.publishEvent(new ItemSent(savedSession));

            int tableNumber = diningTableRepository.findById(savedSession.getTableId())
                    .orElseThrow(() -> new ResourceNotFoundException("Table not found"))
                    .getTableNumber();

            eventPublisher.publishEvent(new KitchenItemsConfirmed(
                    savedSession.getId(),
                    tableNumber,
                    drafts
            ));
        }
    }

    public SessionStatus getSessionStatus(String id) {

        Session session = sessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + id));
        return session.getStatus();

    }
}
