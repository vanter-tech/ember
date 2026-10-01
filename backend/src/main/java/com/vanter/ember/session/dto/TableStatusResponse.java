package com.vanter.ember.session.dto;

import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record TableStatusResponse(
        UUID tableId,
        int tableNumber,
        boolean isOccupied,
        ActiveSessionSummary currentSession,
        /** Tables attached to this table's session; empty when it is not a merged primary. */
        List<LinkedTableSummary> linkedTables,
        /** Set only on a table attached to ANOTHER table's session: that primary table. */
        UUID linkedToTableId,
        Integer linkedToTableNumber
) {

    public TableStatusResponse {
        if (linkedTables == null) {
            linkedTables = List.of();
        }
    }
}
