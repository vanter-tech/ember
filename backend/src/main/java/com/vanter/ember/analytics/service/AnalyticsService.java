package com.vanter.ember.analytics.service;

import com.vanter.ember.analytics.dto.AnalyticsRangeResponse;
import com.vanter.ember.analytics.dto.AnalyticsSalesResponse;
import com.vanter.ember.analytics.dto.AnalyticsSummaryResponse;
import com.vanter.ember.analytics.dto.SalesBucket;
import com.vanter.ember.analytics.dto.SalesGranularity;
import com.vanter.ember.billing.repository.BillActivityWindow;
import com.vanter.ember.billing.repository.BillDailyOrders;
import com.vanter.ember.billing.repository.BillRepository;
import com.vanter.ember.billing.repository.BillSalesTotals;
import com.vanter.ember.billing.repository.PaymentDailyRevenue;
import com.vanter.ember.billing.repository.PaymentRepository;
import com.vanter.ember.session.model.SessionStatus;
import com.vanter.ember.session.repository.SessionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    /**
     * Lower bound used when the client sends no {@code from}. {@code LocalDateTime.MIN} is year
     * -999999999, which no SQL {@code timestamp} column can hold, so the epoch stands in for
     * "everything this tenant has ever billed".
     */
    static final LocalDateTime EPOCH_FLOOR = LocalDateTime.of(1970, 1, 1, 0, 0);

    private final BillRepository billRepository;
    private final PaymentRepository paymentRepository;
    private final SessionRepository sessionRepository;

    @Transactional(readOnly = true)
    public AnalyticsRangeResponse getRange(UUID restaurantId) {
        BillActivityWindow window = billRepository.findActivityWindow(restaurantId);
        if (window == null) {
            return new AnalyticsRangeResponse(null, null, 0L);
        }
        return new AnalyticsRangeResponse(
                window.firstBillAt(),
                window.lastBillAt(),
                window.billCount() == null ? 0L : window.billCount());
    }

    /**
     * Summary cards for the dashboard. {@code from}/{@code to} are optional and inclusive; they
     * default to the whole of the tenant's history up to now.
     *
     * @throws IllegalArgumentException if the window is inverted — an empty result there would look
     *     like "no sales" rather than like the bad input it is.
     */
    @Transactional(readOnly = true)
    public AnalyticsSummaryResponse getSummary(UUID restaurantId, LocalDateTime from, LocalDateTime to) {
        Window window = resolveWindow(from, to);
        LocalDateTime windowStart = window.start();
        LocalDateTime windowEnd = window.end();

        BigDecimal revenue = paymentRepository.sumConfirmedRevenue(restaurantId, windowStart, windowEnd);
        BillSalesTotals sales = billRepository.findSalesTotals(restaurantId, windowStart, windowEnd);
        long activeSessions = sessionRepository.countByTenantIdAndStatus(restaurantId, SessionStatus.OPEN);

        long paidBillCount = sales == null || sales.billCount() == null ? 0L : sales.billCount();
        BigDecimal salesTotal = sales == null || sales.salesTotal() == null ? BigDecimal.ZERO : sales.salesTotal();
        BigDecimal averageOrderValue = paidBillCount == 0
                ? scaled(BigDecimal.ZERO)
                : salesTotal.divide(BigDecimal.valueOf(paidBillCount), 2, RoundingMode.HALF_UP);

        return new AnalyticsSummaryResponse(
                scaled(revenue == null ? BigDecimal.ZERO : revenue),
                activeSessions,
                averageOrderValue,
                paidBillCount,
                windowStart,
                windowEnd);
    }

    /**
     * Temporal sales series for the dashboard chart. Shares the summary's window rules and metric
     * semantics — revenue is confirmed payments, orders are {@code PAID} bills — and rolls the
     * per-day database rows up into day/week/month/year buckets here, so ISO week boundaries do not
     * depend on the database vendor.
     *
     * <p>The returned series is gap-free. It starts at the client's {@code from}; with no
     * {@code from} it starts at the first bucket that saw activity rather than at
     * {@link #EPOCH_FLOOR}, which would otherwise emit decades of empty daily buckets.
     *
     * @throws IllegalArgumentException on an inverted window or an unknown granularity.
     */
    @Transactional(readOnly = true)
    public AnalyticsSalesResponse getSales(
            UUID restaurantId, String granularityParam, LocalDateTime from, LocalDateTime to) {
        SalesGranularity granularity = SalesGranularity.from(granularityParam);
        Window window = resolveWindow(from, to);

        Map<LocalDate, BigDecimal> revenueByBucket = new HashMap<>();
        for (PaymentDailyRevenue row :
                paymentRepository.findConfirmedRevenueByDay(restaurantId, window.start(), window.end())) {
            revenueByBucket.merge(
                    granularity.bucketStart(row.date()),
                    row.revenue() == null ? BigDecimal.ZERO : row.revenue(),
                    BigDecimal::add);
        }

        Map<LocalDate, Long> ordersByBucket = new HashMap<>();
        for (BillDailyOrders row :
                billRepository.findPaidBillsByDay(restaurantId, window.start(), window.end())) {
            ordersByBucket.merge(
                    granularity.bucketStart(row.date()),
                    row.billCount() == null ? 0L : row.billCount(),
                    Long::sum);
        }

        LocalDate seriesEnd = granularity.bucketStart(window.end().toLocalDate());
        LocalDate seriesStart = from != null
                ? granularity.bucketStart(from.toLocalDate())
                : earliestBucket(revenueByBucket.keySet(), ordersByBucket.keySet());

        List<SalesBucket> buckets = new ArrayList<>();
        BigDecimal totalRevenue = BigDecimal.ZERO;
        long totalPaidBills = 0L;
        for (LocalDate cursor = seriesStart;
                cursor != null && !cursor.isAfter(seriesEnd);
                cursor = granularity.next(cursor)) {
            BigDecimal revenue = scaled(revenueByBucket.getOrDefault(cursor, BigDecimal.ZERO));
            long paidBills = ordersByBucket.getOrDefault(cursor, 0L);
            buckets.add(new SalesBucket(cursor, granularity.bucketEnd(cursor), revenue, paidBills));
            totalRevenue = totalRevenue.add(revenue);
            totalPaidBills += paidBills;
        }

        return new AnalyticsSalesResponse(
                granularity,
                window.start(),
                window.end(),
                scaled(totalRevenue),
                totalPaidBills,
                buckets);
    }

    /** The window both analytics reads apply: optional, inclusive, and never inverted. */
    private Window resolveWindow(LocalDateTime from, LocalDateTime to) {
        LocalDateTime start = from == null ? EPOCH_FLOOR : from;
        LocalDateTime end = to == null ? LocalDateTime.now() : to;
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("Analytics range 'from' must not be after 'to'");
        }
        return new Window(start, end);
    }

    /** Null when neither measure produced a single bucket, i.e. the tenant billed nothing. */
    private LocalDate earliestBucket(Set<LocalDate> revenueBuckets, Set<LocalDate> orderBuckets) {
        return Stream.concat(revenueBuckets.stream(), orderBuckets.stream())
                .min(LocalDate::compareTo)
                .orElse(null);
    }

    private BigDecimal scaled(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private record Window(LocalDateTime start, LocalDateTime end) {}
}
