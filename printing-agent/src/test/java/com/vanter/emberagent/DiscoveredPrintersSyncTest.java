package com.vanter.emberagent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class DiscoveredPrintersSyncTest {

    private static DiscoveredPrinter printer(String name) {
        return new DiscoveredPrinter(name, "driver", "USB001", false);
    }

    private final List<DiscoveredPrinter> onThisPc = new ArrayList<>();
    private final List<List<DiscoveredPrinter>> reports = new ArrayList<>();
    private final AtomicBoolean backendAccepts = new AtomicBoolean(true);

    private DiscoveredPrintersSync sync() {
        return new DiscoveredPrintersSync(() -> List.copyOf(onThisPc), list -> {
            reports.add(list);
            return backendAccepts.get();
        });
    }

    @Test
    void reportNow_alwaysSendsTheCurrentList() {
        onThisPc.add(printer("EPSON L3210 Series"));
        DiscoveredPrintersSync sync = sync();

        sync.reportNow();
        sync.reportNow();

        assertEquals(2, reports.size());
    }

    @Test
    void reportIfChanged_sendsOnlyWhenTheListChanged_soNoReconnectIsNeededToSeeANewPrinter() {
        onThisPc.add(printer("EPSON L3210 Series"));
        DiscoveredPrintersSync sync = sync();
        sync.reportNow();

        sync.reportIfChanged(); // nothing new
        assertEquals(1, reports.size());

        onThisPc.add(printer("XP-80C")); // a printer is installed while the agent is connected
        sync.reportIfChanged();
        assertEquals(2, reports.size());
        assertEquals(2, reports.get(1).size());

        sync.reportIfChanged(); // same list again
        assertEquals(2, reports.size());
    }

    @Test
    void reportIfChanged_alsoNoticesARemovedOrRenamedQueue() {
        onThisPc.add(printer("EPSON L310 Series"));
        DiscoveredPrintersSync sync = sync();
        sync.reportNow();

        onThisPc.clear();
        onThisPc.add(printer("EPSON L3210 Series"));
        sync.reportIfChanged();

        assertEquals(2, reports.size());
        assertEquals("EPSON L3210 Series", reports.get(1).get(0).name());
    }

    @Test
    void aRejectedDelivery_isRetriedOnTheNextCheck_evenIfTheListDidNotChange() {
        onThisPc.add(printer("EPSON L3210 Series"));
        backendAccepts.set(false);
        DiscoveredPrintersSync sync = sync();
        sync.reportNow();

        sync.reportIfChanged(); // still not delivered -> tries again
        assertEquals(2, reports.size());

        backendAccepts.set(true);
        sync.reportIfChanged();
        assertEquals(3, reports.size());

        sync.reportIfChanged(); // delivered now: quiet again
        assertEquals(3, reports.size());
    }

    @Test
    void aFailingBackend_neverBreaksTheCaller() {
        onThisPc.add(printer("EPSON L3210 Series"));
        DiscoveredPrintersSync sync = new DiscoveredPrintersSync(() -> List.copyOf(onThisPc), list -> {
            throw new IllegalStateException("backend down");
        });

        sync.reportNow();
        sync.reportIfChanged();
    }
}
