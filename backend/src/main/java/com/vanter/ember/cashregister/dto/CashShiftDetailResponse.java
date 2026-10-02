package com.vanter.ember.cashregister.dto;

import com.vanter.ember.billing.dto.PaymentResponse;
import com.vanter.ember.billing.dto.ShiftRefundResponse;
import com.vanter.ember.billing.dto.VoidedBillResponse;
import java.util.List;

/**
 * Everything behind one shift's numbers. {@code refunds} are those of the payments taken in the
 * shift (whenever they were refunded); {@code voidedBills} are the bills voided while the shift was open.
 */
public record CashShiftDetailResponse(
        CashShiftResponse shift,
        List<CashMovementResponse> movements,
        List<PaymentResponse> payments,
        List<ShiftRefundResponse> refunds,
        List<VoidedBillResponse> voidedBills) {}
