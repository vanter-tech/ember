package com.vanter.ember.cashregister.dto;

/** {@code status}: PENDING (waiting for the accountant) | RECEIVED, for one cash payment of a table's bill. */
public record CashReceiptStatusResponse(String participantName, String status) {}
