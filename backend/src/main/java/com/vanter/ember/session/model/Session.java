package com.vanter.ember.session.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Session {

    @Id
    private String id;

    @Version
    private Long version;

    private UUID tenantId;

    private UUID tableId;
    private String waiterId;

    private SessionStatus status;

    private int maxParticipants;

    @Builder.Default
    private List<Participant> participants = new ArrayList<>();

    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @Builder.Default
    private List<SessionActivity> activityLog = new ArrayList<>();

    private String joinCode;

    private LocalDateTime createdAt;
}
