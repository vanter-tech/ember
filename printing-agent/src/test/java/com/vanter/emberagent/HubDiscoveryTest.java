package com.vanter.emberagent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.net.http.HttpClient;
import java.util.List;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HubDiscoveryTest {

    private MockWebServer server;

    @BeforeEach
    void start() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void stop() {
        server.close();
    }

    private HubDiscovery discoveryOn(int port) {
        HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        return new HubDiscovery(http, List::of, port);
    }

    @Test
    void aServerWhoseRootRedirectsToTheHubApp_isFound_onThisPc() {
        server.enqueue(new MockResponse.Builder().code(302).addHeader("Location", "/app/").build());

        List<String> found = discoveryOn(server.getPort()).discover();

        assertEquals(List.of("http://127.0.0.1:" + server.getPort()), found);
    }

    @Test
    void anAbsoluteRedirectToTheHubApp_alsoCounts() {
        server.enqueue(new MockResponse.Builder()
                .code(302).addHeader("Location", "http://192.168.1.10:8080/app/").build());

        assertEquals(1, discoveryOn(server.getPort()).discover().size());
    }

    @Test
    void aRedirectSomewhereElse_isNotAHub() {
        server.enqueue(new MockResponse.Builder().code(302).addHeader("Location", "/login").build());

        assertTrue(discoveryOn(server.getPort()).discover().isEmpty());
    }

    @Test
    void aPlainWebServer_isNotAHub() {
        server.enqueue(new MockResponse.Builder().code(404).body("nope").build());

        assertTrue(discoveryOn(server.getPort()).discover().isEmpty());
    }

    @Test
    void nothingListening_findsNothing() {
        int deadPort = server.getPort();
        server.close();

        assertTrue(discoveryOn(deadPort).discover().isEmpty());
    }

    @Test
    void subnetHosts_ofAClassCLan_coversEveryOtherHostButNotNetworkOrBroadcast() throws Exception {
        List<String> hosts = HubDiscovery.subnetHosts(InetAddress.getByName("192.168.1.21"), 24);

        assertEquals(253, hosts.size());
        assertTrue(hosts.contains("192.168.1.1"));
        assertTrue(hosts.contains("192.168.1.254"));
        assertFalse(hosts.contains("192.168.1.21"), "not itself");
        assertFalse(hosts.contains("192.168.1.0"));
        assertFalse(hosts.contains("192.168.1.255"));
    }

    @Test
    void subnetHosts_widerThanA24_isNarrowedToTheSurrounding24() throws Exception {
        List<String> hosts = HubDiscovery.subnetHosts(InetAddress.getByName("10.20.30.40"), 8);

        assertEquals(253, hosts.size());
        assertTrue(hosts.stream().allMatch(h -> h.startsWith("10.20.30.")));
    }

    @Test
    void subnetHosts_ofASmallerSubnet_keepsItsRealSize() throws Exception {
        // 192.168.1.70/26 -> 192.168.1.64 .. .127: hosts .65-.126 minus itself
        List<String> hosts = HubDiscovery.subnetHosts(InetAddress.getByName("192.168.1.70"), 26);

        assertEquals(61, hosts.size());
        assertTrue(hosts.contains("192.168.1.65"));
        assertTrue(hosts.contains("192.168.1.126"));
        assertFalse(hosts.contains("192.168.1.70"));
        assertFalse(hosts.contains("192.168.1.130"));
    }
}
