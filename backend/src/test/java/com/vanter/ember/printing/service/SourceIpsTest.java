package com.vanter.ember.printing.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SourceIpsTest {

    @Test
    void everyLoopbackSpelling_collapsesToTheLocalKey() {
        assertThat(SourceIps.normalize("127.0.0.1")).isEqualTo(SourceIps.LOCAL);
        assertThat(SourceIps.normalize("::1")).isEqualTo(SourceIps.LOCAL);
        assertThat(SourceIps.normalize("0:0:0:0:0:0:0:1")).isEqualTo(SourceIps.LOCAL);
    }

    @Test
    void aRemoteAddress_isKeptAsIs_andIpv4MappedIpv6MatchesPlainIpv4() {
        assertThat(SourceIps.normalize("203.0.113.21")).isEqualTo("203.0.113.21");
        assertThat(SourceIps.normalize(" 203.0.113.21 ")).isEqualTo("203.0.113.21");
        assertThat(SourceIps.normalize("::ffff:203.0.113.21")).isEqualTo("203.0.113.21");
    }

    @Test
    void blankOrNull_hasNoKey() {
        assertThat(SourceIps.normalize(null)).isNull();
        assertThat(SourceIps.normalize("  ")).isNull();
    }
}
