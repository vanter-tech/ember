package com.vanter.emberagent;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Keeps the backend's picture of this PC's Windows print queues current. The admin's "add printer"
 * dropdown is fed by what the agent last reported; when that happened only once per connection, a
 * printer installed (or renamed) afterwards did not show up until the operator re-paired. This
 * re-reports whenever the enumerated list differs from the last one the backend accepted, and a
 * failed delivery is simply retried on the next check.
 */
final class DiscoveredPrintersSync {

    private final Supplier<List<DiscoveredPrinter>> enumerate;
    private final Predicate<List<DiscoveredPrinter>> report;
    private List<DiscoveredPrinter> lastDelivered;

    /** @param report returns true when the backend accepted the list */
    DiscoveredPrintersSync(Supplier<List<DiscoveredPrinter>> enumerate, Predicate<List<DiscoveredPrinter>> report) {
        this.enumerate = enumerate;
        this.report = report;
    }

    /** Always reports (used right after connecting). */
    void reportNow() {
        push(enumerate.get());
    }

    /** Reports only if the queues changed since the last accepted report. */
    void reportIfChanged() {
        List<DiscoveredPrinter> current = enumerate.get();
        if (!current.equals(lastDelivered)) {
            push(current);
        }
    }

    private void push(List<DiscoveredPrinter> printers) {
        try {
            if (report.test(printers)) {
                lastDelivered = List.copyOf(printers);
            }
        } catch (RuntimeException e) {
            // best-effort: never break the session; the next check retries
        }
    }
}
