package com.vanter.ember.session.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.vanter.ember.session.dto.TableStatusResponse;
import com.vanter.ember.session.model.LinkedTable;
import com.vanter.ember.session.model.Participant;
import com.vanter.ember.session.model.Session;
import com.vanter.ember.session.model.SessionStatus;
import com.vanter.ember.session.repository.SessionRepository;
import com.vanter.ember.settings.model.DiningTables;
import com.vanter.ember.settings.repository.DiningTableRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID M3 = UUID.randomUUID();
    private static final UUID M4 = UUID.randomUUID();
    private static final UUID M5 = UUID.randomUUID();

    @Mock DiningTableRepository diningTableRepository;
    @Mock SessionRepository sessionRepository;
    @InjectMocks DashboardService dashboardService;

    private static DiningTables table(UUID id, int number) {
        return DiningTables.builder().id(id).restaurantId(TENANT).tableNumber(number).isActive(true).build();
    }

    private static Session family(UUID primary, UUID... linked) {
        List<LinkedTable> links = new ArrayList<>();
        int n = 4;
        for (UUID l : linked) {
            links.add(LinkedTable.builder().tableId(l).tableNumber(n++).linkedAt(LocalDateTime.now()).build());
        }
        return Session.builder().id("s-1").tenantId(TENANT).tableId(primary).waiterId("w@test.com")
                .status(SessionStatus.OPEN).createdAt(LocalDateTime.now())
                .participants(new ArrayList<>(List.of(Participant.builder().name("Ana").build())))
                .linkedTables(links).build();
    }

    private TableStatusResponse row(List<TableStatusResponse> rows, UUID id) {
        return rows.stream().filter(r -> r.tableId().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void getLiveStatus_aLinkedTableIsOccupied_pointsAtThePrimary_andSharesTheSession() {
        when(diningTableRepository.findByRestaurantIdAndIsActiveTrueOrderByTableNumberAsc(TENANT))
                .thenReturn(List.of(table(M3, 3), table(M4, 4), table(M5, 5)));
        when(sessionRepository.findByTenantIdAndStatus(TENANT, SessionStatus.OPEN))
                .thenReturn(List.of(family(M3, M4)));

        List<TableStatusResponse> rows = dashboardService.getLiveStatus(TENANT);

        TableStatusResponse primary = row(rows, M3);
        assertThat(primary.isOccupied()).isTrue();
        assertThat(primary.linkedToTableId()).isNull();
        assertThat(primary.linkedTables()).singleElement().satisfies(l -> {
            assertThat(l.tableId()).isEqualTo(M4);
            assertThat(l.tableNumber()).isEqualTo(4);
        });

        TableStatusResponse linked = row(rows, M4);
        assertThat(linked.isOccupied()).isTrue();
        assertThat(linked.linkedToTableId()).isEqualTo(M3);
        assertThat(linked.linkedToTableNumber()).isEqualTo(3);
        assertThat(linked.currentSession().sessionId()).isEqualTo("s-1");

        TableStatusResponse free = row(rows, M5);
        assertThat(free.isOccupied()).isFalse();
        assertThat(free.currentSession()).isNull();
        assertThat(free.linkedTables()).isEmpty();
    }

    @Test
    void getLiveStatus_anUnmergedSessionHasNoLinkedTables() {
        when(diningTableRepository.findByRestaurantIdAndIsActiveTrueOrderByTableNumberAsc(TENANT))
                .thenReturn(List.of(table(M3, 3)));
        when(sessionRepository.findByTenantIdAndStatus(TENANT, SessionStatus.OPEN))
                .thenReturn(List.of(family(M3)));

        TableStatusResponse primary = dashboardService.getLiveStatus(TENANT).get(0);

        assertThat(primary.isOccupied()).isTrue();
        assertThat(primary.linkedTables()).isEmpty();
    }

    @Test
    void getLiveStatus_whenThePrimaryWasDeactivatedMidSession_theLinkedTableStillShowsOccupiedWithoutANumber() {
        when(diningTableRepository.findByRestaurantIdAndIsActiveTrueOrderByTableNumberAsc(TENANT))
                .thenReturn(List.of(table(M4, 4)));
        when(sessionRepository.findByTenantIdAndStatus(TENANT, SessionStatus.OPEN))
                .thenReturn(List.of(family(M3, M4)));

        TableStatusResponse linked = dashboardService.getLiveStatus(TENANT).get(0);

        assertThat(linked.isOccupied()).isTrue();
        assertThat(linked.linkedToTableId()).isEqualTo(M3);
        assertThat(linked.linkedToTableNumber()).isNull();
    }

    @Test
    void getLiveStatus_aClosedSessionNoLongerOccupiesItsLinkedTables() {
        when(diningTableRepository.findByRestaurantIdAndIsActiveTrueOrderByTableNumberAsc(TENANT))
                .thenReturn(List.of(table(M3, 3), table(M4, 4)));
        when(sessionRepository.findByTenantIdAndStatus(TENANT, SessionStatus.OPEN)).thenReturn(List.of());

        assertThat(dashboardService.getLiveStatus(TENANT)).allSatisfy(r -> assertThat(r.isOccupied()).isFalse());
    }
}
