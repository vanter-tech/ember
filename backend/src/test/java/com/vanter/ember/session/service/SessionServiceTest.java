package com.vanter.ember.session.service;

import com.vanter.ember.catalog.model.RestaurantTable;
import com.vanter.ember.catalog.model.TableStatus;
import com.vanter.ember.catalog.service.RestaurantTableService;
import com.vanter.ember.session.event.SessionOpened;
import com.vanter.ember.session.exception.TooManyParticipantsException;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock SessionRepository sessionRepository;
    @Mock RestaurantTableService tableService;
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

        Session result = sessionService.createSession(1L, 10L, 4);

        assertThat(result.getId()).isEqualTo("sess-1");
        assertThat(result.getStatus()).isEqualTo(SessionStatus.OPEN);
        assertThat(result.getTableId()).isEqualTo(1L);
        assertThat(result.getWaiterId()).isEqualTo(10L);
        assertThat(result.getMaxParticipants()).isEqualTo(4);
    }

    @Test
    void createSession_setsCreatedAt() {
        when(tableService.findById(1L)).thenReturn(availableTable());
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Session result = sessionService.createSession(1L, 10L, 4);

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

        sessionService.createSession(1L, 10L, 4);

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

        assertThatThrownBy(() -> sessionService.createSession(1L, 10L, 4))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("occupied");
    }

    // --- join tests ---

    private Session openSessionWithCapacity(int maxParticipants, List<Participant> participants) {
        return Session.builder()
                .id("sess-1").tableId(1L).waiterId(10L)
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

    // --- expandCapacity tests ---

    @Test
    void expandCapacity_increasesByGivenAmount() {
        Session session = openSessionWithCapacity(4, List.of());
        when(sessionRepository.findById("sess-1")).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Session result = sessionService.expandCapacity("sess-1", 10L, 2);

        assertThat(result.getMaxParticipants()).isEqualTo(6);
    }

    @Test
    void expandCapacity_throwsWhenNotAssignedWaiter() {
        Session session = openSessionWithCapacity(4, List.of());
        when(sessionRepository.findById("sess-1")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.expandCapacity("sess-1", 99L, 2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("authorized");
    }
}
