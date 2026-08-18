package com.vanter.ember.loyalty.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LoyaltyVisitResponse(LocalDateTime visitedAt, BigDecimal amountPaid, int pointsEarned) {}
