package com.vanter.ember.session.service;

import com.vanter.ember.catalog.model.TableStatus;
import com.vanter.ember.catalog.service.RestaurantTableService;
import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.session.event.SessionOpened;
import com.vanter.ember.session.exception.TooManyParticipantsException;
import com.vanter.ember.session.model.Participant;
import com.vanter.ember.session.model.Session;
import com.vanter.ember.session.model.SessionStatus;
import com.vanter.ember.session.repository.SessionRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final SessionRepository sessionRepository;
    private final RestaurantTableService tableService;
    private final ApplicationEventPublisher eventPublisher;
    private final QrTokenService qrTokenService;

    public Session createSession(Long tableId, Long waiterId, int maxParticipants) {
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
        return sessionRepository.save(session);
    }
}
