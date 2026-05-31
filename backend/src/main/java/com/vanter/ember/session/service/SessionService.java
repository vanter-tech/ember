package com.vanter.ember.session.service;

import com.vanter.ember.catalog.model.MenuItem;
import com.vanter.ember.catalog.model.TableStatus;
import com.vanter.ember.catalog.model.dto.MenuItemResponse;
import com.vanter.ember.catalog.service.MenuItemService;
import com.vanter.ember.catalog.service.RestaurantTableService;
import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.kitchen.event.KitchenItemUpdated;
import com.vanter.ember.session.event.ItemAdded;
import com.vanter.ember.session.event.ItemStatusUpdated;
import com.vanter.ember.session.event.OrderItemAdded;
import com.vanter.ember.session.event.ParticipantJoined;
import com.vanter.ember.session.event.SessionOpened;
import com.vanter.ember.session.exception.TooManyParticipantsException;
import com.vanter.ember.session.model.OrderItem;
import com.vanter.ember.session.model.OrderItemStatus;
import com.vanter.ember.session.model.Participant;
import com.vanter.ember.session.model.Session;
import com.vanter.ember.session.model.SessionStatus;
import com.vanter.ember.session.repository.SessionRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final SessionRepository sessionRepository;
    private final RestaurantTableService tableService;
    private final MenuItemService menuItemService;
    private final ApplicationEventPublisher eventPublisher;
    private final QrTokenService qrTokenService;

    public Session findById(String sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));
    }

    public Session createSession(Long tableId, String waiterId, int maxParticipants) {
        var table = tableService.findById(tableId);
        if (table.getStatus() == TableStatus.OCCUPIED) {
            throw new IllegalStateException(
                    "Table " + table.getNumber() + " is already occupied");
        }

        Session session = sessionRepository.save(Session.builder()
                .tableId(tableId)
                .waiterId(waiterId)
                .status(SessionStatus.OPEN)
                .maxParticipants(maxParticipants)
                .createdAt(LocalDateTime.now())
                .build());

        eventPublisher.publishEvent(
                new SessionOpened(session.getId(), tableId, table.getNumber()));

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

        Participant participant = session.getParticipants().stream()
                .filter(p -> p.getUserId().equals(participantId))
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
                .participantId(participantId)
                .participantName(participant.getName())
                .status(OrderItemStatus.PENDING)
                .addedAt(LocalDateTime.now())
                .build();
        session.getItems().add(newItem);

        Session saved = sessionRepository.save(session);
        int tableNumber = tableService.findById(saved.getTableId()).getNumber();

        eventPublisher.publishEvent(new ItemAdded(
                saved.getId(),
                newItem.getName(),
                newItem.getPrice(),
                newItem.getParticipantName(),
                newItem.getStatus(),
                saved.getItems()));

        eventPublisher.publishEvent(new OrderItemAdded(
                saved.getId(),
                newItem.getId(),
                tableNumber,
                newItem.getItemId(),
                newItem.getName(),
                newItem.getPrice(),
                newItem.getParticipantName()));

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

        boolean isWaiter = session.getWaiterId().equals(requesterId);
        boolean isOwner = item.getParticipantId().equals(requesterId);
        if (!isWaiter && !isOwner) {
            throw new IllegalStateException(
                    "Only the item's owner or the session waiter can remove it");
        }

        session.getItems().remove(item);
        return sessionRepository.save(session);
    }
}
