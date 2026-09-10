package com.vanter.ember.printing.model;

/**
 * One Windows print queue the agent enumerated on its PC (spec §2.3). Reported wholesale on
 * every connect/refetch and stored as a JSON array on {@link PrintAgent#getDiscoveredPrinters()}
 * — no history. {@code inkjetGuess} is the agent's heuristic that this queue is a driver-only
 * inkjet (EcoTank etc.) so the admin UI can pre-pick {@code DRIVER} render mode.
 */
public record DiscoveredPrinter(String name, String driverName, String portName, boolean inkjetGuess) {}
