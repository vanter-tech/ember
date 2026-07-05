package com.vanter.ember.session.repository;

import com.vanter.ember.session.model.Session;
import com.vanter.ember.session.model.SessionStatus;
import java.util.List;
import java.util.UUID;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface SessionRepository extends MongoRepository<Session, String> {

    List<Session> findByTableIdAndStatus(UUID tableId, SessionStatus status);

    List<Session> findByParticipants_UserId(String userId);

    List<Session> findByTableIdInAndStatus(List<UUID> tableIds, SessionStatus status);
}
