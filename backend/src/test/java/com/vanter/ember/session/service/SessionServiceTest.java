package com.vanter.ember.session.service;

import com.vanter.ember.catalog.model.MenuItem;
import com.vanter.ember.catalog.model.RestaurantTable;
import com.vanter.ember.catalog.model.TableStatus;
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
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock SessionRepository sessionRepository;
    @Mock RestaurantTableService tableService;
    @Mock MenuItemService menuItemService;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock QrTokenService qrTokenService;
    @InjectMocks SessionService sessionService;

    private RestaurantTable availableTable() {
        return RestaurantTable.builder()
                .id(1L).number(5).capacity(4).status(TableStatus.AVAILABLE).build();
    }

    @Test
    void createSession_savesSessionWithOpenStatus() {
        when(tableService.findById(1L)).thenReturn(availableTable());
        when(sessionRepository.save(any())).thenAnswer(inv -> {
            Session s = inv.getArgument(0);
            s.setId("sess-1");
            return s;
        });

        Session result = sessionService.createSession(1L, "waiter@test.com", 4);

        assertThat(result.getId()).isEqualTo("sess-1");
        assertThat(result.getStatus()).isEqualTo(SessionStatus.OPEN);
        assertThat(result.getTableId()).isEqualTo(1L);
        assertThat(result.getWaiterId()).isEqualTo("waiter@test.com");
        assertThat(result.getMaxParticipants()).isEqualTo(4);
    }

    @Test
    void createSession_setsCreatedAt() {
        when(tableService.findById(1L)).thenReturn(availableTable());
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Session result = sessionService.createSession(1L, "waiter@test.com", 4);

        assertThat(result.getCreatedAt()).isNotNull();
    }

    @Test
    void createSession_publishesSessionOpenedEvent() {
        when(tableService.findById(1L)).thenReturn(availableTable());
        when(sessionRepository.save(any())).thenAnswer(inv -> {
            Session s = inv.getArgument(0);
            s.setId("sess-1");
            return s;
        });

        sessionService.createSession(1L, "waiter@test.com", 4);

        ArgumentCaptor<SessionOpened> captor = ArgumentCaptor.forClass(SessionOpened.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().sessionId()).isEqualTo("sess-1");
        assertThat(captor.getValue().tableId()).isEqualTo(1L);
        assertThat(captor.getValue().tableNumber()).isEqualTo(5);
    }

    @Test
    void createSession_throwsWhenTableOccupied() {
        RestaurantTable occupied = RestaurantTable.builder()
                .id(1L).number(5).capacity(4).status(TableStatus.OCCUPIED).build();
        when(tableService.findById(1L)).thenReturn(occupied);

        assertThatThrownBy(() -> sessionService.createSession(1L, "waiter@test.com", 4))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("occupied");
    }

    // --- join tests ---

    private Session openSessionWithCapacity(int maxParticipants, List<Participant> participants) {
        return Session.builder()
                .id("sess-1").tableId(1L).waiterId("waiter@test.com")
                .status(SessionStatus.OPEN)
                .maxParticipants(maxParticipants)
                .participants(new ArrayList<>(participants))
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void join_addsParticipantWhenTokenValidAndCapacityAvailable() {
        when(qrTokenService.validateQrToken("qr-token")).thenReturn("sess-1");
        when(qrTokenService.extractMaxParticipants("qr-token")).thenReturn(4);
        when(sessionRepository.findById("sess-1"))
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
        when(qrTokenService.validateQrToken("qr-token")).thenReturn("sess-1");
        when(qrTokenService.extractMaxParticipants("qr-token")).thenReturn(2);
        when(sessionRepository.findById("sess-1"))
                .thenReturn(Optional.of(openSessionWithCapacity(2, full)));

        assertThatThrownBy(() -> sessionService.joinSession("qr-token", "u3", "C"))
                .isInstanceOf(TooManyParticipantsException.class);
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
        when(qrTokenService.validateQrToken("qr-token")).thenReturn("sess-1");
        when(qrTokenService.extractMaxParticipants("qr-token")).thenReturn(4);
        when(sessionRepository.findById("sess-1"))
                .thenReturn(Optional.of(openSessionWithCapacity(4, existing)));

        assertThatThrownBy(() -> sessionService.joinSession("qr-token", "user-1", "Alice"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already");
    }

    @Test
    void join_publishesParticipantJoinedEvent() {
        when(qrTokenService.validateQrToken("qr-token")).thenReturn("sess-1");
        when(qrTokenService.extractMaxParticipants("qr-token")).thenReturn(4);
        when(sessionRepository.findById("sess-1"))
                .thenReturn(Optional.of(openSessionWithCapacity(4, List.of())));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        sessionService.joinSession("qr-token", "user-1", "Alice");

        ArgumentCaptor<ParticipantJoined> captor = ArgumentCaptor.forClass(ParticipantJoined.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().sessionId()).isEqualTo("sess-1");
        assertThat(captor.getValue().userId()).isEqualTo("user-1");
        assertThat(captor.getValue().userName()).isEqualTo("Alice");
    }

    // --- expandCapacity tests ---

    @Test
    void expandCapacity_increasesByGivenAmount() {
        Session session = openSessionWithCapacity(4, List.of());
        when(sessionRepository.findById("sess-1")).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Session result = sessionService.expandCapacity("sess-1", "waiter@test.com", 2);

        assertThat(result.getMaxParticipants()).isEqualTo(6);
    }

    @Test
    void expandCapacity_throwsWhenNotAssignedWaiter() {
        Session session = openSessionWithCapacity(4, List.of());
        when(sessionRepository.findById("sess-1")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.expandCapacity("sess-1", "other@test.com", 2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("authorized");
    }

    // --- addItem tests ---

    private MenuItem availableMenuItem() {
        return MenuItem.builder()
                .id(10L).name("Tacos").price(new java.math.BigDecimal("12.50"))
                .available(true).build();
    }

    private Session openSessionWithParticipant(String participantId) {
        return Session.builder()
                .id("sess-1").tableId(1L).waiterId("waiter@test.com")
                .status(SessionStatus.OPEN).maxParticipants(4)
                .participants(new ArrayList<>(List.of(
                        Participant.builder().userId(participantId).name("Alice").build())))
                .items(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void addItem_addsOrderItemWithPendingStatus() {
        Session session = openSessionWithParticipant("user-1");
        when(sessionRepository.findById("sess-1")).thenReturn(Optional.of(session));
        when(menuItemService.findById(10L)).thenReturn(availableMenuItem());
        when(tableService.findById(1L)).thenReturn(availableTable());
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Session result = sessionService.addItem("sess-1", "user-1", 10L);

        assertThat(result.getItems()).hasSize(1);
        OrderItem item = result.getItems().get(0);
        assertThat(item.getItemId()).isEqualTo(10L);
        assertThat(item.getName()).isEqualTo("Tacos");
        assertThat(item.getPrice()).isEqualByComparingTo("12.50");
        assertThat(item.getParticipantId()).isEqualTo("user-1");
        assertThat(item.getParticipantName()).isEqualTo("Alice");
        assertThat(item.getStatus()).isEqualTo(OrderItemStatus.PENDING);
        assertThat(item.getAddedAt()).isNotNull();
    }

    @Test
    void addItem_throwsWhenParticipantNotInSession() {
        Session session = openSessionWithParticipant("user-1");
        when(sessionRepository.findById("sess-1")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.addItem("sess-1", "user-99", 10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not a participant");
    }

    @Test
    void addItem_throwsWhenSessionClosed() {
        Session session = openSessionWithParticipant("user-1");
        session.setStatus(SessionStatus.CLOSED);
        when(sessionRepository.findById("sess-1")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.addItem("sess-1", "user-1", 10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed");
    }

    @Test
    void addItem_throwsWhenMenuItemNotAvailable() {
        Session session = openSessionWithParticipant("user-1");
        MenuItem unavailable = availableMenuItem();
        unavailable.setAvailable(false);
        when(sessionRepository.findById("sess-1")).thenReturn(Optional.of(session));
        when(menuItemService.findById(10L)).thenReturn(unavailable);

        assertThatThrownBy(() -> sessionService.addItem("sess-1", "user-1", 10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not available");
    }

    @Test
    void addItem_publishesItemAddedEvent() {
        Session session = openSessionWithParticipant("user-1");
        when(sessionRepository.findById("sess-1")).thenReturn(Optional.of(session));
        when(menuItemService.findById(10L)).thenReturn(availableMenuItem());
        when(tableService.findById(1L)).thenReturn(availableTable());
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        sessionService.addItem("sess-1", "user-1", 10L);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(2)).publishEvent(captor.capture());
        ItemAdded event = captor.getAllValues().stream()
                .filter(e -> e instanceof ItemAdded)
                .map(e -> (ItemAdded) e)
                .findFirst().orElseThrow();
        assertThat(event.sessionId()).isEqualTo("sess-1");
        assertThat(event.itemName()).isEqualTo("Tacos");
        assertThat(event.participantName()).isEqualTo("Alice");
        assertThat(event.status()).isEqualTo(OrderItemStatus.PENDING);
        assertThat(event.sessionItems()).hasSize(1);
    }

    @Test
    void addItem_throwsWhenMenuItemNotFound() {
        Session session = openSessionWithParticipant("user-1");
        when(sessionRepository.findById("sess-1")).thenReturn(Optional.of(session));
        when(menuItemService.findById(99L))
                .thenThrow(new ResourceNotFoundException("Menu item not found: 99"));

        assertThatThrownBy(() -> sessionService.addItem("sess-1", "user-1", 99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void addItem_publishesOrderItemAddedEventToKitchen() {
        Session session = openSessionWithParticipant("user-1");
        when(sessionRepository.findById("sess-1")).thenReturn(Optional.of(session));
        when(menuItemService.findById(10L)).thenReturn(availableMenuItem());
        when(tableService.findById(1L)).thenReturn(availableTable());
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        sessionService.addItem("sess-1", "user-1", 10L);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(2)).publishEvent(captor.capture());
        OrderItemAdded event = captor.getAllValues().stream()
                .filter(e -> e instanceof OrderItemAdded)
                .map(e -> (OrderItemAdded) e)
                .findFirst().orElseThrow();
        assertThat(event.sessionId()).isEqualTo("sess-1");
        assertThat(event.orderItemId()).isNotNull();
        assertThat(event.tableNumber()).isEqualTo(5);
        assertThat(event.itemId()).isEqualTo(10L);
        assertThat(event.itemName()).isEqualTo("Tacos");
        assertThat(event.price()).isEqualByComparingTo("12.50");
        assertThat(event.participantName()).isEqualTo("Alice");
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
                .id("sess-1").tableId(1L).waiterId("waiter@test.com")
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
        when(sessionRepository.findById("sess-1")).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Session result = sessionService.removeItem("sess-1", "order-item-1", "user-1");

        assertThat(result.getItems()).isEmpty();
    }

    @Test
    void removeItem_removesItemWhenWaiterRequests() {
        Session session = openSessionWithItem(OrderItemStatus.PENDING, "user-1");
        when(sessionRepository.findById("sess-1")).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Session result = sessionService.removeItem("sess-1", "order-item-1", "waiter@test.com");

        assertThat(result.getItems()).isEmpty();
    }

    @Test
    void removeItem_throwsWhenItemIsPreparing() {
        Session session = openSessionWithItem(OrderItemStatus.PREPARING, "user-1");
        when(sessionRepository.findById("sess-1")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.removeItem("sess-1", "order-item-1", "user-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("being prepared");
    }

    @Test
    void removeItem_throwsWhenParticipantRemovesOthersItem() {
        Session session = openSessionWithItem(OrderItemStatus.PENDING, "user-1");
        session.getParticipants().add(Participant.builder().userId("user-2").name("Bob").build());
        when(sessionRepository.findById("sess-1")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.removeItem("sess-1", "order-item-1", "user-2"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("owner");
    }

    @Test
    void removeItem_throwsWhenItemNotFound() {
        Session session = openSessionWithItem(OrderItemStatus.PENDING, "user-1");
        when(sessionRepository.findById("sess-1")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.removeItem("sess-1", "nonexistent-id", "user-1"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- handleKitchenItemUpdated tests ---

    @Test
    void handleKitchenItemUpdated_updatesOrderItemStatus() {
        Session session = openSessionWithItem(OrderItemStatus.PENDING, "user-1");
        when(sessionRepository.findById("sess-1")).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        sessionService.handleKitchenItemUpdated(
                new KitchenItemUpdated("sess-1", "order-item-1", OrderItemStatus.PREPARING));

        ArgumentCaptor<Session> captor = ArgumentCaptor.forClass(Session.class);
        verify(sessionRepository).save(captor.capture());
        assertThat(captor.getValue().getItems().get(0).getStatus()).isEqualTo(OrderItemStatus.PREPARING);
    }

    @Test
    void handleKitchenItemUpdated_throwsWhenSessionNotFound() {
        when(sessionRepository.findById("sess-999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.handleKitchenItemUpdated(
                new KitchenItemUpdated("sess-999", "order-item-1", OrderItemStatus.PREPARING)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void handleKitchenItemUpdated_throwsWhenItemNotFound() {
        Session session = openSessionWithItem(OrderItemStatus.PENDING, "user-1");
        when(sessionRepository.findById("sess-1")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.handleKitchenItemUpdated(
                new KitchenItemUpdated("sess-1", "nonexistent-id", OrderItemStatus.PREPARING)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void handleKitchenItemUpdated_publishesItemStatusUpdatedEvent() {
        Session session = openSessionWithItem(OrderItemStatus.PENDING, "user-1");
        when(sessionRepository.findById("sess-1")).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        sessionService.handleKitchenItemUpdated(
                new KitchenItemUpdated("sess-1", "order-item-1", OrderItemStatus.PREPARING));

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(ItemStatusUpdated.class);
        ItemStatusUpdated event = (ItemStatusUpdated) captor.getValue();
        assertThat(event.sessionId()).isEqualTo("sess-1");
        assertThat(event.itemId()).isEqualTo("order-item-1");
        assertThat(event.itemName()).isEqualTo("Tacos");
        assertThat(event.participantName()).isEqualTo("Alice");
        assertThat(event.newStatus()).isEqualTo(OrderItemStatus.PREPARING);
    }

    // --- closeSession tests ---

    @Test
    void closeSession_setsStatusToClosed() {
        Session session = Session.builder()
                .id("sess-1").tableId(1L).waiterId("waiter@test.com")
                .status(SessionStatus.OPEN).maxParticipants(4)
                .createdAt(LocalDateTime.now()).build();
        when(sessionRepository.findById("sess-1")).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        sessionService.closeSession("sess-1");

        ArgumentCaptor<Session> captor = ArgumentCaptor.forClass(Session.class);
        verify(sessionRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(SessionStatus.CLOSED);
    }

    @Test
    void closeSession_throwsWhenSessionNotFound() {
        when(sessionRepository.findById("sess-999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.closeSession("sess-999"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
