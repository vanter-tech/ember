package com.vanter.ember.cashregister.exception;

/**
 * Raised by the two write paths that stamp a payment/movement onto the open till
 * ({@code PaymentService.registerPhysicalPayment}, {@code CashShiftService.recordMovement})
 * when that till is past its effective deadline and has not been prolonged. Mapped to HTTP 409
 * with a stable {@code code} so the frontend can tell it apart from the "tables still open" 409.
 */
public class CashShiftOverdueException extends RuntimeException {
    public CashShiftOverdueException(String message) {
        super(message);
    }
}
