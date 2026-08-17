package com.vanter.ember.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vanter.ember.analytics.dto.AnalyticsProductsResponse;
import com.vanter.ember.analytics.dto.AnalyticsSalesResponse;
import com.vanter.ember.analytics.dto.AnalyticsSummaryResponse;
import com.vanter.ember.analytics.dto.AnalyticsTablesResponse;
import com.vanter.ember.analytics.dto.ProductPerformance;
import com.vanter.ember.analytics.dto.SalesGranularity;
import com.vanter.ember.analytics.dto.TablePerformance;
import com.vanter.ember.billing.repository.BillDailyOrders;
import com.vanter.ember.billing.repository.BillRepository;
import com.vanter.ember.billing.repository.BillSalesTotals;
import com.vanter.ember.billing.repository.PaidBillActivity;
import com.vanter.ember.billing.repository.PaymentDailyRevenue;
import com.vanter.ember.billing.repository.PaymentRepository;
import com.vanter.ember.billing.repository.RefundDailyAmount;
import com.vanter.ember.billing.repository.RefundRepository;
import com.vanter.ember.catalog.model.Category;
import com.vanter.ember.catalog.model.MenuItem;
import com.vanter.ember.catalog.repository.MenuItemRepository;
import com.vanter.ember.session.model.OrderItem;
import com.vanter.ember.session.model.OrderItemStatus;
import com.vanter.ember.session.model.Session;
import com.vanter.ember.session.model.SessionStatus;
import com.vanter.ember.session.repository.SessionRepository;
import com.vanter.ember.settings.model.DiningTables;
import com.vanter.ember.settings.repository.DiningTableRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
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
    @Mock MenuItemRepository menuItemRepository;
    @Mock DiningTableRepository diningTableRepository;
    @Mock RefundRepository refundRepository;

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

    @Test
    void getSummary_netsOutRefundsIssuedInTheWindow() {
        when(paymentRepository.sumConfirmedRevenue(eq(TENANT_ID), any(), any()))
                .thenReturn(new BigDecimal("100.00"));
        when(refundRepository.sumRefundsInWindow(eq(TENANT_ID), any(), any()))
                .thenReturn(new BigDecimal("15.00"));
        when(billRepository.findSalesTotals(eq(TENANT_ID), any(), any())).thenReturn(null);
        when(sessionRepository.countByTenantIdAndStatus(any(), any())).thenReturn(0L);

        AnalyticsSummaryResponse summary = analyticsService.getSummary(TENANT_ID, FROM, TO);

        assertThat(summary.totalRevenue()).isEqualByComparingTo("85.00");
    }

    @Test
    void getSales_netsOutRefundsPerBucket() {
        when(paymentRepository.findConfirmedRevenueByDay(eq(TENANT_ID), any(), any()))
                .thenReturn(List.of(new PaymentDailyRevenue(2026, 8, 17, new BigDecimal("100.00"))));
        when(refundRepository.findRefundsByDay(eq(TENANT_ID), any(), any()))
                .thenReturn(List.of(new RefundDailyAmount(2026, 8, 17, new BigDecimal("15.00"))));
        when(billRepository.findPaidBillsByDay(eq(TENANT_ID), any(), any())).thenReturn(List.of());

        AnalyticsSalesResponse sales = analyticsService.getSales(
                TENANT_ID, "day", LocalDateTime.of(2026, 8, 17, 0, 0), LocalDateTime.of(2026, 8, 17, 23, 59));

        assertThat(sales.totalRevenue()).isEqualByComparingTo("85.00");
        assertThat(sales.buckets()).singleElement()
                .satisfies(bucket -> assertThat(bucket.revenue()).isEqualByComparingTo("85.00"));
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

    private static OrderItem soldItem(Long itemId, String name, String price, OrderItemStatus status) {
        return OrderItem.builder()
                .id(UUID.randomUUID().toString())
                .itemId(itemId)
                .name(name)
                .price(price == null ? null : new BigDecimal(price))
                .status(status)
                .build();
    }

    private static Session sessionWith(OrderItem... items) {
        return Session.builder()
                .id(UUID.randomUUID().toString())
                .tenantId(TENANT_ID)
                .items(new ArrayList<>(List.of(items)))
                .build();
    }

    private static MenuItem catalogItem(Long id, String name, Long categoryId, String categoryName) {
        return MenuItem.builder()
                .id(id)
                .name(name)
                .category(Category.builder().id(categoryId).name(categoryName).build())
                .build();
    }

    private void stubProducts(List<String> sessionIds, List<Session> sessions, List<MenuItem> catalog) {
        when(billRepository.findPaidSessionIds(eq(TENANT_ID), any(), any())).thenReturn(sessionIds);
        if (!sessionIds.isEmpty()) {
            when(sessionRepository.findByTenantIdAndIdIn(eq(TENANT_ID), any())).thenReturn(sessions);
        }
        if (!catalog.isEmpty()) {
            when(menuItemRepository.findByTenantIdAndIdInWithCategory(eq(TENANT_ID), any()))
                    .thenReturn(catalog);
        }
    }

    /** Three 50.00 mains and three 10.00 drinks: 180.00 of item revenue over six line items. */
    private void stubTwoProductsSold() {
        stubProducts(
                List.of("s-1", "s-2"),
                List.of(
                        sessionWith(
                                soldItem(4L, "Lomo", "50.00", OrderItemStatus.DELIVERED),
                                soldItem(4L, "Lomo", "50.00", OrderItemStatus.PENDING),
                                soldItem(9L, "Chicha", "10.00", OrderItemStatus.DELIVERED)),
                        sessionWith(
                                soldItem(4L, "Lomo", "50.00", OrderItemStatus.READY),
                                soldItem(9L, "Chicha", "10.00", OrderItemStatus.DELIVERED),
                                soldItem(9L, "Chicha", "10.00", OrderItemStatus.PREPARING))),
                List.of(
                        catalogItem(4L, "Lomo saltado", 2L, "Fondos"),
                        catalogItem(9L, "Chicha morada", 3L, "Bebidas")));
    }

    @Test
    void getProducts_ranksItemsByRevenueWithParetoShares() {
        stubTwoProductsSold();

        AnalyticsProductsResponse products = analyticsService.getProducts(TENANT_ID, FROM, TO, null);

        assertThat(products.totalRevenue()).isEqualByComparingTo("180.00");
        assertThat(products.totalQuantity()).isEqualTo(6L);
        assertThat(products.productCount()).isEqualTo(2);
        assertThat(products.from()).isEqualTo(FROM);
        assertThat(products.to()).isEqualTo(TO);

        ProductPerformance best = products.products().get(0);
        assertThat(best.itemId()).isEqualTo(4L);
        assertThat(best.name()).isEqualTo("Lomo saltado");
        assertThat(best.categoryId()).isEqualTo(2L);
        assertThat(best.categoryName()).isEqualTo("Fondos");
        assertThat(best.quantitySold()).isEqualTo(3L);
        assertThat(best.revenue()).isEqualByComparingTo("150.00");
        assertThat(best.revenueShare()).isEqualByComparingTo("83.33");
        assertThat(best.cumulativeShare()).isEqualByComparingTo("83.33");

        ProductPerformance second = products.products().get(1);
        assertThat(second.name()).isEqualTo("Chicha morada");
        assertThat(second.revenue()).isEqualByComparingTo("30.00");
        assertThat(second.revenueShare()).isEqualByComparingTo("16.67");
        assertThat(second.cumulativeShare()).isEqualByComparingTo("100.00");
    }

    @Test
    void getProducts_rollsTheSameLineItemsUpByCategory() {
        stubTwoProductsSold();

        AnalyticsProductsResponse products = analyticsService.getProducts(TENANT_ID, FROM, TO, null);

        assertThat(products.categories()).hasSize(2);
        assertThat(products.categories().get(0).categoryId()).isEqualTo(2L);
        assertThat(products.categories().get(0).name()).isEqualTo("Fondos");
        assertThat(products.categories().get(0).quantitySold()).isEqualTo(3L);
        assertThat(products.categories().get(0).revenue()).isEqualByComparingTo("150.00");
        assertThat(products.categories().get(0).revenueShare()).isEqualByComparingTo("83.33");
        assertThat(products.categories().get(1).name()).isEqualTo("Bebidas");
        assertThat(products.categories().get(1).revenue()).isEqualByComparingTo("30.00");
    }

    @Test
    void getProducts_limitTrimsTheListButLeavesTotalsAndSharesOverTheFullSet() {
        stubTwoProductsSold();

        AnalyticsProductsResponse products = analyticsService.getProducts(TENANT_ID, FROM, TO, 1);

        assertThat(products.products()).hasSize(1);
        assertThat(products.products().get(0).name()).isEqualTo("Lomo saltado");
        assertThat(products.products().get(0).revenueShare()).isEqualByComparingTo("83.33");
        assertThat(products.productCount()).isEqualTo(2);
        assertThat(products.totalRevenue()).isEqualByComparingTo("180.00");
        assertThat(products.categories()).hasSize(2);
    }

    @Test
    void getProducts_skipsItemsStillInTheCartWhenTheTablePaid() {
        stubProducts(
                List.of("s-1"),
                List.of(sessionWith(
                        soldItem(4L, "Lomo", "50.00", OrderItemStatus.DELIVERED),
                        soldItem(9L, "Chicha", "999.00", OrderItemStatus.DRAFT))),
                List.of(catalogItem(4L, "Lomo saltado", 2L, "Fondos")));

        AnalyticsProductsResponse products = analyticsService.getProducts(TENANT_ID, FROM, TO, null);

        assertThat(products.products()).singleElement().satisfies(product -> {
            assertThat(product.name()).isEqualTo("Lomo saltado");
            assertThat(product.quantitySold()).isEqualTo(1L);
        });
        assertThat(products.totalRevenue()).isEqualByComparingTo("50.00");
    }

    @Test
    void getProducts_readsOnlyTheSessionsWhoseBillsSettledInTheWindow() {
        stubProducts(
                List.of("s-1", "s-2"),
                List.of(sessionWith(soldItem(4L, "Lomo", "50.00", OrderItemStatus.DELIVERED))),
                List.of(catalogItem(4L, "Lomo saltado", 2L, "Fondos")));

        analyticsService.getProducts(TENANT_ID, FROM, TO, null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(sessionRepository).findByTenantIdAndIdIn(eq(TENANT_ID), ids.capture());
        assertThat(ids.getValue()).containsExactly("s-1", "s-2");
    }

    @Test
    void getProducts_withoutASettledBillReportsNothingAndNeverTouchesMongo() {
        when(billRepository.findPaidSessionIds(eq(TENANT_ID), any(), any())).thenReturn(List.of());

        AnalyticsProductsResponse products = analyticsService.getProducts(TENANT_ID, FROM, TO, null);

        assertThat(products.products()).isEmpty();
        assertThat(products.categories()).isEmpty();
        assertThat(products.productCount()).isZero();
        assertThat(products.totalRevenue()).isEqualByComparingTo("0.00");
        assertThat(products.totalQuantity()).isZero();
        verify(sessionRepository, never()).findByTenantIdAndIdIn(any(), any());
    }

    @Test
    void getProducts_keepsTheSoldNameWhenTheMenuItemNoLongerExists() {
        stubProducts(
                List.of("s-1"),
                List.of(sessionWith(
                        soldItem(77L, "Causa limeña", "20.00", OrderItemStatus.DELIVERED),
                        soldItem(null, "Corrección de cuenta", "5.00", OrderItemStatus.DELIVERED))),
                List.of());

        AnalyticsProductsResponse products = analyticsService.getProducts(TENANT_ID, FROM, TO, null);

        assertThat(products.products()).hasSize(2);
        assertThat(products.products().get(0).itemId()).isNull();
        assertThat(products.products().get(0).name()).isEqualTo("Causa limeña");
        assertThat(products.products().get(0).categoryId()).isNull();
        assertThat(products.products().get(0).categoryName()).isNull();
        assertThat(products.products().get(1).name()).isEqualTo("Corrección de cuenta");
        assertThat(products.categories()).singleElement().satisfies(category -> {
            assertThat(category.categoryId()).isNull();
            assertThat(category.revenue()).isEqualByComparingTo("25.00");
        });
    }

    @Test
    void getProducts_toleratesASessionWithNoItemsAndALineItemWithNoPrice() {
        stubProducts(
                List.of("s-1", "s-2"),
                List.of(
                        Session.builder().id("s-1").tenantId(TENANT_ID).items(null).build(),
                        sessionWith(soldItem(4L, "Lomo", null, OrderItemStatus.DELIVERED))),
                List.of(catalogItem(4L, "Lomo saltado", 2L, "Fondos")));

        AnalyticsProductsResponse products = analyticsService.getProducts(TENANT_ID, FROM, TO, null);

        assertThat(products.products()).singleElement().satisfies(product -> {
            assertThat(product.quantitySold()).isEqualTo(1L);
            assertThat(product.revenue()).isEqualByComparingTo("0.00");
            assertThat(product.revenueShare()).isEqualByComparingTo("0.00");
        });
        assertThat(products.totalRevenue()).isEqualByComparingTo("0.00");
    }

    @Test
    void getProducts_rejectsANonPositiveLimit() {
        assertThatThrownBy(() -> analyticsService.getProducts(TENANT_ID, FROM, TO, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");

        verify(billRepository, never()).findPaidSessionIds(any(), any(), any());
    }

    @Test
    void getProducts_rejectsAnInvertedWindow() {
        assertThatThrownBy(() -> analyticsService.getProducts(TENANT_ID, TO, FROM, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be after");

        verify(billRepository, never()).findPaidSessionIds(any(), any(), any());
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

    private static PaidBillActivity paidBill(String sessionId, String total, LocalDateTime createdAt) {
        return new PaidBillActivity(sessionId, new BigDecimal(total), createdAt);
    }

    private static Session sessionAt(String id, UUID tableId, LocalDateTime createdAt) {
        return Session.builder().id(id).tenantId(TENANT_ID).tableId(tableId).createdAt(createdAt).build();
    }

    @Test
    void getTables_attributesRevenueAndTurnoverPerTableOrderedByRevenue() {
        UUID tableA = UUID.randomUUID();
        UUID tableB = UUID.randomUUID();
        when(billRepository.findPaidBillActivity(eq(TENANT_ID), any(), any())).thenReturn(List.of(
                paidBill("s-1", "100.00", LocalDateTime.of(2026, 8, 2, 20, 30)),
                paidBill("s-2", "40.00", LocalDateTime.of(2026, 8, 3, 21, 0))));
        when(sessionRepository.findByTenantIdAndIdIn(eq(TENANT_ID), any())).thenReturn(List.of(
                sessionAt("s-1", tableA, LocalDateTime.of(2026, 8, 2, 20, 0)),
                sessionAt("s-2", tableB, LocalDateTime.of(2026, 8, 3, 20, 45))));
        when(diningTableRepository.findByRestaurantIdAndIdIn(eq(TENANT_ID), any())).thenReturn(List.of(
                DiningTables.builder().id(tableA).tableNumber(1).build(),
                DiningTables.builder().id(tableB).tableNumber(2).build()));
        when(diningTableRepository.countByRestaurantIdAndIsActiveTrue(TENANT_ID)).thenReturn(5L);

        AnalyticsTablesResponse response = analyticsService.getTables(TENANT_ID, FROM, TO);

        assertThat(response.totalRevenue()).isEqualByComparingTo("140.00");
        assertThat(response.totalTurnovers()).isEqualTo(2L);
        assertThat(response.activeTableCount()).isEqualTo(5L);
        assertThat(response.averageTurnoverRate()).isEqualByComparingTo("0.40");
        assertThat(response.averageSessionDurationMinutes()).isEqualByComparingTo("22.5");
        assertThat(response.tables()).hasSize(2);

        TablePerformance best = response.tables().get(0);
        assertThat(best.tableId()).isEqualTo(tableA);
        assertThat(best.tableNumber()).isEqualTo(1);
        assertThat(best.turnoverCount()).isEqualTo(1L);
        assertThat(best.revenue()).isEqualByComparingTo("100.00");
        assertThat(best.revenueShare()).isEqualByComparingTo("71.43");
        assertThat(best.averageSessionDurationMinutes()).isEqualByComparingTo("30.0");

        TablePerformance second = response.tables().get(1);
        assertThat(second.tableId()).isEqualTo(tableB);
        assertThat(second.revenue()).isEqualByComparingTo("40.00");
        assertThat(second.averageSessionDurationMinutes()).isEqualByComparingTo("15.0");
    }

    @Test
    void getTables_keepsRevenueForATableThatHasSinceBeenDeleted() {
        UUID deletedTable = UUID.randomUUID();
        when(billRepository.findPaidBillActivity(eq(TENANT_ID), any(), any()))
                .thenReturn(List.of(paidBill("s-1", "60.00", LocalDateTime.of(2026, 8, 2, 20, 30))));
        when(sessionRepository.findByTenantIdAndIdIn(eq(TENANT_ID), any()))
                .thenReturn(List.of(sessionAt("s-1", deletedTable, LocalDateTime.of(2026, 8, 2, 20, 0))));
        when(diningTableRepository.findByRestaurantIdAndIdIn(eq(TENANT_ID), any())).thenReturn(List.of());

        AnalyticsTablesResponse response = analyticsService.getTables(TENANT_ID, FROM, TO);

        assertThat(response.tables()).singleElement().satisfies(table -> {
            assertThat(table.tableId()).isEqualTo(deletedTable);
            assertThat(table.tableNumber()).isNull();
            assertThat(table.revenue()).isEqualByComparingTo("60.00");
        });
    }

    @Test
    void getTables_toleratesASessionWithNoCreatedAt() {
        UUID tableA = UUID.randomUUID();
        when(billRepository.findPaidBillActivity(eq(TENANT_ID), any(), any()))
                .thenReturn(List.of(paidBill("s-1", "25.00", LocalDateTime.of(2026, 8, 2, 20, 30))));
        when(sessionRepository.findByTenantIdAndIdIn(eq(TENANT_ID), any()))
                .thenReturn(List.of(sessionAt("s-1", tableA, null)));
        when(diningTableRepository.findByRestaurantIdAndIdIn(eq(TENANT_ID), any()))
                .thenReturn(List.of(DiningTables.builder().id(tableA).tableNumber(3).build()));

        AnalyticsTablesResponse response = analyticsService.getTables(TENANT_ID, FROM, TO);

        assertThat(response.averageSessionDurationMinutes()).isNull();
        assertThat(response.tables()).singleElement().satisfies(table -> {
            assertThat(table.turnoverCount()).isEqualTo(1L);
            assertThat(table.revenue()).isEqualByComparingTo("25.00");
            assertThat(table.averageSessionDurationMinutes()).isNull();
        });
    }

    @Test
    void getTables_skipsBillsWhoseSessionOrTableCannotBeResolved() {
        when(billRepository.findPaidBillActivity(eq(TENANT_ID), any(), any()))
                .thenReturn(List.of(paidBill("s-missing", "999.00", LocalDateTime.of(2026, 8, 2, 20, 30))));
        when(sessionRepository.findByTenantIdAndIdIn(eq(TENANT_ID), any())).thenReturn(List.of());
        when(diningTableRepository.countByRestaurantIdAndIsActiveTrue(TENANT_ID)).thenReturn(2L);

        AnalyticsTablesResponse response = analyticsService.getTables(TENANT_ID, FROM, TO);

        assertThat(response.tables()).isEmpty();
        assertThat(response.totalRevenue()).isEqualByComparingTo("0.00");
        assertThat(response.totalTurnovers()).isZero();
        assertThat(response.activeTableCount()).isEqualTo(2L);
        assertThat(response.averageTurnoverRate()).isEqualByComparingTo("0.00");
        assertThat(response.averageSessionDurationMinutes()).isNull();
        verify(diningTableRepository, never()).findByRestaurantIdAndIdIn(any(), any());
    }

    @Test
    void getTables_withoutAnyPaidBillsKeepsTheLiveActiveTableCount() {
        when(billRepository.findPaidBillActivity(eq(TENANT_ID), any(), any())).thenReturn(List.of());
        when(diningTableRepository.countByRestaurantIdAndIsActiveTrue(TENANT_ID)).thenReturn(4L);

        AnalyticsTablesResponse response = analyticsService.getTables(TENANT_ID, FROM, TO);

        assertThat(response.tables()).isEmpty();
        assertThat(response.activeTableCount()).isEqualTo(4L);
        assertThat(response.averageTurnoverRate()).isEqualByComparingTo("0.00");
        assertThat(response.averageSessionDurationMinutes()).isNull();
        verify(sessionRepository, never()).findByTenantIdAndIdIn(any(), any());
    }

    @Test
    void getTables_averageTurnoverRateIsZeroWithNoActiveTables() {
        UUID tableA = UUID.randomUUID();
        when(billRepository.findPaidBillActivity(eq(TENANT_ID), any(), any()))
                .thenReturn(List.of(paidBill("s-1", "30.00", LocalDateTime.of(2026, 8, 2, 20, 30))));
        when(sessionRepository.findByTenantIdAndIdIn(eq(TENANT_ID), any()))
                .thenReturn(List.of(sessionAt("s-1", tableA, LocalDateTime.of(2026, 8, 2, 20, 0))));
        when(diningTableRepository.findByRestaurantIdAndIdIn(eq(TENANT_ID), any()))
                .thenReturn(List.of(DiningTables.builder().id(tableA).tableNumber(1).build()));
        when(diningTableRepository.countByRestaurantIdAndIsActiveTrue(TENANT_ID)).thenReturn(0L);

        AnalyticsTablesResponse response = analyticsService.getTables(TENANT_ID, FROM, TO);

        assertThat(response.averageTurnoverRate()).isEqualByComparingTo("0.00");
    }

    @Test
    void getTables_rejectsAnInvertedWindow() {
        assertThatThrownBy(() -> analyticsService.getTables(TENANT_ID, TO, FROM))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be after");

        verify(billRepository, never()).findPaidBillActivity(any(), any(), any());
        verify(diningTableRepository, never()).countByRestaurantIdAndIsActiveTrue(any());
    }
}
