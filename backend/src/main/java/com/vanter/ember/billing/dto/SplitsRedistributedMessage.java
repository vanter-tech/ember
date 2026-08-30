package com.vanter.ember.billing.dto;

import com.vanter.ember.billing.model.BillSplit;
import java.util.List;

/**
 * Broadcast on {@code /topic/session/{sessionId}} when a departing diner's unpaid share is
 * spread across the participants still present. {@code splits} is the full post-redistribution
 * split list so every client can replace its view without a follow-up fetch.
 */
public record SplitsRedistributedMessage(
        String type, Long billId, String departedParticipantName, List<BillSplit> splits) {

    public static SplitsRedistributedMessage of(
            Long billId, String departedParticipantName, List<BillSplit> splits) {
        return new SplitsRedistributedMessage(
                "SPLITS_REDISTRIBUTED", billId, departedParticipantName, splits);
    }
}
