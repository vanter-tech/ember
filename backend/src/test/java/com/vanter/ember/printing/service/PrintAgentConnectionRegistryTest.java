package com.vanter.ember.printing.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PrintAgentConnectionRegistryTest {

    private final PrintAgentConnectionRegistry registry = new PrintAgentConnectionRegistry();

    @Test
    void connectedAgentsAt_findsTheAgentsThatConnectedFromThatAddress() {
        UUID caja1 = UUID.randomUUID();
        UUID caja2 = UUID.randomUUID();
        registry.markConnected(caja1, "s1", "203.0.113.21");
        registry.markConnected(caja2, "s2", "203.0.113.22");

        assertThat(registry.connectedAgentsAt("203.0.113.21")).containsExactly(caja1);
        assertThat(registry.connectedAgentsAt("203.0.113.22")).containsExactly(caja2);
        assertThat(registry.connectedAgentsAt("203.0.113.99")).isEmpty();
        assertThat(registry.connectedAgentsAt(null)).isEmpty();
    }

    @Test
    void anAgentOnTheHubsOwnPc_matchesWhicheverLoopbackTheBrowserUses() {
        UUID hubPcAgent = UUID.randomUUID();
        registry.markConnected(hubPcAgent, "s1", "127.0.0.1");

        assertThat(registry.connectedAgentsAt(SourceIps.normalize("0:0:0:0:0:0:0:1"))).containsExactly(hubPcAgent);
    }

    @Test
    void disconnect_forgetsTheAddress() {
        UUID agent = UUID.randomUUID();
        registry.markConnected(agent, "s1", "203.0.113.21");

        registry.markDisconnected("s1");

        assertThat(registry.isConnected(agent)).isFalse();
        assertThat(registry.connectedAgentsAt("203.0.113.21")).isEmpty();
    }

    @Test
    void aStaleSessionDisconnect_doesNotDropTheNewerConnection() {
        UUID agent = UUID.randomUUID();
        registry.markConnected(agent, "old", "203.0.113.21");
        registry.markConnected(agent, "new", "203.0.113.30");

        registry.markDisconnected("old");

        assertThat(registry.isConnected(agent)).isTrue();
        assertThat(registry.connectedAgentsAt("203.0.113.30")).containsExactly(agent);
    }

    @Test
    void connectingWithoutAnAddress_isStillConnected_butNeverRoutable() {
        UUID agent = UUID.randomUUID();
        registry.markConnected(agent, "s1");

        assertThat(registry.isConnected(agent)).isTrue();
        assertThat(registry.connectedAgentsAt("203.0.113.21")).isEmpty();
    }
}
