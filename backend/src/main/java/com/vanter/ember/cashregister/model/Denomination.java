package com.vanter.ember.cashregister.model;

import java.math.BigDecimal;

/** One legal córdoba denomination — a fixed catalog entry, never persisted on its own. */
public record Denomination(String id, BigDecimal value, DenominationKind kind) {}
