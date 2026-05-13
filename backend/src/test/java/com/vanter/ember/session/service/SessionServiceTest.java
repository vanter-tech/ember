package com.vanter.ember.session.service;

import com.vanter.ember.catalog.model.RestaurantTable;
import com.vanter.ember.catalog.model.TableStatus;
import com.vanter.ember.catalog.service.RestaurantTableService;
import com.vanter.ember.session.event.SessionOpened;
import com.vanter.ember.session.model.Session;
import com.vanter.ember.session.model.SessionStatus;
import com.vanter.ember.session.repository.SessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

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
}
