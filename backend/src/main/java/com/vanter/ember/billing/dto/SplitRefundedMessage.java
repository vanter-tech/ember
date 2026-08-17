package com.vanter.ember.billing.dto;

import java.math.BigDecimal;

public record SplitRefundedMessage(String type, Long billId, String participantName, String status, BigDecimal amount) {

    public static SplitRefundedMessage of(Long billId, String participantName, String status, BigDecimal amount) {
        return new SplitRefundedMessage("SPLIT_REFUNDED", billId, participantName, status, amount);
    }
}
