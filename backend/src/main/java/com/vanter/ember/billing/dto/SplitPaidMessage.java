package com.vanter.ember.billing.dto;

public record SplitPaidMessage(String type, Long billId, String participantName, boolean paid) {

    public static SplitPaidMessage of(Long billId, String participantName, boolean paid) {
        return new SplitPaidMessage("SPLIT_PAID", billId, participantName, paid);
    }
}
