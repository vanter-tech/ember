package com.vanter.ember.billing.dto;

public record SplitPaidMessage(String type, Long billId, String participantName, String status) {

    public static SplitPaidMessage of(Long billId, String participantName, String status) {
        return new SplitPaidMessage("SPLIT_PAID", billId, participantName, status);
    }
}
