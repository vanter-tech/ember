package com.vanter.emberagent;

import java.util.List;

/**
 * Matches an incoming {@link AgentConnection.PrintJobPayload} against this agent's known
 * printers by role, prints to each match, and reports the outcome via {@link AckCallback} —
 * pulled out of {@link Main}'s connection loop so it's testable without a real STOMP session
 * (same "single testable responsibility" rationale as {@link AgentConnection}'s own javadoc).
 *
 * <p>Logs every step to stdout/stderr: a job that reaches the agent but is never acked back
 * (e.g. because no printer matches its role) used to look identical, from the backend's point
 * of view, to hardware that printed successfully but forgot to ack — {@code print_jobs} just
 * stayed {@code SENT} forever in both cases, with no way to tell them apart (found during
 * PRINT-07 debugging, 2026-08-26).
 */
public class PrintJobDispatcher {

    public interface AckCallback {
        void ack(String jobId, String printerConfigId, String result, String error);
    }

    private final NetworkPrinterSender networkPrinterSender;
    private final UsbPrinterSender usbPrinterSender;
    private final WindowsPrintQueueSender windowsPrintQueueSender;

    public PrintJobDispatcher(
            NetworkPrinterSender networkPrinterSender,
            UsbPrinterSender usbPrinterSender,
            WindowsPrintQueueSender windowsPrintQueueSender) {
        this.networkPrinterSender = networkPrinterSender;
        this.usbPrinterSender = usbPrinterSender;
        this.windowsPrintQueueSender = windowsPrintQueueSender;
    }

    public void dispatch(
            AgentConnection.PrintJobPayload job,
            List<PrinterConfigClient.PrinterConfigDto> printers,
            AckCallback ackCallback) {
        System.out.println("[print-agent] job recibido id=" + job.jobId() + " role=" + job.role()
                + " impresoras conocidas=" + printers.size());

        boolean matched = false;
        for (PrinterConfigClient.PrinterConfigDto printer : printers) {
            if (!printer.role().equals(job.role())) {
                continue;
            }
            matched = true;
            System.out.println("[print-agent] enviando job " + job.jobId() + " a impresora '"
                    + printer.label() + "' (" + printer.connectionType() + ")");
            try {
                if ("NETWORK".equals(printer.connectionType())) {
                    networkPrinterSender.print(printer, job.payload());
                } else if ("WINDOWS_QUEUE".equals(printer.connectionType())) {
                    windowsPrintQueueSender.print(printer, job.payload());
                } else {
                    usbPrinterSender.print(printer, job.payload());
                }
                System.out.println("[print-agent] job " + job.jobId() + " impreso correctamente en '"
                        + printer.label() + "'");
                ackCallback.ack(job.jobId(), printer.id(), "PRINTED", null);
            } catch (Exception e) {
                System.err.println("[print-agent] ERROR imprimiendo job " + job.jobId() + " en '"
                        + printer.label() + "': " + e.getMessage());
                ackCallback.ack(job.jobId(), printer.id(), "ERROR", e.getMessage());
            }
        }

        if (!matched) {
            String error = "No hay impresora activa configurada para el rol " + job.role()
                    + " en este agente";
            System.err.println("[print-agent] " + error + " (job " + job.jobId() + ")");
            ackCallback.ack(job.jobId(), null, "ERROR", error);
        }
    }
}
