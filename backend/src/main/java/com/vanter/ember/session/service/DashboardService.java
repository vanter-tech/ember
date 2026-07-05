package com.vanter.ember.session.service;

import com.vanter.ember.session.dto.ActiveSessionSummary;
import com.vanter.ember.session.dto.TableStatusResponse;
import com.vanter.ember.session.model.Session;
import com.vanter.ember.session.model.SessionStatus;
import com.vanter.ember.session.repository.SessionRepository;
import com.vanter.ember.settings.model.DiningTables;
import com.vanter.ember.settings.repository.DiningTableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class DashboardService {

    private final DiningTableRepository diningTableRepository;
    private final SessionRepository sessionRepository;

    public List<TableStatusResponse> getLiveStatus(UUID restaurantId) {
        var tables = diningTableRepository.findByRestaurantIdAndIsActiveTrueOrderByTableNumberAsc(restaurantId);

        var tablesId = tables.stream().map(DiningTables::getId).toList();
        var activeSession = sessionRepository.findByTableIdInAndStatus(tablesId, SessionStatus.OPEN);

        var sessionMap = activeSession.stream().collect(
                Collectors.toMap(
                        Session::getTableId,
                        session ->  session
                )
        );

        return tables.stream().map(
                (table) -> {
                    var currentSession = sessionMap.get(table.getId());
                    if(currentSession != null) {
                        return TableStatusResponse.builder()
                                .tableId(table.getId())
                                .tableNumber(table.getTableNumber())
                                .isOccupied(true)
                                .currentSession( new ActiveSessionSummary(
                                        currentSession.getId(),
                                        currentSession.getWaiterId(),
                                        currentSession.getParticipants().size(),
                                        currentSession.getCreatedAt()
                                )
                        ).build();
                    }else {
                        return TableStatusResponse.builder()
                                .tableId(table.getId())
                                .tableNumber(table.getTableNumber())
                                .isOccupied(false)
                                .build();
                    }
                }
        ).toList();

    }
}
