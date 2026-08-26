package com.vanter.ember.printing.model;

/**
 * Only meaningful for {@link ConnectionType#WINDOWS_QUEUE} — {@code RAW} submits ESC/POS bytes
 * straight to the spooler (bypasses the queue's driver, needs a real ESC/POS-capable printer);
 * {@code DRIVER} lets the queue's own Windows driver rasterize plain text, for printers with no
 * ESC/POS support (e.g. inkjet queues like "EPSON L3210 Series") that only work through their
 * driver. {@link ConnectionType#NETWORK}/{@link ConnectionType#USB} configs always default to
 * {@code RAW} and ignore this field — the agent only reads it for {@code WINDOWS_QUEUE}.
 */
public enum PrinterRenderMode { RAW, DRIVER }
