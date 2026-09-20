package com.vanter.ember.hub.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class PostgresToolsTest {

    /**
     * Without -w, pg_dump/pg_restore/dropdb/createdb prompt for a password on the console when the
     * port is answered by a password-protected Postgres (e.g. a dev Docker one) and never return —
     * the Hub's backup button stayed disabled forever.
     */
    @Test
    void everyCommandIsNonInteractive() {
        PostgresTools tools = new PostgresTools(Path.of("bin"), 5432);

        List<String> cmd = tools.buildCommand("pg_dump", "-Fc", "-U", "ember");

        assertThat(cmd.get(0)).endsWith("pg_dump");
        assertThat(cmd).contains("-w");
    }

    @Test
    void aToolThatNeverExitsIsKilledAfterItsTimeout() {
        assumeTrue(System.getProperty("os.name").toLowerCase().contains("win"));

        assertThatThrownBy(() -> new PostgresTools(Path.of("bin"), 5432)
                .execute(List.of("ping", "-n", "30", "127.0.0.1"), "ping", Duration.ofMillis(500)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("no terminó");
    }
}
