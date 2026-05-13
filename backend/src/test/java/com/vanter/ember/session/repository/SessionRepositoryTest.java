package com.vanter.ember.session.repository;

import com.vanter.ember.session.model.Participant;
import com.vanter.ember.session.model.Session;
import com.vanter.ember.session.model.SessionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
class SessionRepositoryTest {

    @Autowired SessionRepository sessionRepository;

    @BeforeEach
    void setUp() {
        sessionRepository.deleteAll();
    }

    @Test
    void save_persistsSession() {
        Session session = Session.builder()
                .tableId(1L).waiterId(10L)
                .status(SessionStatus.OPEN)
                .maxParticipants(4)
                .createdAt(LocalDateTime.now())
                .build();

        Session saved = sessionRepository.save(session);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(SessionStatus.OPEN);
    }

    @Test
    void findByTableIdAndStatus_returnsMatchingSessions() {
        sessionRepository.save(Session.builder().tableId(1L).waiterId(10L)
                .status(SessionStatus.OPEN).maxParticipants(4)
                .createdAt(LocalDateTime.now()).build());
        sessionRepository.save(Session.builder().tableId(1L).waiterId(10L)
                .status(SessionStatus.CLOSED).maxParticipants(4)
                .createdAt(LocalDateTime.now()).build());
        sessionRepository.save(Session.builder().tableId(2L).waiterId(10L)
                .status(SessionStatus.OPEN).maxParticipants(4)
                .createdAt(LocalDateTime.now()).build());

        List<Session> result = sessionRepository.findByTableIdAndStatus(1L, SessionStatus.OPEN);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTableId()).isEqualTo(1L);
        assertThat(result.get(0).getStatus()).isEqualTo(SessionStatus.OPEN);
    }

    @Test
    void findByParticipants_UserId_returnsSessionsForUser() {
        Participant alice = Participant.builder().userId("user-1").name("Alice").build();
        Participant bob = Participant.builder().userId("user-2").name("Bob").build();

        sessionRepository.save(Session.builder().tableId(1L).waiterId(10L)
                .status(SessionStatus.OPEN).maxParticipants(4)
                .participants(List.of(alice))
                .createdAt(LocalDateTime.now()).build());
        sessionRepository.save(Session.builder().tableId(2L).waiterId(10L)
                .status(SessionStatus.OPEN).maxParticipants(4)
                .participants(List.of(bob))
                .createdAt(LocalDateTime.now()).build());

        List<Session> result = sessionRepository.findByParticipants_UserId("user-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getParticipants().get(0).getUserId()).isEqualTo("user-1");
    }
}
