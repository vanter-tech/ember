package com.vanter.ember.billing.event;

public record PaymentCompleted(String sessionId, java.util.UUID tableId, Long billId) {}
