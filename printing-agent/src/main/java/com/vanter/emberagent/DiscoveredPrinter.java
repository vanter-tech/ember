package com.vanter.emberagent;

/**
 * One Windows print queue the agent enumerated on its PC (spec §2.3). Agent-side twin of
 * {@code com.vanter.ember.printing.model.DiscoveredPrinter} — the agent module can't import
 * backend classes, but the field names must stay identical so the JSON round-trips.
 */
public record DiscoveredPrinter(String name, String driverName, String portName, boolean inkjetGuess) {}
