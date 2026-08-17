package com.vanter.ember.cashregister.dto;

import com.vanter.ember.billing.dto.PaymentResponse;
import java.util.List;

public record CashShiftDetailResponse(
        CashShiftResponse shift, List<CashMovementResponse> movements, List<PaymentResponse> payments) {}
