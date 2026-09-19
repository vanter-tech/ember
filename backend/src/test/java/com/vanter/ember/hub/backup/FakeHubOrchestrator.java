package com.vanter.ember.hub.backup;

import com.vanter.ember.hub.control.HubOrchestrator;
import com.vanter.ember.hub.control.ServicePhase;
import java.nio.file.Path;

/** Test double shared by the backup tests. */
final class FakeHubOrchestrator implements HubOrchestrator {
    ServicePhase postgres = ServicePhase.RUNNING;

    @Override public void start(String[] launchArgs) {}
    @Override public void stop() {}
    @Override public void installLicense(Path source) {}
    @Override public void removeLicense() {}

    @Override
    public HubStatusSnapshot snapshot() {
        return new HubStatusSnapshot(postgres, null, ServicePhase.STOPPED, null, ServicePhase.STOPPED, null,
                new LicenseSnapshot(LicenseSnapshot.NONE, null, null), 8080);
    }
}
