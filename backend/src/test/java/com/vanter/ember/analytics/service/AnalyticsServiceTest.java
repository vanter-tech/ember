package com.vanter.ember.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vanter.ember.analytics.dto.AnalyticsSalesResponse;
import com.vanter.ember.analytics.dto.AnalyticsSummaryResponse;
import com.vanter.ember.analytics.dto.SalesGranularity;
import com.vanter.ember.billing.repository.BillDailyOrders;
import com.vanter.ember.billing.repository.BillRepository;
import com.vanter.ember.billing.repository.BillSalesTotals;
import com.vanter.ember.billing.repository.PaymentDailyRevenue;
import com.vanter.ember.billing.repository.PaymentRepository;
import com.vanter.ember.session.model.SessionStatus;
import com.vanter.ember.session.repository.SessionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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

    private static PaymentDailyRevenue revenueOn(int month, int day, String amount) {
        return new PaymentDailyRevenue(2026, month, day, new BigDecimal(amount));
    }

    private static BillDailyOrders ordersOn(int month, int day, long count) {
        return new BillDailyOrders(2026, month, day, count);
    }

    private void stubSales(List<PaymentDailyRevenue> revenue, List<BillDailyOrders> orders) {
        when(paymentRepository.findConfirmedRevenueByDay(eq(TENANT_ID), any(), any())).thenReturn(revenue);
        when(billRepository.findPaidBillsByDay(eq(TENANT_ID), any(), any())).thenReturn(orders);
    }

    @Test
    void getSales_bucketsByDayAndZeroFillsQuietDays() {
        stubSales(
                List.of(revenueOn(8, 1, "100.00"), revenueOn(8, 3, "50.50")),
                List.of(ordersOn(8, 1, 2L), ordersOn(8, 3, 1L)));

        AnalyticsSalesResponse sales = analyticsService.getSales(
                TENANT_ID, "day", FROM, LocalDateTime.of(2026, 8, 3, 23, 59, 59));

        assertThat(sales.granularity()).isEqualTo(SalesGranularity.DAY);
        assertThat(sales.buckets()).hasSize(3);
        assertThat(sales.buckets().get(0).bucketStart()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(sales.buckets().get(0).bucketEnd()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(sales.buckets().get(0).revenue()).isEqualByComparingTo("100.00");
        assertThat(sales.buckets().get(1).revenue()).isEqualByComparingTo("0.00");
        assertThat(sales.buckets().get(1).paidBillCount()).isZero();
        assertThat(sales.buckets().get(2).revenue()).isEqualByComparingTo("50.50");
        assertThat(sales.totalRevenue()).isEqualByComparingTo("150.50");
        assertThat(sales.paidBillCount()).isEqualTo(3L);
    }

    @Test
    void getSales_rollsDaysUpIntoIsoWeeksStartingMonday() {
        stubSales(
                List.of(revenueOn(8, 3, "10.00"), revenueOn(8, 5, "5.00"), revenueOn(8, 10, "20.00")),
                List.of(ordersOn(8, 5, 4L)));

        AnalyticsSalesResponse sales = analyticsService.getSales(
                TENANT_ID,
                "week",
                LocalDateTime.of(2026, 8, 3, 0, 0),
                LocalDateTime.of(2026, 8, 12, 0, 0));

        assertThat(sales.buckets()).hasSize(2);
        assertThat(sales.buckets().get(0).bucketStart()).isEqualTo(LocalDate.of(2026, 8, 3));
        assertThat(sales.buckets().get(0).bucketEnd()).isEqualTo(LocalDate.of(2026, 8, 9));
        assertThat(sales.buckets().get(0).revenue()).isEqualByComparingTo("15.00");
        assertThat(sales.buckets().get(0).paidBillCount()).isEqualTo(4L);
        assertThat(sales.buckets().get(1).bucketStart()).isEqualTo(LocalDate.of(2026, 8, 10));
        assertThat(sales.buckets().get(1).revenue()).isEqualByComparingTo("20.00");
    }

    @Test
    void getSales_rollsDaysUpIntoCalendarMonths() {
        stubSales(
                List.of(revenueOn(7, 9, "30.00"), revenueOn(8, 1, "10.00"), revenueOn(8, 31, "60.00")),
                List.of());

        AnalyticsSalesResponse sales = analyticsService.getSales(
                TENANT_ID,
                "month",
                LocalDateTime.of(2026, 7, 1, 0, 0),
                LocalDateTime.of(2026, 8, 31, 23, 59, 59));

        assertThat(sales.buckets()).hasSize(2);
        assertThat(sales.buckets().get(0).bucketStart()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(sales.buckets().get(0).bucketEnd()).isEqualTo(LocalDate.of(2026, 7, 31));
        assertThat(sales.buckets().get(0).revenue()).isEqualByComparingTo("30.00");
        assertThat(sales.buckets().get(1).bucketStart()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(sales.buckets().get(1).revenue()).isEqualByComparingTo("70.00");
    }

    @Test
    void getSales_rollsDaysUpIntoCalendarYears() {
        stubSales(List.of(revenueOn(8, 1, "10.00"), revenueOn(12, 31, "90.00")), List.of());

        AnalyticsSalesResponse sales = analyticsService.getSales(
                TENANT_ID,
                "YEAR",
                LocalDateTime.of(2026, 1, 1, 0, 0),
                LocalDateTime.of(2026, 12, 31, 23, 59, 59));

        assertThat(sales.buckets()).singleElement().satisfies(bucket -> {
            assertThat(bucket.bucketStart()).isEqualTo(LocalDate.of(2026, 1, 1));
            assertThat(bucket.bucketEnd()).isEqualTo(LocalDate.of(2026, 12, 31));
            assertThat(bucket.revenue()).isEqualByComparingTo("100.00");
        });
    }

    @Test
    void getSales_anExplicitFromLeadsTheSeriesEvenWhenThoseBucketsAreEmpty() {
        stubSales(List.of(revenueOn(8, 3, "10.00")), List.of());

        AnalyticsSalesResponse sales = analyticsService.getSales(
                TENANT_ID, "day", FROM, LocalDateTime.of(2026, 8, 3, 12, 0));

        assertThat(sales.buckets()).hasSize(3);
        assertThat(sales.buckets().get(0).bucketStart()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(sales.buckets().get(0).revenue()).isEqualByComparingTo("0.00");
    }

    @Test
    void getSales_withoutFromStartsAtTheFirstActiveBucketRatherThanTheEpoch() {
        stubSales(List.of(), List.of(ordersOn(8, 12, 2L)));

        AnalyticsSalesResponse sales =
                analyticsService.getSales(TENANT_ID, "day", null, LocalDateTime.of(2026, 8, 14, 23, 0));

        assertThat(sales.from()).isEqualTo(AnalyticsService.EPOCH_FLOOR);
        assertThat(sales.buckets()).hasSize(3);
        assertThat(sales.buckets().get(0).bucketStart()).isEqualTo(LocalDate.of(2026, 8, 12));
        assertThat(sales.buckets().get(0).paidBillCount()).isEqualTo(2L);
        assertThat(sales.buckets().get(2).bucketStart()).isEqualTo(LocalDate.of(2026, 8, 14));
    }

    @Test
    void getSales_withNoBillingHistoryAndNoFromReturnsAnEmptySeries() {
        stubSales(List.of(), List.of());

        AnalyticsSalesResponse sales = analyticsService.getSales(TENANT_ID, "day", null, TO);

        assertThat(sales.buckets()).isEmpty();
        assertThat(sales.totalRevenue()).isEqualByComparingTo("0.00");
        assertThat(sales.paidBillCount()).isZero();
    }

    @Test
    void getSales_defaultsToDailyGranularityWhenNoneIsSent() {
        stubSales(List.of(revenueOn(8, 1, "10.00")), List.of());

        assertThat(analyticsService.getSales(TENANT_ID, null, FROM, TO).granularity())
                .isEqualTo(SalesGranularity.DAY);
    }

    @Test
    void getSales_rejectsAnUnknownGranularityInsteadOfSilentlyFallingBack() {
        assertThatThrownBy(() -> analyticsService.getSales(TENANT_ID, "fortnight", FROM, TO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fortnight");

        verify(paymentRepository, never()).findConfirmedRevenueByDay(any(), any(), any());
        verify(billRepository, never()).findPaidBillsByDay(any(), any(), any());
    }

    @Test
    void getSales_rejectsAnInvertedWindow() {
        assertThatThrownBy(() -> analyticsService.getSales(TENANT_ID, "day", TO, FROM))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be after");

        verify(paymentRepository, never()).findConfirmedRevenueByDay(any(), any(), any());
        verify(billRepository, never()).findPaidBillsByDay(any(), any(), any());
    }

    @Test
    void getSales_toleratesNullAggregatesInADayRow() {
        stubSales(List.of(new PaymentDailyRevenue(2026, 8, 1, null)), List.of(new BillDailyOrders(2026, 8, 1, null)));

        AnalyticsSalesResponse sales =
                analyticsService.getSales(TENANT_ID, "day", FROM, LocalDateTime.of(2026, 8, 1, 23, 0));

        assertThat(sales.buckets()).singleElement().satisfies(bucket -> {
            assertThat(bucket.revenue()).isEqualByComparingTo("0.00");
            assertThat(bucket.paidBillCount()).isZero();
        });
    }
}
