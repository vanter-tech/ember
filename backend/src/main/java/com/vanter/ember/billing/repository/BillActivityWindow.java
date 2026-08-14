package com.vanter.ember.billing.repository;

import java.time.LocalDateTime;

/** Aggregate projection over {@code bills} — earliest/latest bill and how many exist. */
public record BillActivityWindow(LocalDateTime firstBillAt, LocalDateTime lastBillAt, Long billCount) {}
