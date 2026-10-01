package com.vanter.ember.session.service;

import com.vanter.ember.session.dto.ActiveSessionSummary;
import com.vanter.ember.session.dto.LinkedTableSummary;
import com.vanter.ember.session.dto.TableStatusResponse;
import com.vanter.ember.session.model.Session;
import com.vanter.ember.session.model.SessionStatus;
import com.vanter.ember.session.repository.SessionRepository;
import com.vanter.ember.settings.model.DiningTables;
import com.vanter.ember.settings.repository.DiningTableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class DashboardService {

    private final DiningTableRepository diningTableRepository;
    private final SessionRepository sessionRepository;

    public List<TableStatusResponse> getLiveStatus(UUID restaurantId) {
        var tables = diningTableRepository.findByRestaurantIdAndIsActiveTrueOrderByTableNumberAsc(restaurantId);

        Map<UUID, DiningTables> activeById = tables.stream()
                .collect(Collectors.toMap(DiningTables::getId, t -> t));
        Map<UUID, Session> occupancy = TableOccupancy.byTable(
                sessionRepository.findByTenantIdAndStatus(restaurantId, SessionStatus.OPEN));

        return tables.stream().map(table -> {
            Session session = occupancy.get(table.getId());
            if (session == null) {
                return TableStatusResponse.builder()
                        .tableId(table.getId())
                        .tableNumber(table.getTableNumber())
                        .isOccupied(false)
                        .build();
            }
            TableStatusResponse.TableStatusResponseBuilder row = TableStatusResponse.builder()
                    .tableId(table.getId())
                    .tableNumber(table.getTableNumber())
                    .isOccupied(true)
                    .currentSession(new ActiveSessionSummary(
                            session.getId(),
                            session.getWaiterId(),
                            session.getParticipants().size(),
                            session.getCreatedAt()));
            if (session.getTableId().equals(table.getId())) {
                row.linkedTables(session.getLinkedTables() == null ? List.of() : session.getLinkedTables().stream()
                        .map(l -> new LinkedTableSummary(l.getTableId(), l.getTableNumber()))
                        .toList());
            } else {
                DiningTables primary = activeById.get(session.getTableId());
                row.linkedToTableId(session.getTableId())
                        .linkedToTableNumber(primary == null ? null : primary.getTableNumber());
            }
            return row.build();
        }).toList();
    }
}
