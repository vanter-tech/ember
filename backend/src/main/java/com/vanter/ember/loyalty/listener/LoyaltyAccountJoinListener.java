package com.vanter.ember.loyalty.listener;

import com.vanter.ember.loyalty.service.LoyaltyAccountService;
import com.vanter.ember.session.event.ParticipantJoined;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Table-join is what links a customer to a tenant's loyalty program (decision #2 — never
 * registration/login). {@link ParticipantJoined} already fires exactly once per true first-join
 * from both {@code SessionService.joinSession} and {@code joinSessionCode}, so this is the one
 * hook needed for both paths.
 */
@Component
@RequiredArgsConstructor
public class LoyaltyAccountJoinListener {

    private final LoyaltyAccountService loyaltyAccountService;

    @EventListener
    public void handleParticipantJoined(ParticipantJoined event) {
        loyaltyAccountService.findOrCreate(event.tenantId(), event.userId());
    }
}
