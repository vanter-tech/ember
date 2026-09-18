package com.vanter.ember.cashregister.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class NicaraguaDenominationsTest {

    @Test
    void all_hasFourteenEntriesCoveringBillsAndCoins() {
        assertThat(NicaraguaDenominations.ALL).hasSize(14);
        assertThat(NicaraguaDenominations.ALL.stream().filter(d -> d.kind() == DenominationKind.BILL))
                .hasSize(7);
        assertThat(NicaraguaDenominations.ALL.stream().filter(d -> d.kind() == DenominationKind.COIN))
                .hasSize(7);
    }

    @Test
    void all_idsAreUnique() {
        long distinctIds = NicaraguaDenominations.ALL.stream().map(Denomination::id).distinct().count();
        assertThat(distinctIds).isEqualTo(14);
    }

    @Test
    void byId_findsTheTenCordobaBillAndTheTenCordobaCoinAsDistinctEntries() {
        assertThat(NicaraguaDenominations.byId("bill_10")).isPresent();
        assertThat(NicaraguaDenominations.byId("coin_10")).isPresent();
        assertThat(NicaraguaDenominations.byId("bill_10").get().value())
                .isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(NicaraguaDenominations.byId("coin_10").get().value())
                .isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(NicaraguaDenominations.byId("bill_10").get().kind()).isEqualTo(DenominationKind.BILL);
        assertThat(NicaraguaDenominations.byId("coin_10").get().kind()).isEqualTo(DenominationKind.COIN);
    }

    @Test
    void byId_returnsEmptyForAnUnknownId() {
        assertThat(NicaraguaDenominations.byId("bill_777")).isEmpty();
    }
}
