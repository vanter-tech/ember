package com.vanter.ember.printing.exception;

/** Raised when a bill receipt is requested for a bill that is not {@code PAID}. Mapped to HTTP 409
 *  with {@code code: BILL_NOT_PAID} by {@code GlobalExceptionHandler}. */
public class BillNotPaidException extends RuntimeException {
    public BillNotPaidException(Long billId) {
        super("Bill " + billId + " is not fully paid");
    }
}
