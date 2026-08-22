package com.vanter.ember.printing.dto;

import java.util.UUID;

/** Sent agent → backend over {@code /app/print-agent/ack}. */
public record PrintJobAck(UUID jobId, UUID printerConfigId, String result, String error) {}
