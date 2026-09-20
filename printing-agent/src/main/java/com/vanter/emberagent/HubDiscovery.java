package com.vanter.emberagent;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Finds the Ember Hub on the local network, so an operator pairing a caja never types an address.
 * This PC is tried first (Hub and agent on the same machine), then every host of the local
 * subnet(s) on the Hub's port.
 *
 * <p>A host counts as a Hub only if {@code GET /} answers with a redirect to {@code /app/} — the
 * Hub's bundled web app root. The cloud, or any other web server, does not do that. This is a
 * plain LAN convenience, not authentication: the pairing code is what proves the right server,
 * and {@link PairingClient#redeemAny} tries the code against every candidate.
 */
public class HubDiscovery {

    public static final int HUB_PORT = 8080;

    private static final Duration CONNECT_TIMEOUT = Duration.ofMillis(350);
    private static final Duration REQUEST_TIMEOUT = Duration.ofMillis(900);
    private static final long SCAN_DEADLINE_SECONDS = 12;
    private static final int SCAN_THREADS = 64;
    private static final String THIS_PC = "127.0.0.1";

    private final HttpClient http;
    private final Supplier<List<String>> lanHosts;
    private final int port;

    public HubDiscovery() {
        this(HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .connectTimeout(CONNECT_TIMEOUT)
                        .build(),
                HubDiscovery::lanHostsToScan,
                HUB_PORT);
    }

    HubDiscovery(HttpClient http, Supplier<List<String>> lanHosts, int port) {
        this.http = http;
        this.lanHosts = lanHosts;
        this.port = port;
    }

    /** Base URLs of the Hubs found, this PC's own first; empty when there is none. */
    public List<String> discover() {
        Set<String> hosts = new LinkedHashSet<>();
        hosts.add(THIS_PC);
        hosts.addAll(lanHosts.get());

        ExecutorService pool = Executors.newFixedThreadPool(SCAN_THREADS, r -> {
            Thread t = new Thread(r, "hub-discovery");
            t.setDaemon(true);
            return t;
        });
        try {
            List<Callable<String>> probes = new ArrayList<>();
            for (String host : hosts) {
                probes.add(() -> isHub(host) ? "http://" + host + ":" + port : null);
            }
            List<String> found = new ArrayList<>();
            for (Future<String> result : pool.invokeAll(probes, SCAN_DEADLINE_SECONDS, TimeUnit.SECONDS)) {
                if (result.isCancelled()) {
                    continue;
                }
                try {
                    String url = result.get();
                    if (url != null) {
                        found.add(url);
                    }
                } catch (Exception ignored) {
                    // a host that errored is simply not a Hub
                }
            }
            return found; // invokeAll keeps submission order, so this PC comes first
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        } finally {
            pool.shutdownNow();
        }
    }

    private boolean isHub(String host) {
        if (!portOpen(host)) {
            return false;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + host + ":" + port + "/"))
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            if (status != 301 && status != 302 && status != 303 && status != 307 && status != 308) {
                return false;
            }
            return response.headers().firstValue("Location")
                    .map(HubDiscovery::pointsAtTheHubApp)
                    .orElse(false);
        } catch (IOException | RuntimeException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private boolean portOpen(String host) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), (int) CONNECT_TIMEOUT.toMillis());
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean pointsAtTheHubApp(String location) {
        try {
            String path = URI.create(location).getPath();
            return "/app/".equals(path) || "/app".equals(path);
        } catch (RuntimeException e) {
            return false;
        }
    }

    // --- subnet enumeration -----------------------------------------------------

    /** Every other host of each up, non-loopback IPv4 subnet this PC is on. */
    static List<String> lanHostsToScan() {
        Set<String> hosts = new LinkedHashSet<>();
        try {
            for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nic.isUp() || nic.isLoopback() || nic.isVirtual()) {
                    continue;
                }
                nic.getInterfaceAddresses().stream()
                        .filter(a -> a.getAddress() instanceof Inet4Address)
                        .filter(a -> !a.getAddress().isLinkLocalAddress())
                        .forEach(a -> hosts.addAll(subnetHosts(a.getAddress(), a.getNetworkPrefixLength())));
            }
        } catch (IOException e) {
            // no interfaces to enumerate: only this PC will be tried
        }
        return new ArrayList<>(hosts);
    }

    /**
     * The hosts of {@code address}'s subnet other than itself. Anything wider than a /24 is
     * narrowed to the /24 around the address (a /16 would be 65k probes); narrower subnets keep
     * their real size.
     */
    static List<String> subnetHosts(InetAddress address, int prefixLength) {
        byte[] octets = address.getAddress();
        int self = ((octets[0] & 0xff) << 24) | ((octets[1] & 0xff) << 16)
                | ((octets[2] & 0xff) << 8) | (octets[3] & 0xff);
        int prefix = Math.max(24, Math.min(prefixLength, 30));
        int mask = prefix == 0 ? 0 : -1 << (32 - prefix);
        int network = self & mask;
        int broadcast = network | ~mask;
        List<String> hosts = new ArrayList<>();
        for (long ip = (network & 0xffffffffL) + 1; ip < (broadcast & 0xffffffffL); ip++) {
            if ((int) ip == self) {
                continue;
            }
            hosts.add(((ip >> 24) & 0xff) + "." + ((ip >> 16) & 0xff) + "." + ((ip >> 8) & 0xff) + "." + (ip & 0xff));
        }
        return hosts;
    }
}
