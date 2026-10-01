package com.vanter.ember.session.service;

import com.vanter.ember.session.model.LinkedTable;
import com.vanter.ember.session.model.Session;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Single definition of "occupied": a table is occupied by an OPEN session when it is that
 * session's primary table or one of its linked tables. Callers pass OPEN sessions only.
 */
public final class TableOccupancy {

    private TableOccupancy() {
    }

    public static Map<UUID, Session> byTable(Collection<Session> openSessions) {
        Map<UUID, Session> byTable = new HashMap<>();
        for (Session session : openSessions) {
            byTable.put(session.getTableId(), session);
        }
        for (Session session : openSessions) {
            List<LinkedTable> linked = session.getLinkedTables();
            if (linked == null) {
                continue;
            }
            for (LinkedTable table : linked) {
                byTable.putIfAbsent(table.getTableId(), session);
            }
        }
        return byTable;
    }
}
