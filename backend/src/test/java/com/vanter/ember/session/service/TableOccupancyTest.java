package com.vanter.ember.session.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanter.ember.session.model.LinkedTable;
import com.vanter.ember.session.model.Session;
import com.vanter.ember.session.model.SessionStatus;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TableOccupancyTest {

    private static final UUID M3 = UUID.randomUUID();
    private static final UUID M4 = UUID.randomUUID();
    private static final UUID M7 = UUID.randomUUID();

    private static Session session(String id, UUID primary, UUID... linked) {
        List<LinkedTable> links = new ArrayList<>();
        int n = 10;
        for (UUID l : linked) {
            links.add(LinkedTable.builder().tableId(l).tableNumber(n++).linkedAt(LocalDateTime.now()).build());
        }
        return Session.builder().id(id).tableId(primary).status(SessionStatus.OPEN).linkedTables(links).build();
    }

    @Test
    void byTable_mapsPrimaryAndLinkedTablesToTheirSession() {
        Session family = session("s-1", M3, M4);
        Session other = session("s-2", M7);

        Map<UUID, Session> map = TableOccupancy.byTable(List.of(family, other));

        assertThat(map.get(M3)).isSameAs(family);
        assertThat(map.get(M4)).isSameAs(family);
        assertThat(map.get(M7)).isSameAs(other);
        assertThat(map).hasSize(3);
    }

    @Test
    void byTable_primaryWinsIfLegacyDataClaimsTheSameTableTwice() {
        Session a = session("s-1", M3);
        Session b = session("s-2", M7, M3);

        assertThat(TableOccupancy.byTable(List.of(b, a)).get(M3)).isSameAs(a);
    }

    @Test
    void byTable_toleratesASessionWhoseLinkedTablesIsNull() {
        Session legacy = Session.builder().id("s-1").tableId(M3).status(SessionStatus.OPEN).build();
        legacy.setLinkedTables(null);

        assertThat(TableOccupancy.byTable(List.of(legacy))).containsOnlyKeys(M3);
        assertThat(legacy.linkedTableNumbers()).isEmpty();
    }

    @Test
    void byTable_emptyInputIsEmpty() {
        assertThat(TableOccupancy.byTable(List.of())).isEmpty();
    }
}
