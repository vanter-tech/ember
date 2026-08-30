package com.vanter.ember.session.service;

import com.vanter.ember.catalog.model.MenuItem;
import com.vanter.ember.catalog.model.dto.MenuItemResponse;
import com.vanter.ember.catalog.model.dto.ModifierGroupResponse;
import com.vanter.ember.catalog.model.dto.ModifierOptionResponse;
import com.vanter.ember.catalog.repository.MenuItemRepository;
import com.vanter.ember.catalog.service.MenuItemService;
import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.kitchen.event.KitchenItemUpdated;
import com.vanter.ember.restaurant.model.RestaurantStatus;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import com.vanter.ember.session.dto.OrderItemDto;
import com.vanter.ember.session.dto.ParticipantDto;
import com.vanter.ember.session.dto.SessionActivityDto;
import com.vanter.ember.session.dto.SessionDetailResponseDto;
import com.vanter.ember.session.event.*;
import com.vanter.ember.session.exception.InvalidModifierSelectionException;
import com.vanter.ember.session.exception.TooManyParticipantsException;
import com.vanter.ember.session.model.OrderItem;
import com.vanter.ember.session.model.OrderItemStatus;
import com.vanter.ember.session.model.Participant;
import com.vanter.ember.session.model.SelectedModifier;
import com.vanter.ember.session.model.Session;
import com.vanter.ember.session.model.SessionActivity;
import com.vanter.ember.session.model.SessionStatus;
import com.vanter.ember.session.repository.SessionRepository;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.vanter.ember.settings.model.DiningTables;
import com.vanter.ember.settings.repository.DiningTableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
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
    private final RestaurantRepository restaurantRepository;

    /**
     * Loads a session scoped to the tenant bound to the current request: a session owned by another
     * restaurant is indistinguishable from a nonexistent one.
     */
    public Session findById(String sessionId) {
        return sessionRepository.findByIdAndTenantId(sessionId, TenantContextHolder.requireTenantId())
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
                        i.getModifiers(),
                        i.getAddedAt()
                ))
        ).toList();

        var ActivityList = session.getActivityLog().stream().map(
                (a -> new SessionActivityDto(
                        a.getType(),
                        a.getItemName(),
                        a.getParticipantName(),
                        a.getTimestamp()
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
                ActivityList,
                session.getCreatedAt()
        );
    }

    public Session createSession(UUID tableId, String waiterId, int maxParticipants) {
        UUID tenantId = TenantContextHolder.requireTenantId();

        var table = diningTableRepository.findById(tableId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + tableId));

        var OpenSession = sessionRepository.findByTenantIdAndTableIdAndStatus(
                tenantId, tableId, SessionStatus.OPEN);

        if (!OpenSession.isEmpty()) {
            throw new IllegalStateException(
                    "Table " + table.getTableNumber() + " is already occupied");
        }

        Session session = sessionRepository.save(Session.builder()
                .tenantId(tenantId)
                .tableId(tableId)
                .waiterId(waiterId)
                .status(SessionStatus.OPEN)
                .maxParticipants(maxParticipants)
                .createdAt(LocalDateTime.now())
                .joinCode(generateJoinCode())
                .build());

        eventPublisher.publishEvent(
                new SessionOpened(tenantId, session.getId(), tableId, table.getTableNumber()));

        return session;
    }

    public Session joinSession(String qrToken, String requesterEmail, String userName) {
        var qr = qrTokenService.validateQrToken(qrToken);
        bindResolvedTenant(qr.tenantId());

        var user = userRepository.findByEmail(requesterEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + requesterEmail));

        Session session = findById(qr.sessionId());

        int maxParticipants = session.getMaxParticipants();
        if (session.getParticipants().size() >= maxParticipants) {
            throw new TooManyParticipantsException(
                    "Session " + qr.sessionId() + " is at full capacity (" + maxParticipants + ")");
        }

        boolean alreadyJoined = session.getParticipants().stream()
                .anyMatch(p -> p.getUserId().equals(user.getId()));
        if (alreadyJoined) {
            throw new IllegalStateException(
                    "User " + user.getId() + " has already joined this session");
        }

        rejectIfSeatedElsewhere(session, user.getId());

        session.getParticipants().add(Participant.builder().userId(user.getId()).name(userName).build());
        Session saved = sessionRepository.save(session);

        eventPublisher.publishEvent(
                new ParticipantJoined(saved.getTenantId(), saved.getId(), user.getId(), userName));
        return saved;
    }

    public Session joinSessionCode(String joinCode, String userId ) {

        var user = userRepository.findByEmail(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        // Untenanted on purpose: the customer types this code before any restaurant is bound to
        // their token, so the code itself is what identifies the tenant.
        List<Session> matches = sessionRepository.findByJoinCodeAndStatus(joinCode, SessionStatus.OPEN);
        if (matches.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Code not found: " + joinCode);
        }
        if (matches.size() > 1) {
            // Join codes are random, not globally unique, so two restaurants can hold the same
            // open code. Refuse rather than guess which diner's table was meant.
            throw new IllegalStateException(
                    "Join code " + joinCode + " is in use at more than one restaurant right now;"
                            + " ask staff to reopen the table for a new code");
        }
        Session session = matches.get(0);
        bindResolvedTenant(session.getTenantId());

        boolean alreadyJoin = session.getParticipants().stream().anyMatch(p -> p.getUserId().equals(user.getId()));
        if (alreadyJoin) {
            return session;
        }

        rejectIfSeatedElsewhere(session, user.getId());

        session.getParticipants().add(Participant.builder().userId(user.getId()).name(user.getName()).build());
        Session saved = sessionRepository.save(session);
        eventPublisher.publishEvent(
                new ParticipantJoined(saved.getTenantId(), saved.getId(), user.getId(), user.getName()));
        return saved;
    }

    /**
     * Binds the tenant a join credential resolved to, for the remainder of this request — the one
     * place outside the JWT filters allowed to touch {@link TenantContextHolder}, because a
     * customer's token carries no restaurant until they join a table. The id never comes from raw
     * client input: it is read off a server-signed QR token or off the stored session document,
     * exactly like PublicRestaurantController resolves a slug through its own lookup first.
     */
    private void bindResolvedTenant(UUID tenantId) {
        var restaurant = restaurantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found: " + tenantId));
        if (restaurant.getStatus() != RestaurantStatus.ACTIVE) {
            throw new AccessDeniedException("This restaurant is not accepting orders right now");
        }
        TenantContextHolder.setTenantId(tenantId);
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
        Session session = findById(sessionId);

        if (!session.getWaiterId().equals(requestingWaiter)) {
            throw new IllegalStateException(
                    "Only the assigned waiter is authorized to expand capacity");
        }

        session.setMaxParticipants(session.getMaxParticipants() + additional);
        return sessionRepository.save(session);
    }

    private static final int SESSION_TIMEOUT_HOURS = 8;

    public Session addItem(String sessionId, String participantId, Long menuItemId, List<Long> selectedOptionIds) {
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

        List<SelectedModifier> selectedModifiers = resolveSelectedModifiers(menuItem, selectedOptionIds);
        BigDecimal totalPrice = menuItem.getPrice().add(selectedModifiers.stream()
                .map(SelectedModifier::getPriceDelta)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        OrderItem newItem = OrderItem.builder()
                .id(UUID.randomUUID().toString())
                .itemId(menuItem.getId())
                .name(menuItem.getName())
                .price(totalPrice)
                .participantId(participant.getUserId())
                .participantName(participant.getName())
                .status(OrderItemStatus.DRAFT)
                .modifiers(selectedModifiers)
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

    private List<SelectedModifier> resolveSelectedModifiers(MenuItemResponse menuItem, List<Long> selectedOptionIds) {
        List<ModifierGroupResponse> assignedGroups =
                menuItem.getModifierGroups() != null ? menuItem.getModifierGroups() : List.of();

        Map<Long, ModifierOptionResponse> optionsById = assignedGroups.stream()
                .flatMap(g -> g.getOptions().stream())
                .collect(Collectors.toMap(ModifierOptionResponse::getId, o -> o));

        for (Long optionId : selectedOptionIds) {
            if (!optionsById.containsKey(optionId)) {
                throw new InvalidModifierSelectionException(
                        "Option " + optionId + " does not belong to an active modifier group of menu item "
                                + menuItem.getId());
            }
        }

        List<SelectedModifier> resolved = new ArrayList<>();
        for (ModifierGroupResponse group : assignedGroups) {
            long selectedInGroup = group.getOptions().stream()
                    .filter(o -> selectedOptionIds.contains(o.getId()))
                    .count();

            if (selectedInGroup < group.getMinSelections()
                    || (group.getMaxSelections() != null && selectedInGroup > group.getMaxSelections())) {
                throw new InvalidModifierSelectionException(
                        "Group \"" + group.getName() + "\" requires between " + group.getMinSelections()
                                + " and " + (group.getMaxSelections() == null ? "unlimited" : group.getMaxSelections())
                                + " selections, got " + selectedInGroup);
            }

            group.getOptions().stream()
                    .filter(o -> selectedOptionIds.contains(o.getId()))
                    .forEach(o -> resolved.add(SelectedModifier.builder()
                            .groupName(group.getName())
                            .optionName(o.getName())
                            .priceDelta(o.getPriceDelta())
                            .build()));
        }
        return resolved;
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
    }

    public void closeSession(String sessionId) {
        Session session = findById(sessionId);
        session.setStatus(SessionStatus.CLOSED);
        sessionRepository.save(session);

        eventPublisher.publishEvent(new SessionClosed(
                session.getTenantId(), session.getId(), session.getTableId(), session.getStatus()
        ));
    }

    public void closeEmptySession(String sessionId){
        Session session = findById(sessionId);


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

        if (item.getStatus() == OrderItemStatus.PREPARING
                || item.getStatus() == OrderItemStatus.READY
                || item.getStatus() == OrderItemStatus.DELIVERED) {
            throw new IllegalStateException(
                    "Cannot remove item '" + item.getName() + "' as it has already been sent to the kitchen");
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

        session.getActivityLog().add(SessionActivity.builder()
                .type(SessionActivity.Type.ITEM_DELETED)
                .itemName(item.getName())
                .participantName(item.getParticipantName())
                .timestamp(LocalDateTime.now())
                .build());

        eventPublisher.publishEvent(new DeleteItem(
                session.getId(), item.getId()
        ));

        return sessionRepository.save(session);


    }

    public void confirmDraftsForUser(String sessionId, String userId, String requesterEmail) {
        Session session = findById(sessionId);

        User requester = userRepository.findByEmail(requesterEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + requesterEmail));

        if (!requester.getId().equals(userId)) {
            throw new AccessDeniedException("Not authorized to confirm this user's order");
        }

        DiningTables table = diningTableRepository.findById(session.getTableId())
                .orElseThrow(() -> new ResourceNotFoundException("Table not found"));

        List<OrderItem> drafts = session.getItems().stream()
                .filter(item  -> item.getParticipantId().equals(userId))
                .filter(item -> item.getStatus() == OrderItemStatus.DRAFT)
                .toList();

        if(!drafts.isEmpty()){
            drafts.forEach(item -> {
                item.setStatus(OrderItemStatus.PENDING);
                item.setAddedAt(LocalDateTime.now());
                session.getActivityLog().add(SessionActivity.builder()
                        .type(SessionActivity.Type.ITEM_SENT)
                        .itemName(item.getName())
                        .participantName(item.getParticipantName())
                        .timestamp(item.getAddedAt())
                        .build());
            });

            Session savedSession = sessionRepository.save(session);
            eventPublisher.publishEvent(new ItemSent(savedSession));

            eventPublisher.publishEvent(new KitchenItemsConfirmed(
                    savedSession.getTenantId(),
                    savedSession.getId(),
                    table.getTableNumber(),
                    drafts
            ));
        }
    }

    public SessionStatus getSessionStatus(String id) {

        return findById(id).getStatus();

    }

    /** True when the user behind {@code userEmail} is a participant of the given session. */
    public boolean isParticipant(String sessionId, String userEmail) {
        String userId = userRepository.findByEmail(userEmail)
                .map(User::getId)
                .orElse(null);
        if (userId == null) {
            return false;
        }
        return findById(sessionId).getParticipants().stream()
                .anyMatch(p -> p.getUserId().equals(userId));
    }

    /**
     * One active table per customer: refuse a join when the user is already a participant of a
     * different OPEN session in the same restaurant. Scoped to the session's tenant — a stale
     * session at another venue is not the common footgun and there is no cross-tenant finder.
     */
    private void rejectIfSeatedElsewhere(Session target, String userId) {
        boolean seatedElsewhere = sessionRepository
                .findByTenantIdAndParticipants_UserId(target.getTenantId(), userId).stream()
                .anyMatch(s -> !s.getId().equals(target.getId()) && s.getStatus() == SessionStatus.OPEN);
        if (seatedElsewhere) {
            throw new IllegalStateException(
                    "You are already seated at another table; leave it before joining a new one");
        }
    }

    /**
     * A customer abandons an open session. Their DRAFT items are discarded (and broadcast as
     * removed); anything already sent to the kitchen stays on the table bill. When the last
     * participant leaves and nothing is billable the session is closed outright; otherwise a
     * {@link ParticipantLeft} event lets the billing side redistribute an unpaid split.
     */
    public Session leaveSession(String sessionId, String requesterEmail) {
        Session session = findById(sessionId);
        if (session.getStatus() != SessionStatus.OPEN) {
            throw new IllegalStateException("Session is not open: " + sessionId);
        }

        User user = userRepository.findByEmail(requesterEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + requesterEmail));

        Participant leaver = session.getParticipants().stream()
                .filter(p -> p.getUserId().equals(user.getId()))
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException("Not a participant of this session"));

        List<OrderItem> discardedDrafts = session.getItems().stream()
                .filter(i -> user.getId().equals(i.getParticipantId())
                        && i.getStatus() == OrderItemStatus.DRAFT)
                .toList();
        session.getItems().removeAll(discardedDrafts);
        session.getParticipants().removeIf(p -> p.getUserId().equals(user.getId()));

        boolean hasBillableItems = session.getItems().stream()
                .anyMatch(i -> i.getStatus() != OrderItemStatus.DRAFT);

        if (session.getParticipants().isEmpty() && !hasBillableItems) {
            session.setStatus(SessionStatus.CLOSED);
            Session closed = sessionRepository.save(session);
            eventPublisher.publishEvent(new SessionClosed(
                    closed.getTenantId(), closed.getId(), closed.getTableId(), closed.getStatus()));
            return closed;
        }

        Session saved = sessionRepository.save(session);
        discardedDrafts.forEach(d -> eventPublisher.publishEvent(new DeleteItem(saved.getId(), d.getId())));
        eventPublisher.publishEvent(new ParticipantLeft(
                saved.getTenantId(), saved.getId(), user.getId(), leaver.getName()));
        return saved;
    }

    /**
     * Re-attach a customer to their still-open session after a re-login, when their JWT has lost
     * the tenant scope. Looked up untenanted (like {@link #joinSessionCode}) then verified: the
     * session must still be OPEN and the caller must already be one of its participants. Binds the
     * session's restaurant for the response so the controller can hand back a re-scoped token.
     */
    public Session resumeSession(String sessionId, String requesterEmail) {
        User user = userRepository.findByEmail(requesterEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + requesterEmail));

        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));

        if (session.getStatus() != SessionStatus.OPEN) {
            throw new IllegalStateException("Session is not open: " + sessionId);
        }

        boolean isParticipant = session.getParticipants().stream()
                .anyMatch(p -> p.getUserId().equals(user.getId()));
        if (!isParticipant) {
            throw new AccessDeniedException("Not a participant of this session");
        }

        bindResolvedTenant(session.getTenantId());
        return session;
    }
}
