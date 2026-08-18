package com.vanter.ember.loyalty.listener;

import static org.mockito.Mockito.verify;

import com.vanter.ember.loyalty.service.LoyaltyAccountService;
import com.vanter.ember.session.event.ParticipantJoined;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoyaltyAccountJoinListenerTest {

    @Mock LoyaltyAccountService loyaltyAccountService;
    @InjectMocks LoyaltyAccountJoinListener listener;

    @Test
    void handleParticipantJoined_delegatesToFindOrCreate() {
        UUID tenantId = UUID.randomUUID();
        ParticipantJoined event = new ParticipantJoined(tenantId, "sess-1", "user-1", "Alice");

        listener.handleParticipantJoined(event);

        verify(loyaltyAccountService).findOrCreate(tenantId, "user-1");
    }
}
