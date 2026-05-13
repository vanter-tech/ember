package com.vanter.ember.billing.event;

public record PaymentCompleted(String sessionId, Long tableId, Long billId) {}
