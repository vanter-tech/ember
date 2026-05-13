package com.vanter.ember.billing.event;

import com.vanter.ember.billing.model.SplitMethod;

public record BillingRequested(String sessionId, SplitMethod splitMethod, int participantCount) {}
