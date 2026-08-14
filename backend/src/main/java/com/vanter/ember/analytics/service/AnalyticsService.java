package com.vanter.ember.analytics.service;

import com.vanter.ember.analytics.dto.AnalyticsProductsResponse;
import com.vanter.ember.analytics.dto.AnalyticsRangeResponse;
import com.vanter.ember.analytics.dto.AnalyticsSalesResponse;
import com.vanter.ember.analytics.dto.AnalyticsSummaryResponse;
import com.vanter.ember.analytics.dto.AnalyticsTablesResponse;
import com.vanter.ember.analytics.dto.CategoryPerformance;
import com.vanter.ember.analytics.dto.ProductPerformance;
import com.vanter.ember.analytics.dto.SalesBucket;
import com.vanter.ember.analytics.dto.SalesGranularity;
import com.vanter.ember.analytics.dto.TablePerformance;
import com.vanter.ember.billing.repository.BillActivityWindow;
import com.vanter.ember.billing.repository.BillDailyOrders;
import com.vanter.ember.billing.repository.BillRepository;
import com.vanter.ember.billing.repository.BillSalesTotals;
import com.vanter.ember.billing.repository.PaidBillActivity;
import com.vanter.ember.billing.repository.PaymentDailyRevenue;
import com.vanter.ember.billing.repository.PaymentRepository;
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
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
    private final MenuItemRepository menuItemRepository;
    private final DiningTableRepository diningTableRepository;

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

    /**
     * Product performance (Pareto / top-selling) for the dashboard. Shares the window rules of the
     * summary and the sales series, and the same definition of a sale: only the line items of
     * sessions whose bill settled ({@code PAID}) inside the window count, so an open table or an
     * abandoned cart never shows up as a top seller.
     *
     * <p>This is the one analytics read that spans both stores — the line items live on the Mongo
     * {@code Session}, the catalogue in Postgres — so the join happens here rather than in a query.
     * Items still in {@code DRAFT} when the table paid were never ordered and are skipped, and a
     * line item whose menu item has since been deleted keeps the name it was sold under instead of
     * being dropped.
     *
     * <p>Shares are always computed over the whole product set; {@code limit} only truncates the
     * returned list, so a top-10 view still reports true percentages.
     *
     * @throws IllegalArgumentException on an inverted window or a non-positive {@code limit}.
     */
    @Transactional(readOnly = true)
    public AnalyticsProductsResponse getProducts(
            UUID restaurantId, LocalDateTime from, LocalDateTime to, Integer limit) {
        Window window = resolveWindow(from, to);
        if (limit != null && limit < 1) {
            throw new IllegalArgumentException("Analytics 'limit' must be a positive number");
        }

        List<String> soldSessionIds =
                billRepository.findPaidSessionIds(restaurantId, window.start(), window.end());
        if (soldSessionIds.isEmpty()) {
            return emptyProducts(window);
        }

        Map<ProductKey, Tally> tallies = new LinkedHashMap<>();
        for (Session session : sessionRepository.findByTenantIdAndIdIn(restaurantId, soldSessionIds)) {
            if (session.getItems() == null) {
                continue;
            }
            for (OrderItem item : session.getItems()) {
                if (item == null || item.getStatus() == OrderItemStatus.DRAFT) {
                    continue;
                }
                tallies.computeIfAbsent(ProductKey.of(item), key -> new Tally(item.getName()))
                        .add(item.getPrice());
            }
        }
        if (tallies.isEmpty()) {
            return emptyProducts(window);
        }

        Map<Long, MenuItem> catalog = loadCatalog(restaurantId, tallies.keySet());

        BigDecimal totalRevenue = BigDecimal.ZERO;
        long totalQuantity = 0L;
        for (Tally tally : tallies.values()) {
            totalRevenue = totalRevenue.add(tally.revenue);
            totalQuantity += tally.quantity;
        }

        List<Map.Entry<ProductKey, Tally>> ranked = new ArrayList<>(tallies.entrySet());
        ranked.sort(byRevenueThenQuantityThenName());

        List<ProductPerformance> products = new ArrayList<>(ranked.size());
        Map<Long, Tally> byCategory = new LinkedHashMap<>();
        BigDecimal running = BigDecimal.ZERO;
        for (Map.Entry<ProductKey, Tally> entry : ranked) {
            Tally tally = entry.getValue();
            MenuItem menuItem = catalog.get(entry.getKey().itemId());
            Long categoryId = menuItem == null ? null : menuItem.getCategory().getId();
            String categoryName = menuItem == null ? null : menuItem.getCategory().getName();

            running = running.add(tally.revenue);
            products.add(new ProductPerformance(
                    menuItem == null ? null : menuItem.getId(),
                    menuItem == null ? tally.name : menuItem.getName(),
                    categoryId,
                    categoryName,
                    tally.quantity,
                    scaled(tally.revenue),
                    percentOf(tally.revenue, totalRevenue),
                    percentOf(running, totalRevenue)));

            byCategory.computeIfAbsent(categoryId, id -> new Tally(categoryName)).merge(tally);
        }

        List<Map.Entry<Long, Tally>> rankedCategories = new ArrayList<>(byCategory.entrySet());
        rankedCategories.sort(byRevenueThenQuantityThenName());
        List<CategoryPerformance> categories = new ArrayList<>(rankedCategories.size());
        for (Map.Entry<Long, Tally> entry : rankedCategories) {
            Tally tally = entry.getValue();
            categories.add(new CategoryPerformance(
                    entry.getKey(),
                    tally.name,
                    tally.quantity,
                    scaled(tally.revenue),
                    percentOf(tally.revenue, totalRevenue)));
        }

        return new AnalyticsProductsResponse(
                window.start(),
                window.end(),
                scaled(totalRevenue),
                totalQuantity,
                products.size(),
                limit == null || limit >= products.size()
                        ? products
                        : List.copyOf(products.subList(0, limit)),
                categories);
    }

    /**
     * Table performance for the dashboard: turnover, revenue and average session duration per
     * table. {@code Bill} carries no {@code tableId} of its own and a session's open/settle
     * instants only exist on the Mongo {@code Session}, so — like {@link #getProducts} — the join
     * happens here rather than in a query: the {@code PAID} bills in the window are matched back
     * to their session for its {@code tableId} and open time, then rolled up per table.
     *
     * <p>Session duration is approximated as {@code bill.createdAt - session.createdAt}: the same
     * settlement instant every other analytics read anchors "a sale" to, since {@code Session}
     * carries no closed/settled timestamp of its own. {@code activeTableCount} is a LIVE count,
     * like the summary's {@code activeSessions} — it deliberately ignores the window.
     *
     * @throws IllegalArgumentException on an inverted window.
     */
    @Transactional(readOnly = true)
    public AnalyticsTablesResponse getTables(UUID restaurantId, LocalDateTime from, LocalDateTime to) {
        Window window = resolveWindow(from, to);
        long activeTableCount = diningTableRepository.countByRestaurantIdAndIsActiveTrue(restaurantId);

        List<PaidBillActivity> activity =
                billRepository.findPaidBillActivity(restaurantId, window.start(), window.end());
        if (activity.isEmpty()) {
            return emptyTables(window, activeTableCount);
        }

        List<String> sessionIds = activity.stream().map(PaidBillActivity::sessionId).toList();
        Map<String, Session> sessionsById = new HashMap<>();
        for (Session session : sessionRepository.findByTenantIdAndIdIn(restaurantId, sessionIds)) {
            sessionsById.put(session.getId(), session);
        }

        Map<UUID, TableTally> tallies = new LinkedHashMap<>();
        for (PaidBillActivity bill : activity) {
            Session session = sessionsById.get(bill.sessionId());
            if (session == null || session.getTableId() == null) {
                continue;
            }
            tallies.computeIfAbsent(session.getTableId(), id -> new TableTally())
                    .add(bill.total(), session.getCreatedAt(), bill.createdAt());
        }
        if (tallies.isEmpty()) {
            return emptyTables(window, activeTableCount);
        }

        Map<UUID, DiningTables> tablesById = new HashMap<>();
        for (DiningTables table :
                diningTableRepository.findByRestaurantIdAndIdIn(restaurantId, tallies.keySet())) {
            tablesById.put(table.getId(), table);
        }

        BigDecimal totalRevenue = BigDecimal.ZERO;
        long totalTurnovers = 0L;
        long totalDurationSeconds = 0L;
        long totalDurationSamples = 0L;
        for (TableTally tally : tallies.values()) {
            totalRevenue = totalRevenue.add(tally.revenue);
            totalTurnovers += tally.turnoverCount;
            totalDurationSeconds += tally.durationSeconds;
            totalDurationSamples += tally.durationSamples;
        }

        List<Map.Entry<UUID, TableTally>> ranked = new ArrayList<>(tallies.entrySet());
        ranked.sort(Comparator
                .comparing((Map.Entry<UUID, TableTally> entry) -> entry.getValue().revenue).reversed()
                .thenComparing(entry -> entry.getValue().turnoverCount, Comparator.reverseOrder()));

        List<TablePerformance> tables = new ArrayList<>(ranked.size());
        for (Map.Entry<UUID, TableTally> entry : ranked) {
            TableTally tally = entry.getValue();
            DiningTables table = tablesById.get(entry.getKey());
            tables.add(new TablePerformance(
                    entry.getKey(),
                    table == null ? null : table.getTableNumber(),
                    tally.turnoverCount,
                    scaled(tally.revenue),
                    percentOf(tally.revenue, totalRevenue),
                    averageMinutes(tally.durationSeconds, tally.durationSamples)));
        }

        BigDecimal averageTurnoverRate = activeTableCount == 0
                ? scaled(BigDecimal.ZERO)
                : BigDecimal.valueOf(totalTurnovers)
                        .divide(BigDecimal.valueOf(activeTableCount), 2, RoundingMode.HALF_UP);

        return new AnalyticsTablesResponse(
                window.start(),
                window.end(),
                activeTableCount,
                totalTurnovers,
                scaled(totalRevenue),
                averageTurnoverRate,
                averageMinutes(totalDurationSeconds, totalDurationSamples),
                tables);
    }

    private AnalyticsTablesResponse emptyTables(Window window, long activeTableCount) {
        return new AnalyticsTablesResponse(
                window.start(),
                window.end(),
                activeTableCount,
                0L,
                scaled(BigDecimal.ZERO),
                scaled(BigDecimal.ZERO),
                null,
                List.of());
    }

    /** Mean minutes between session-open and bill-settle over the given samples, null with none. */
    private BigDecimal averageMinutes(long totalSeconds, long samples) {
        if (samples == 0) {
            return null;
        }
        return BigDecimal.valueOf(totalSeconds)
                .divide(BigDecimal.valueOf(samples * 60), 1, RoundingMode.HALF_UP);
    }

    /** Catalogue rows for the tallied items, skipped entirely when nothing carried a menu-item id. */
    private Map<Long, MenuItem> loadCatalog(UUID restaurantId, Set<ProductKey> keys) {
        Set<Long> itemIds = new LinkedHashSet<>();
        for (ProductKey key : keys) {
            if (key.itemId() != null) {
                itemIds.add(key.itemId());
            }
        }
        if (itemIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, MenuItem> catalog = new HashMap<>();
        for (MenuItem menuItem :
                menuItemRepository.findByTenantIdAndIdInWithCategory(restaurantId, itemIds)) {
            catalog.put(menuItem.getId(), menuItem);
        }
        return catalog;
    }

    /** Biggest seller first; quantity then name break ties so the ranking is stable across calls. */
    private <K> Comparator<Map.Entry<K, Tally>> byRevenueThenQuantityThenName() {
        return Comparator
                .comparing((Map.Entry<K, Tally> entry) -> entry.getValue().revenue).reversed()
                .thenComparing(entry -> entry.getValue().quantity, Comparator.reverseOrder())
                .thenComparing(
                        entry -> entry.getValue().name,
                        Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private AnalyticsProductsResponse emptyProducts(Window window) {
        return new AnalyticsProductsResponse(
                window.start(), window.end(), scaled(BigDecimal.ZERO), 0L, 0, List.of(), List.of());
    }

    /** Percentage of the window's item revenue, from unrounded money so shares do not drift. */
    private BigDecimal percentOf(BigDecimal part, BigDecimal total) {
        if (total.signum() == 0) {
            return scaled(BigDecimal.ZERO);
        }
        return part.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP);
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

    /**
     * Groups line items by the menu item they were ordered from. Items that carry no catalogue id —
     * legacy or hand-added rows — fall back to grouping by the name they were sold under, so they
     * still aggregate instead of collapsing into one nameless bucket.
     */
    private record ProductKey(Long itemId, String name) {
        static ProductKey of(OrderItem item) {
            return item.getItemId() != null
                    ? new ProductKey(item.getItemId(), null)
                    : new ProductKey(null, item.getName());
        }
    }

    /** Mutable running total for one product or category while the two stores are joined up. */
    private static final class Tally {

        private final String name;
        private long quantity;
        private BigDecimal revenue = BigDecimal.ZERO;

        private Tally(String name) {
            this.name = name;
        }

        /** One line item is one unit — {@code OrderItem} carries no quantity of its own. */
        private void add(BigDecimal price) {
            quantity++;
            if (price != null) {
                revenue = revenue.add(price);
            }
        }

        private void merge(Tally other) {
            quantity += other.quantity;
            revenue = revenue.add(other.revenue);
        }
    }

    /** Mutable running total for one table while its bills and sessions are joined up. */
    private static final class TableTally {

        private long turnoverCount;
        private BigDecimal revenue = BigDecimal.ZERO;
        private long durationSeconds;
        private long durationSamples;

        /** {@code sessionOpenedAt} may be missing on legacy data — duration is skipped, not the turnover. */
        private void add(BigDecimal total, LocalDateTime sessionOpenedAt, LocalDateTime billSettledAt) {
            turnoverCount++;
            if (total != null) {
                revenue = revenue.add(total);
            }
            if (sessionOpenedAt != null) {
                durationSeconds += Duration.between(sessionOpenedAt, billSettledAt).getSeconds();
                durationSamples++;
            }
        }
    }
}
