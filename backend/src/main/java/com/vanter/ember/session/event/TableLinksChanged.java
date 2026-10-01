package com.vanter.ember.session.event;

import java.util.List;
import java.util.UUID;

/**
 * A table was attached to or detached from a session. Always carries the session's FULL current
 * list of linked table numbers, so listeners overwrite their copy instead of applying a delta.
 */
public record TableLinksChanged(
        String type, UUID tenantId, String sessionId, UUID tableId, List<Integer> linkedTableNumbers) {

    public static TableLinksChanged linked(
            UUID tenantId, String sessionId, UUID tableId, List<Integer> linkedTableNumbers) {
        return new TableLinksChanged("TABLES_LINKED", tenantId, sessionId, tableId, List.copyOf(linkedTableNumbers));
    }

    public static TableLinksChanged unlinked(
            UUID tenantId, String sessionId, UUID tableId, List<Integer> linkedTableNumbers) {
        return new TableLinksChanged("TABLE_UNLINKED", tenantId, sessionId, tableId, List.copyOf(linkedTableNumbers));
    }
}
