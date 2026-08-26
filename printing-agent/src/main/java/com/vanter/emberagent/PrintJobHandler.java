package com.vanter.emberagent;

import java.util.List;

/**
 * Refetches this agent's printer list from the backend on every job, instead of reusing the
 * snapshot fetched once when the STOMP session was opened. A print-agent connection is
 * long-lived (hours/days) and a printer can be added, edited, or deactivated at any point during
 * that window — a stale snapshot would silently keep routing (or failing to route) jobs against
 * config that no longer matches what's in the admin UI, with no way to tell from the outside
 * (found while continuing PRINT-07 debugging, 2026-08-26).
 */
public class PrintJobHandler {

    private final PrinterConfigClient printerConfigClient;
    private final PrintJobDispatcher dispatcher;
    private final String backendBaseUrl;
    private final String jwt;

    public PrintJobHandler(
            PrinterConfigClient printerConfigClient,
            PrintJobDispatcher dispatcher,
            String backendBaseUrl,
            String jwt) {
        this.printerConfigClient = printerConfigClient;
        this.dispatcher = dispatcher;
        this.backendBaseUrl = backendBaseUrl;
        this.jwt = jwt;
    }

    public void handle(AgentConnection.PrintJobPayload job, PrintJobDispatcher.AckCallback ackCallback) {
        List<PrinterConfigClient.PrinterConfigDto> printers;
        try {
            printers = printerConfigClient.fetchMyPrinters(backendBaseUrl, jwt);
        } catch (Exception e) {
            String error = "No se pudo obtener la configuracion de impresoras: " + e.getMessage();
            System.err.println("[print-agent] " + error + " (job " + job.jobId() + ")");
            ackCallback.ack(job.jobId(), null, "ERROR", error);
            return;
        }
        dispatcher.dispatch(job, printers, ackCallback);
    }
}
