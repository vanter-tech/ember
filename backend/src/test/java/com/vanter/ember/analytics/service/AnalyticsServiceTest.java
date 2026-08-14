package com.vanter.ember.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vanter.ember.analytics.dto.AnalyticsSummaryResponse;
import com.vanter.ember.billing.repository.BillRepository;
import com.vanter.ember.billing.repository.BillSalesTotals;
import com.vanter.ember.billing.repository.PaymentRepository;
import com.vanter.ember.session.model.SessionStatus;
import com.vanter.ember.session.repository.SessionRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final LocalDateTime FROM = LocalDateTime.of(2026, 8, 1, 0, 0);
    private static final LocalDateTime TO = LocalDateTime.of(2026, 8, 14, 23, 59, 59);

    @Mock BillRepository billRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock SessionRepository sessionRepository;

    @InjectMocks AnalyticsService analyticsService;

    private void stub(BigDecimal revenue, BillSalesTotals sales, long activeSessions) {
        when(paymentRepository.sumConfirmedRevenue(eq(TENANT_ID), any(), any())).thenReturn(revenue);
        when(billRepository.findSalesTotals(eq(TENANT_ID), any(), any())).thenReturn(sales);
        when(sessionRepository.countByTenantIdAndStatus(TENANT_ID, SessionStatus.OPEN))
                .thenReturn(activeSessions);
    }

    @Test
    void getSummary_dividesSalesByPaidBillsForTheAverageOrderValue() {
        stub(new BigDecimal("1520.50"), new BillSalesTotals(4L, new BigDecimal("169.00")), 3L);

        AnalyticsSummaryResponse summary = analyticsService.getSummary(TENANT_ID, FROM, TO);

        assertThat(summary.totalRevenue()).isEqualByComparingTo("1520.50");
        assertThat(summary.averageOrderValue()).isEqualByComparingTo("42.25");
        assertThat(summary.paidBillCount()).isEqualTo(4L);
        assertThat(summary.activeSessions()).isEqualTo(3L);
        assertThat(summary.from()).isEqualTo(FROM);
        assertThat(summary.to()).isEqualTo(TO);
    }

    @Test
    void getSummary_roundsTheAverageOrderValueToTwoDecimals() {
        stub(new BigDecimal("100.00"), new BillSalesTotals(3L, new BigDecimal("100.00")), 0L);

        assertThat(analyticsService.getSummary(TENANT_ID, FROM, TO).averageOrderValue())
                .isEqualByComparingTo("33.33");
    }

    @Test
    void getSummary_emptyWindowReportsZerosRatherThanNulls() {
        stub(null, new BillSalesTotals(0L, null), 0L);

        AnalyticsSummaryResponse summary = analyticsService.getSummary(TENANT_ID, FROM, TO);

        assertThat(summary.totalRevenue()).isEqualByComparingTo("0.00");
        assertThat(summary.averageOrderValue()).isEqualByComparingTo("0.00");
        assertThat(summary.paidBillCount()).isZero();
    }

    @Test
    void getSummary_toleratesANullAggregateRow() {
        stub(null, null, 0L);

        AnalyticsSummaryResponse summary = analyticsService.getSummary(TENANT_ID, FROM, TO);

        assertThat(summary.averageOrderValue()).isEqualByComparingTo("0.00");
        assertThat(summary.paidBillCount()).isZero();
    }

    @Test
    void getSummary_missingBoundsDefaultToTheWholeHistoryUpToNow() {
        stub(new BigDecimal("10.00"), new BillSalesTotals(1L, new BigDecimal("10.00")), 0L);
        LocalDateTime beforeCall = LocalDateTime.now();

        AnalyticsSummaryResponse summary = analyticsService.getSummary(TENANT_ID, null, null);

        ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> to = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(paymentRepository).sumConfirmedRevenue(eq(TENANT_ID), from.capture(), to.capture());

        assertThat(from.getValue()).isEqualTo(AnalyticsService.EPOCH_FLOOR);
        assertThat(to.getValue()).isAfterOrEqualTo(beforeCall);
        assertThat(summary.from()).isEqualTo(AnalyticsService.EPOCH_FLOOR);
        assertThat(summary.to()).isEqualTo(to.getValue());
    }

    @Test
    void getSummary_activeSessionCountIgnoresTheWindow() {
        stub(new BigDecimal("10.00"), new BillSalesTotals(1L, new BigDecimal("10.00")), 7L);

        assertThat(analyticsService.getSummary(TENANT_ID, FROM, TO).activeSessions()).isEqualTo(7L);

        verify(sessionRepository).countByTenantIdAndStatus(TENANT_ID, SessionStatus.OPEN);
    }

    @Test
    void getSummary_rejectsAnInvertedWindowInsteadOfReportingNoSales() {
        assertThatThrownBy(() -> analyticsService.getSummary(TENANT_ID, TO, FROM))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be after");

        verify(paymentRepository, never()).sumConfirmedRevenue(any(), any(), any());
        verify(billRepository, never()).findSalesTotals(any(), any(), any());
    }
}
