package com.vanter.ember.licensing.model.dto;

import java.time.Instant;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class HubHeartbeatResponse {
    private String status;
    private Instant serverTime;
    private String latestVersion;

    /** RSA signature over status + serverTime + the request's nonce; null when no nonce was sent. */
    private String signature;
}
