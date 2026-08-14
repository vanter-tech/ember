package com.vanter.ember.analytics.service;

import com.vanter.ember.analytics.dto.AnalyticsRangeResponse;
import com.vanter.ember.analytics.dto.AnalyticsSummaryResponse;
import com.vanter.ember.billing.repository.BillActivityWindow;
import com.vanter.ember.billing.repository.BillRepository;
import com.vanter.ember.billing.repository.BillSalesTotals;
import com.vanter.ember.billing.repository.PaymentRepository;
import com.vanter.ember.session.model.SessionStatus;
import com.vanter.ember.session.repository.SessionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;
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
        LocalDateTime windowStart = from == null ? EPOCH_FLOOR : from;
        LocalDateTime windowEnd = to == null ? LocalDateTime.now() : to;
        if (windowStart.isAfter(windowEnd)) {
            throw new IllegalArgumentException("Analytics range 'from' must not be after 'to'");
        }

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

    private BigDecimal scaled(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
