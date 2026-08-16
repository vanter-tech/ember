package com.vanter.ember.cashregister.dto;

import java.util.List;

public record CashShiftDetailResponse(CashShiftResponse shift, List<CashMovementResponse> movements) {}
