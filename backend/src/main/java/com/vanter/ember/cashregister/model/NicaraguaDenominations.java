package com.vanter.ember.cashregister.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * The 14 córdoba denominations currently in circulation, per the Banco Central de Nicaragua
 * (bcn.gob.ni/billetes-actuales, bcn.gob.ni/monedas-actuales). The C$10 denomination exists as
 * both a banknote and a coin at the same time — {@code bill_10} and {@code coin_10} are two
 * distinct rows with the same {@link Denomination#value()}, not a duplicate.
 */
public final class NicaraguaDenominations {

    public static final List<Denomination> ALL = List.of(
            new Denomination("bill_1000", new BigDecimal("1000.00"), DenominationKind.BILL),
            new Denomination("bill_500", new BigDecimal("500.00"), DenominationKind.BILL),
            new Denomination("bill_200", new BigDecimal("200.00"), DenominationKind.BILL),
            new Denomination("bill_100", new BigDecimal("100.00"), DenominationKind.BILL),
            new Denomination("bill_50", new BigDecimal("50.00"), DenominationKind.BILL),
            new Denomination("bill_20", new BigDecimal("20.00"), DenominationKind.BILL),
            new Denomination("bill_10", new BigDecimal("10.00"), DenominationKind.BILL),
            new Denomination("coin_10", new BigDecimal("10.00"), DenominationKind.COIN),
            new Denomination("coin_5", new BigDecimal("5.00"), DenominationKind.COIN),
            new Denomination("coin_1", new BigDecimal("1.00"), DenominationKind.COIN),
            new Denomination("coin_050", new BigDecimal("0.50"), DenominationKind.COIN),
            new Denomination("coin_025", new BigDecimal("0.25"), DenominationKind.COIN),
            new Denomination("coin_010", new BigDecimal("0.10"), DenominationKind.COIN),
            new Denomination("coin_005", new BigDecimal("0.05"), DenominationKind.COIN));

    private NicaraguaDenominations() {}

    public static Optional<Denomination> byId(String id) {
        return ALL.stream().filter(d -> d.id().equals(id)).findFirst();
    }
}
