package com.vanter.ember.billing.dto;

import java.math.BigDecimal;

public record DigitalPaymentInitiatedMessage(
        String type, Long paymentId, Long billId, String participantName, BigDecimal amount) {

    public static DigitalPaymentInitiatedMessage of(
            Long paymentId, Long billId, String participantName, BigDecimal amount) {
        return new DigitalPaymentInitiatedMessage(
                "DIGITAL_PAYMENT_INITIATED", paymentId, billId, participantName, amount);
    }
}
