package com.vanter.ember.hub.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PortableDatabaseBootstrapTest {

    @Test
    void isPortInUse_falseWhenPortIsFree() throws Exception {
        PortableDatabaseBootstrap bootstrap =
                new PortableDatabaseBootstrap(Path.of("unused"), Path.of("unused"), 59999);

        assertThat(bootstrap.isPortInUse(59999)).isFalse();
    }

    @Test
    void isPortInUse_trueWhenSomethingIsAlreadyBoundToIt() throws Exception {
        try (ServerSocket occupied = new ServerSocket(0, 1, InetAddress.getByName("localhost"))) {
            int port = occupied.getLocalPort();
            PortableDatabaseBootstrap bootstrap =
                    new PortableDatabaseBootstrap(Path.of("unused"), Path.of("unused"), port);

            assertThat(bootstrap.isPortInUse(port)).isTrue();
        }
    }
}
