package com.vanter.ember.billing.dto;

public record BillVoidedMessage(String type, Long billId, String reason) {

    public static BillVoidedMessage of(Long billId, String reason) {
        return new BillVoidedMessage("BILL_VOIDED", billId, reason);
    }
}
