package com.vanter.ember.hub.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PortableMinioBootstrapTest {

    @Test
    void isPortInUse_falseWhenPortIsFree() throws Exception {
        PortableMinioBootstrap bootstrap =
                new PortableMinioBootstrap(Path.of("unused"), Path.of("unused"), 59998);

        assertThat(bootstrap.isPortInUse(59998)).isFalse();
    }

    @Test
    void isPortInUse_trueWhenSomethingIsAlreadyBoundToIt() throws Exception {
        try (ServerSocket occupied = new ServerSocket(0, 1, InetAddress.getByName("localhost"))) {
            int port = occupied.getLocalPort();
            PortableMinioBootstrap bootstrap =
                    new PortableMinioBootstrap(Path.of("unused"), Path.of("unused"), port);

            assertThat(bootstrap.isPortInUse(port)).isTrue();
        }
    }

    @Test
    void isHealthy_falseWhenNothingListening() {
        PortableMinioBootstrap bootstrap =
                new PortableMinioBootstrap(Path.of("unused"), Path.of("unused"), 59997);

        assertThat(bootstrap.isHealthy()).isFalse();
    }
}
