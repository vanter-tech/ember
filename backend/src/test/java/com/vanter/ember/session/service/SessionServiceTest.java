package com.vanter.ember.session.service;

import com.vanter.ember.catalog.model.dto.MenuItemResponse;
import com.vanter.ember.catalog.service.MenuItemService;
import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.kitchen.event.KitchenItemUpdated;
import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.model.RestaurantStatus;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import com.vanter.ember.session.event.ItemAdded;
import com.vanter.ember.session.event.KitchenItemsConfirmed;
import com.vanter.ember.session.event.ParticipantJoined;
import com.vanter.ember.session.event.SessionOpened;
import com.vanter.ember.session.exception.TooManyParticipantsException;
import com.vanter.ember.session.model.OrderItem;
import com.vanter.ember.session.model.OrderItemStatus;
import com.vanter.ember.session.model.Participant;
import com.vanter.ember.session.model.Session;
import com.vanter.ember.session.model.SessionActivity;
import com.vanter.ember.session.model.SessionStatus;
import com.vanter.ember.session.repository.SessionRepository;
import com.vanter.ember.settings.model.DiningTables;
import com.vanter.ember.settings.repository.DiningTableRepository;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    private static final UUID TABLE_ID = UUID.randomUUID();
    private static final UUID RESTAURANT_ID = UUID.randomUUID();

    @Mock SessionRepository sessionRepository;
    @Mock DiningTableRepository diningTableRepository;
    @Mock MenuItemService menuItemService;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock QrTokenService qrTokenService;
    @Mock UserRepository userRepository;
    @Mock RestaurantRepository restaurantRepository;
    @InjectMocks SessionService sessionService;

    private DiningTables diningTable() {
        return DiningTables.builder().id(TABLE_ID).tableNumber(5).isActive(true).build();
    }

    private User user(String id) {
        return User.builder().id(id).email(id).name("Alice").build();
    }

    @Test
    void createSession_savesSessionWithOpenStatus() {
        when(diningTableRepository.findById(TABLE_ID)).thenReturn(Optional.of(diningTable()));
        when(sessionRepository.findByTenantIdAndTableIdAndStatus(RESTAURANT_ID, TABLE_ID, SessionStatus.OPEN)).thenReturn(List.of());
        when(sessionRepository.save(any())).thenAnswer(inv -> {
            Session s = inv.getArgument(0);
            s.setId("sess-1");
            return s;
        });

        Session result = sessionService.createSession(TABLE_ID, "waiter@test.com", 4);

        assertThat(result.getId()).isEqualTo("sess-1");
        assertThat(result.getStatus()).isEqualTo(SessionStatus.OPEN);
        assertThat(result.getTableId()).isEqualTo(TABLE_ID);
        assertThat(result.getWaiterId()).isEqualTo("waiter@test.com");
        assertThat(result.getMaxParticipants()).isEqualTo(4);
    }

    @Test
    void createSession_stampsTenantFromContext() {
        when(diningTableRepository.findById(TABLE_ID)).thenReturn(Optional.of(diningTable()));
        when(sessionRepository.findByTenantIdAndTableIdAndStatus(RESTAURANT_ID, TABLE_ID, SessionStatus.OPEN))
                .thenReturn(List.of());
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Session result = sessionService.createSession(TABLE_ID, "waiter@test.com", 4);

        assertThat(result.getTenantId()).isEqualTo(RESTAURANT_ID);
    }

    @Test
    void findById_throwsWhenSessionBelongsToAnotherTenant() {
        when(sessionRepository.findByIdAndTenantId("sess-1", RESTAURANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.findById("sess-1"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createSession_setsCreatedAt() {
        when(diningTableRepository.findById(TABLE_ID)).thenReturn(Optional.of(diningTable()));
        when(sessionRepository.findByTenantIdAndTableIdAndStatus(RESTAURANT_ID, TABLE_ID, SessionStatus.OPEN)).thenReturn(List.of());
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Session result = sessionService.createSession(TABLE_ID, "waiter@test.com", 4);

        assertThat(result.getCreatedAt()).isNotNull();
    }

    @Test
    void createSession_publishesSessionOpenedEvent() {
        when(diningTableRepository.findById(TABLE_ID)).thenReturn(Optional.of(diningTable()));
        when(sessionRepository.findByTenantIdAndTableIdAndStatus(RESTAURANT_ID, TABLE_ID, SessionStatus.OPEN)).thenReturn(List.of());
        when(sessionRepository.save(any())).thenAnswer(inv -> {
            Session s = inv.getArgument(0);
            s.setId("sess-1");
            return s;
        });

        sessionService.createSession(TABLE_ID, "waiter@test.com", 4);

        ArgumentCaptor<SessionOpened> captor = ArgumentCaptor.forClass(SessionOpened.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().sessionId()).isEqualTo("sess-1");
        assertThat(captor.getValue().tableId()).isEqualTo(TABLE_ID);
        assertThat(captor.getValue().tableNumber()).isEqualTo(5);
    }

    @Test
    void createSession_throwsWhenTableOccupied() {
        when(diningTableRepository.findById(TABLE_ID)).thenReturn(Optional.of(diningTable()));
        Session existingOpenSession = Session.builder()
                .id("sess-0").tableId(TABLE_ID).status(SessionStatus.OPEN).build();
        when(sessionRepository.findByTenantIdAndTableIdAndStatus(RESTAURANT_ID, TABLE_ID, SessionStatus.OPEN))
                .thenReturn(List.of(existingOpenSession));

        assertThatThrownBy(() -> sessionService.createSession(TABLE_ID, "waiter@test.com", 4))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("occupied");
    }

    // --- join tests ---

    private Session openSessionWithCapacity(int maxParticipants, List<Participant> participants) {
        return Session.builder()
                .id("sess-1").tenantId(RESTAURANT_ID).tableId(TABLE_ID).waiterId("waiter@test.com")
                .status(SessionStatus.OPEN)
                .maxParticipants(maxParticipants)
                .participants(new ArrayList<>(participants))
                .createdAt(LocalDateTime.now())
                .build();
    }

    private void stubQrTokenFor(String token) {
        when(qrTokenService.validateQrToken(token))
                .thenReturn(new QrTokenService.QrTokenData("sess-1", RESTAURANT_ID));
    }

    private void stubActiveRestaurant() {
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(
                Restaurant.builder().id(RESTAURANT_ID).status(RestaurantStatus.ACTIVE).build()));
    }

    @BeforeEach
    void bindTenant() {
        TenantContextHolder.setTenantId(RESTAURANT_ID);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void join_addsParticipantWhenTokenValidAndCapacityAvailable() {
        stubQrTokenFor("qr-token");
        stubActiveRestaurant();
        when(userRepository.findByEmail("user-1")).thenReturn(Optional.of(user("user-1")));
        when(sessionRepository.findByIdAndTenantId("sess-1", RESTAURANT_ID))
                .thenReturn(Optional.of(openSessionWithCapacity(4, List.of())));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Session result = sessionService.joinSession("qr-token", "user-1", "Alice");

        assertThat(result.getParticipants()).hasSize(1);
        assertThat(result.getParticipants().get(0).getUserId()).isEqualTo("user-1");
        assertThat(result.getParticipants().get(0).getName()).isEqualTo("Alice");
    }

    @Test
    void join_throwsTooManyParticipantsWhenFull() {
        List<Participant> full = List.of(
                Participant.builder().userId("u1").name("A").build(),
                Participant.builder().userId("u2").name("B").build());
        stubQrTokenFor("qr-token");
        stubActiveRestaurant();
        when(userRepository.findByEmail("u3")).thenReturn(Optional.of(user("u3")));
        when(sessionRepository.findByIdAndTenantId("sess-1", RESTAURANT_ID))
                .thenReturn(Optional.of(openSessionWithCapacity(2, full)));

        assertThatThrownBy(() -> sessionService.joinSession("qr-token", "u3", "C"))
                .isInstanceOf(TooManyParticipantsException.class);
    }

    @Test
    void join_readsCapacityFromLiveSessionNotFromToken() {
        List<Participant> twoJoined = List.of(
                Participant.builder().userId("u1").name("A").build(),
                Participant.builder().userId("u2").name("B").build());
        stubQrTokenFor("qr-token");
        stubActiveRestaurant();
        when(userRepository.findByEmail("u3")).thenReturn(Optional.of(user("u3")));
        // capacity was expanded to 4 after the QR token was minted for a 2-seat session
        when(sessionRepository.findByIdAndTenantId("sess-1", RESTAURANT_ID))
                .thenReturn(Optional.of(openSessionWithCapacity(4, twoJoined)));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Session result = sessionService.joinSession("qr-token", "u3", "C");

        assertThat(result.getParticipants()).hasSize(3);
    }

    @Test
    void join_throwsWhenSessionBelongsToAnotherTenant() {
        stubQrTokenFor("qr-token");
        stubActiveRestaurant();
        when(userRepository.findByEmail("user-1")).thenReturn(Optional.of(user("user-1")));
        // the session exists, but under another tenant, so the scoped lookup returns nothing
        when(sessionRepository.findByIdAndTenantId("sess-1", RESTAURANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.joinSession("qr-token", "user-1", "Alice"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void join_throwsWhenTokenExpired() {
        when(qrTokenService.validateQrToken("expired-token"))
                .thenThrow(ExpiredJwtException.class);

        assertThatThrownBy(() -> sessionService.joinSession("expired-token", "u1", "Alice"))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void join_throwsOnDuplicateUserId() {
        List<Participant> existing = List.of(
                Participant.builder().userId("user-1").name("Alice").build());
        stubQrTokenFor("qr-token");
        stubActiveRestaurant();
        when(userRepository.findByEmail("user-1")).thenReturn(Optional.of(user("user-1")));
        when(sessionRepository.findByIdAndTenantId("sess-1", RESTAURANT_ID))
                .thenReturn(Optional.of(openSessionWithCapacity(4, existing)));

        assertThatThrownBy(() -> sessionService.joinSession("qr-token", "user-1", "Alice"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already");
    }

    @Test
    void join_publishesParticipantJoinedEvent() {
        stubQrTokenFor("qr-token");
        stubActiveRestaurant();
        when(userRepository.findByEmail("user-1")).thenReturn(Optional.of(user("user-1")));
        when(sessionRepository.findByIdAndTenantId("sess-1", RESTAURANT_ID))
                .thenReturn(Optional.of(openSessionWithCapacity(4, List.of())));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        sessionService.joinSession("qr-token", "user-1", "Alice");

        ArgumentCaptor<ParticipantJoined> captor = ArgumentCaptor.forClass(ParticipantJoined.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().sessionId()).isEqualTo("sess-1");
        assertThat(captor.getValue().userId()).isEqualTo("user-1");
        assertThat(captor.getValue().userName()).isEqualTo("Alice");
    }

    // --- joinSessionCode tests ---

    @Test
    void joinCode_findsSessionAcrossTenantsAndBindsItsRestaurant() {
        when(userRepository.findByEmail("user-1")).thenReturn(Optional.of(user("user-1")));
        when(sessionRepository.findByJoinCodeAndStatus("AB3CD", SessionStatus.OPEN))
                .thenReturn(List.of(openSessionWithCapacity(4, List.of())));
        stubActiveRestaurant();
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Session result = sessionService.joinSessionCode("AB3CD", "user-1");

        assertThat(result.getParticipants()).hasSize(1);
        assertThat(result.getParticipants().get(0).getUserId()).isEqualTo("user-1");
        assertThat(TenantContextHolder.requireTenantId()).isEqualTo(RESTAURANT_ID);
    }

    @Test
    void joinCode_throwsNotFoundWhenNoOpenSessionCarriesTheCode() {
        when(userRepository.findByEmail("user-1")).thenReturn(Optional.of(user("user-1")));
        when(sessionRepository.findByJoinCodeAndStatus("AB3CD", SessionStatus.OPEN))
                .thenReturn(List.of());

        assertThatThrownBy(() -> sessionService.joinSessionCode("AB3CD", "user-1"))
                .isInstanceOf(ResponseStatusException.class);
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void joinCode_refusesWhenTheSameCodeIsOpenAtTwoRestaurants() {
        Session other = Session.builder()
                .id("sess-2").tenantId(UUID.randomUUID()).tableId(UUID.randomUUID())
                .waiterId("waiter@other.com").status(SessionStatus.OPEN).maxParticipants(4)
                .participants(new ArrayList<>()).createdAt(LocalDateTime.now()).build();
        when(userRepository.findByEmail("user-1")).thenReturn(Optional.of(user("user-1")));
        when(sessionRepository.findByJoinCodeAndStatus("AB3CD", SessionStatus.OPEN))
                .thenReturn(List.of(openSessionWithCapacity(4, List.of()), other));

        assertThatThrownBy(() -> sessionService.joinSessionCode("AB3CD", "user-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("more than one restaurant");
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void joinCode_refusesWhenTheResolvedRestaurantIsSuspended() {
        when(userRepository.findByEmail("user-1")).thenReturn(Optional.of(user("user-1")));
        when(sessionRepository.findByJoinCodeAndStatus("AB3CD", SessionStatus.OPEN))
                .thenReturn(List.of(openSessionWithCapacity(4, List.of())));
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(
                Restaurant.builder().id(RESTAURANT_ID).status(RestaurantStatus.SUSPENDED).build()));

        assertThatThrownBy(() -> sessionService.joinSessionCode("AB3CD", "user-1"))
                .isInstanceOf(AccessDeniedException.class);
        verify(sessionRepository, never()).save(any());
    }

    // --- expandCapacity tests ---

    @Test
    void expandCapacity_increasesByGivenAmount() {
        Session session = openSessionWithCapacity(4, List.of());
        when(sessionRepository.findByIdAndTenantId("sess-1", RESTAURANT_ID)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Session result = sessionService.expandCapacity("sess-1", "waiter@test.com", 2);

        assertThat(result.getMaxParticipants()).isEqualTo(6);
    }

    @Test
    void expandCapacity_throwsWhenNotAssignedWaiter() {
        Session session = openSessionWithCapacity(4, List.of());
        when(sessionRepository.findByIdAndTenantId("sess-1", RESTAURANT_ID)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.expandCapacity("sess-1", "other@test.com", 2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("authorized");
    }

    // --- addItem tests ---

    private MenuItemResponse availableMenuItem() {
        return MenuItemResponse.builder()
                .id(10L).name("Tacos").price(new java.math.BigDecimal("12.50"))
                .available(true).build();
    }

    private Session openSessionWithParticipant(String participantId) {
        return Session.builder()
                .id("sess-1").tenantId(RESTAURANT_ID).tableId(TABLE_ID).waiterId("waiter@test.com")
                .status(SessionStatus.OPEN).maxParticipants(4)
                .participants(new ArrayList<>(List.of(
                        Participant.builder().userId(participantId).name("Alice").build())))
                .items(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void addItem_addsOrderItemWithDraftStatus() {
        Session session = openSessionWithParticipant("user-1");
        when(sessionRepository.findByIdAndTenantId("sess-1", RESTAURANT_ID)).thenReturn(Optional.of(session));
        when(userRepository.findByEmail("user-1")).thenReturn(Optional.of(user("user-1")));
        when(menuItemService.findById(10L)).thenReturn(availableMenuItem());
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Session result = sessionService.addItem("sess-1", "user-1", 10L, List.of());

        assertThat(result.getItems()).hasSize(1);
        OrderItem item = result.getItems().get(0);
        assertThat(item.getItemId()).isEqualTo(10L);
        assertThat(item.getName()).isEqualTo("Tacos");
        assertThat(item.getPrice()).isEqualByComparingTo("12.50");
        assertThat(item.getParticipantId()).isEqualTo("user-1");
        assertThat(item.getParticipantName()).isEqualTo("Alice");
        assertThat(item.getStatus()).isEqualTo(OrderItemStatus.DRAFT);
        assertThat(item.getAddedAt()).isNotNull();
    }

    @Test
    void addItem_throwsWhenParticipantNotInSession() {
        Session session = openSessionWithParticipant("user-1");
        when(sessionRepository.findByIdAndTenantId("sess-1", RESTAURANT_ID)).thenReturn(Optional.of(session));
        when(userRepository.findByEmail("user-99")).thenReturn(Optional.of(user("user-99")));

        assertThatThrownBy(() -> sessionService.addItem("sess-1", "user-99", 10L, List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not a participant");
    }

    @Test
    void addItem_throwsWhenSessionClosed() {
        Session session = openSessionWithParticipant("user-1");
        session.setStatus(SessionStatus.CLOSED);
        when(sessionRepository.findByIdAndTenantId("sess-1", RESTAURANT_ID)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.addItem("sess-1", "user-1", 10L, List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed");
    }

    @Test
    void addItem_throwsWhenMenuItemNotAvailable() {
        Session session = openSessionWithParticipant("user-1");
        MenuItemResponse unavailable = availableMenuItem();
        unavailable.setAvailable(false);
        when(sessionRepository.findByIdAndTenantId("sess-1", RESTAURANT_ID)).thenReturn(Optional.of(session));
        when(userRepository.findByEmail("user-1")).thenReturn(Optional.of(user("user-1")));
        when(menuItemService.findById(10L)).thenReturn(unavailable);

        assertThatThrownBy(() -> sessionService.addItem("sess-1", "user-1", 10L, List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not available");
    }

    @Test
    void addItem_publishesItemAddedEvent() {
        Session session = openSessionWithParticipant("user-1");
        when(sessionRepository.findByIdAndTenantId("sess-1", RESTAURANT_ID)).thenReturn(Optional.of(session));
        when(userRepository.findByEmail("user-1")).thenReturn(Optional.of(user("user-1")));
        when(menuItemService.findById(10L)).thenReturn(availableMenuItem());
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        sessionService.addItem("sess-1", "user-1", 10L, List.of());

        ArgumentCaptor<ItemAdded> captor = ArgumentCaptor.forClass(ItemAdded.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().sessionId()).isEqualTo("sess-1");
        assertThat(captor.getValue().itemName()).isEqualTo("Tacos");
        assertThat(captor.getValue().participantName()).isEqualTo("Alice");
        assertThat(captor.getValue().status()).isEqualTo(OrderItemStatus.DRAFT);
        assertThat(captor.getValue().sessionItems()).hasSize(1);
    }

    @Test
    void addItem_throwsWhenMenuItemNotFound() {
        Session session = openSessionWithParticipant("user-1");
        when(sessionRepository.findByIdAndTenantId("sess-1", RESTAURANT_ID)).thenReturn(Optional.of(session));
        when(userRepository.findByEmail("user-1")).thenReturn(Optional.of(user("user-1")));
        when(menuItemService.findById(99L))
                .thenThrow(new ResourceNotFoundException("Menu item not found: 99"));

        assertThatThrownBy(() -> sessionService.addItem("sess-1", "user-1", 99L, List.of()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private MenuItemResponse menuItemWithModifierGroup() {
        var option = com.vanter.ember.catalog.model.dto.ModifierOptionResponse.builder()
                .id(100L).name("Término medio").priceDelta(java.math.BigDecimal.ZERO).active(true).build();
        var group = com.vanter.ember.catalog.model.dto.ModifierGroupResponse.builder()
                .id(1L).name("Término de cocción")
                .selectionType(com.vanter.ember.catalog.model.SelectionType.SINGLE_REQUIRED)
                .minSelections(1).maxSelections(1).active(true)
                .options(List.of(option))
                .build();
        return MenuItemResponse.builder()
                .id(10L).name("Tacos").price(new java.math.BigDecimal("12.50"))
                .available(true).modifierGroups(List.of(group)).build();
    }

    @Test
    void addItem_throwsWhenRequiredModifierGroupNotSelected() {
        Session session = openSessionWithParticipant("user-1");
        when(sessionRepository.findByIdAndTenantId("sess-1", RESTAURANT_ID)).thenReturn(Optional.of(session));
        when(userRepository.findByEmail("user-1")).thenReturn(Optional.of(user("user-1")));
        when(menuItemService.findById(10L)).thenReturn(menuItemWithModifierGroup());

        assertThatThrownBy(() -> sessionService.addItem("sess-1", "user-1", 10L, List.of()))
                .isInstanceOf(com.vanter.ember.session.exception.InvalidModifierSelectionException.class)
                .hasMessageContaining("Término de cocción");
    }

    @Test
    void addItem_throwsWhenOptionNotAssignedToMenuItem() {
        Session session = openSessionWithParticipant("user-1");
        when(sessionRepository.findByIdAndTenantId("sess-1", RESTAURANT_ID)).thenReturn(Optional.of(session));
        when(userRepository.findByEmail("user-1")).thenReturn(Optional.of(user("user-1")));
        when(menuItemService.findById(10L)).thenReturn(menuItemWithModifierGroup());

        assertThatThrownBy(() -> sessionService.addItem("sess-1", "user-1", 10L, List.of(999L)))
                .isInstanceOf(com.vanter.ember.session.exception.InvalidModifierSelectionException.class);
    }

    @Test
    void addItem_computesPriceWithModifierDelta() {
        Session session = openSessionWithParticipant("user-1");
        var option = com.vanter.ember.catalog.model.dto.ModifierOptionResponse.builder()
                .id(100L).name("Extra queso").priceDelta(new java.math.BigDecimal("1.50")).active(true).build();
        var group = com.vanter.ember.catalog.model.dto.ModifierGroupResponse.builder()
                .id(2L).name("Extras")
                .selectionType(com.vanter.ember.catalog.model.SelectionType.MULTI_OPTIONAL)
                .minSelections(0).maxSelections(null).active(true)
                .options(List.of(option))
                .build();
        MenuItemResponse menuItem = MenuItemResponse.builder()
                .id(10L).name("Tacos").price(new java.math.BigDecimal("12.50"))
                .available(true).modifierGroups(List.of(group)).build();

        when(sessionRepository.findByIdAndTenantId("sess-1", RESTAURANT_ID)).thenReturn(Optional.of(session));
        when(userRepository.findByEmail("user-1")).thenReturn(Optional.of(user("user-1")));
        when(menuItemService.findById(10L)).thenReturn(menuItem);
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Session result = sessionService.addItem("sess-1", "user-1", 10L, List.of(100L));

        assertThat(result.getItems().get(0).getPrice()).isEqualByComparingTo("14.00");
        assertThat(result.getItems().get(0).getModifiers()).hasSize(1);
        assertThat(result.getItems().get(0).getModifiers().get(0).getOptionName()).isEqualTo("Extra queso");
    }

    // --- removeItem tests ---

    private Session openSessionWithItem(OrderItemStatus status, String participantId) {
        OrderItem item = OrderItem.builder()
                .id("order-item-1")
                .itemId(10L).name("Tacos").price(new java.math.BigDecimal("12.50"))
                .participantId(participantId).participantName("Alice")
                .status(status).addedAt(LocalDateTime.now())
                .build();
        return Session.builder()
                .id("sess-1").tenantId(RESTAURANT_ID).tableId(TABLE_ID).waiterId("waiter@test.com")
                .status(SessionStatus.OPEN).maxParticipants(4)
                .participants(new ArrayList<>(List.of(
                        Participant.builder().userId(participantId).name("Alice").build())))
                .items(new ArrayList<>(List.of(item)))
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void removeItem_removesItemWhenParticipantOwnsPendingItem() {
        Session session = openSessionWithItem(OrderItemStatus.PENDING, "user-1");
        when(sessionRepository.findByIdAndTenantId("sess-1", RESTAURANT_ID)).thenReturn(Optional.of(session));
        when(userRepository.findByEmail("user-1")).thenReturn(Optional.of(user("user-1")));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Session result = sessionService.removeItem("sess-1", "order-item-1", "user-1");

        assertThat(result.getItems()).isEmpty();
    }

    @Test
    void removeItem_removesItemWhenWaiterRequests() {
        Session session = openSessionWithItem(OrderItemStatus.PENDING, "user-1");
        when(sessionRepository.findByIdAndTenantId("sess-1", RESTAURANT_ID)).thenReturn(Optional.of(session));
        when(userRepository.findByEmail("waiter@test.com")).thenReturn(Optional.of(user("waiter@test.com")));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Session result = sessionService.removeItem("sess-1", "order-item-1", "waiter@test.com");

        assertThat(result.getItems()).isEmpty();
    }

    @Test
    void removeItem_throwsWhenItemIsPreparing() {
        Session session = openSessionWithItem(OrderItemStatus.PREPARING, "user-1");
        when(sessionRepository.findByIdAndTenantId("sess-1", RESTAURANT_ID)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.removeItem("sess-1", "order-item-1", "user-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("being prepared");
    }

    @Test
    void removeItem_throwsWhenParticipantRemovesOthersItem() {
        Session session = openSessionWithItem(OrderItemStatus.PENDING, "user-1");
        session.getParticipants().add(Participant.builder().userId("user-2").name("Bob").build());
        when(sessionRepository.findByIdAndTenantId("sess-1", RESTAURANT_ID)).thenReturn(Optional.of(session));
        when(userRepository.findByEmail("user-2")).thenReturn(Optional.of(user("user-2")));

        assertThatThrownBy(() -> sessionService.removeItem("sess-1", "order-item-1", "user-2"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("owner");
    }

    @Test
    void removeItem_throwsWhenItemNotFound() {
        Session session = openSessionWithItem(OrderItemStatus.PENDING, "user-1");
        when(sessionRepository.findByIdAndTenantId("sess-1", RESTAURANT_ID)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.removeItem("sess-1", "nonexistent-id", "user-1"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void removeItem_appendsDeletedActivityWithoutErasingPriorEntries() {
        Session session = openSessionWithItem(OrderItemStatus.PENDING, "user-1");
        session.getActivityLog().add(SessionActivity.builder()
                .type(SessionActivity.Type.ITEM_SENT)
                .itemName("Tacos").participantName("Alice").timestamp(LocalDateTime.now())
                .build());
        when(sessionRepository.findByIdAndTenantId("sess-1", RESTAURANT_ID)).thenReturn(Optional.of(session));
        when(userRepository.findByEmail("user-1")).thenReturn(Optional.of(user("user-1")));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Session result = sessionService.removeItem("sess-1", "order-item-1", "user-1");

        assertThat(result.getActivityLog()).hasSize(2);
        assertThat(result.getActivityLog().get(0).getType()).isEqualTo(SessionActivity.Type.ITEM_SENT);
        SessionActivity deleted = result.getActivityLog().get(1);
        assertThat(deleted.getType()).isEqualTo(SessionActivity.Type.ITEM_DELETED);
        assertThat(deleted.getItemName()).isEqualTo("Tacos");
        assertThat(deleted.getParticipantName()).isEqualTo("Alice");
    }

    // --- handleKitchenItemUpdated tests ---

    @Test
    void handleKitchenItemUpdated_updatesOrderItemStatus() {
        Session session = openSessionWithItem(OrderItemStatus.PENDING, "user-1");
        when(sessionRepository.findByIdAndTenantId("sess-1", RESTAURANT_ID)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        sessionService.handleKitchenItemUpdated(
                new KitchenItemUpdated(RESTAURANT_ID, "sess-1", "order-item-1", OrderItemStatus.PREPARING));

        ArgumentCaptor<Session> captor = ArgumentCaptor.forClass(Session.class);
        verify(sessionRepository).save(captor.capture());
        assertThat(captor.getValue().getItems().get(0).getStatus()).isEqualTo(OrderItemStatus.PREPARING);
    }

    @Test
    void handleKitchenItemUpdated_throwsWhenSessionNotFound() {
        when(sessionRepository.findByIdAndTenantId("sess-999", RESTAURANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.handleKitchenItemUpdated(
                new KitchenItemUpdated(RESTAURANT_ID, "sess-999", "order-item-1", OrderItemStatus.PREPARING)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void handleKitchenItemUpdated_throwsWhenItemNotFound() {
        Session session = openSessionWithItem(OrderItemStatus.PENDING, "user-1");
        when(sessionRepository.findByIdAndTenantId("sess-1", RESTAURANT_ID)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.handleKitchenItemUpdated(
                new KitchenItemUpdated(RESTAURANT_ID, "sess-1", "nonexistent-id", OrderItemStatus.PREPARING)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void handleKitchenItemUpdated_doesNotExposeStatusToTheCustomer() {
        Session session = openSessionWithItem(OrderItemStatus.PENDING, "user-1");
        when(sessionRepository.findByIdAndTenantId("sess-1", RESTAURANT_ID)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        sessionService.handleKitchenItemUpdated(
                new KitchenItemUpdated(RESTAURANT_ID, "sess-1", "order-item-1", OrderItemStatus.PREPARING));

        verifyNoInteractions(eventPublisher);
    }

    // --- closeSession tests ---

    @Test
    void closeSession_setsStatusToClosed() {
        Session session = Session.builder()
                .id("sess-1").tenantId(RESTAURANT_ID).tableId(TABLE_ID).waiterId("waiter@test.com")
                .status(SessionStatus.OPEN).maxParticipants(4)
                .createdAt(LocalDateTime.now()).build();
        when(sessionRepository.findByIdAndTenantId("sess-1", RESTAURANT_ID)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        sessionService.closeSession("sess-1");

        ArgumentCaptor<Session> captor = ArgumentCaptor.forClass(Session.class);
        verify(sessionRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(SessionStatus.CLOSED);
    }

    @Test
    void closeSession_throwsWhenSessionNotFound() {
        when(sessionRepository.findByIdAndTenantId("sess-999", RESTAURANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.closeSession("sess-999"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- confirmDraftsForUser tests ---

    private User userWithRestaurant(String id, UUID restaurantId) {
        Restaurant restaurant = restaurantId == null ? null : Restaurant.builder().id(restaurantId).build();
        return User.builder().id(id).email(id + "@test.com").name("Alice")
                .restaurantId(restaurant).build();
    }

    private DiningTables diningTableForRestaurant(UUID restaurantId) {
        return DiningTables.builder().id(TABLE_ID).tableNumber(5).isActive(true)
                .restaurantId(restaurantId).build();
    }

    @Test
    void confirmDraftsForUser_throwsWhenPathUserIdDoesNotMatchRequester() {
        Session session = openSessionWithParticipant("user-1");
        when(sessionRepository.findByIdAndTenantId("sess-1", RESTAURANT_ID)).thenReturn(Optional.of(session));
        when(userRepository.findByEmail("user-2@test.com"))
                .thenReturn(Optional.of(userWithRestaurant("user-2", RESTAURANT_ID)));

        assertThatThrownBy(() -> sessionService.confirmDraftsForUser("sess-1", "user-1", "user-2@test.com"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void confirmDraftsForUser_confirmsDraftsWhenUserIdAndTenantMatch() {
        Session session = openSessionWithParticipant("user-1");
        session.getItems().add(OrderItem.builder()
                .id("item-1").itemId(10L).name("Tacos").price(new java.math.BigDecimal("12.50"))
                .participantId("user-1").participantName("Alice")
                .status(OrderItemStatus.DRAFT).addedAt(LocalDateTime.now())
                .build());
        when(sessionRepository.findByIdAndTenantId("sess-1", RESTAURANT_ID)).thenReturn(Optional.of(session));
        when(userRepository.findByEmail("user-1@test.com"))
                .thenReturn(Optional.of(userWithRestaurant("user-1", RESTAURANT_ID)));
        when(diningTableRepository.findById(TABLE_ID))
                .thenReturn(Optional.of(diningTableForRestaurant(RESTAURANT_ID)));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        sessionService.confirmDraftsForUser("sess-1", "user-1", "user-1@test.com");

        assertThat(session.getItems().get(0).getStatus()).isEqualTo(OrderItemStatus.PENDING);
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, org.mockito.Mockito.times(2)).publishEvent(captor.capture());
        assertThat(captor.getAllValues())
                .filteredOn(KitchenItemsConfirmed.class::isInstance)
                .extracting(e -> ((KitchenItemsConfirmed) e).tableNumber())
                .containsExactly(5);
        assertThat(captor.getAllValues())
                .filteredOn(KitchenItemsConfirmed.class::isInstance)
                .extracting(e -> ((KitchenItemsConfirmed) e).tenantId())
                .containsExactly(RESTAURANT_ID);
    }

    @Test
    void confirmDraftsForUser_appendsSentActivityForEachConfirmedItem() {
        Session session = openSessionWithParticipant("user-1");
        session.getItems().add(OrderItem.builder()
                .id("item-1").itemId(10L).name("Tacos").price(new java.math.BigDecimal("12.50"))
                .participantId("user-1").participantName("Alice")
                .status(OrderItemStatus.DRAFT).addedAt(LocalDateTime.now())
                .build());
        when(sessionRepository.findByIdAndTenantId("sess-1", RESTAURANT_ID)).thenReturn(Optional.of(session));
        when(userRepository.findByEmail("user-1@test.com"))
                .thenReturn(Optional.of(userWithRestaurant("user-1", RESTAURANT_ID)));
        when(diningTableRepository.findById(TABLE_ID))
                .thenReturn(Optional.of(diningTableForRestaurant(RESTAURANT_ID)));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        sessionService.confirmDraftsForUser("sess-1", "user-1", "user-1@test.com");

        assertThat(session.getActivityLog()).hasSize(1);
        SessionActivity activity = session.getActivityLog().get(0);
        assertThat(activity.getType()).isEqualTo(SessionActivity.Type.ITEM_SENT);
        assertThat(activity.getItemName()).isEqualTo("Tacos");
        assertThat(activity.getParticipantName()).isEqualTo("Alice");
    }
}
