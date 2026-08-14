package com.vanter.ember.analytics.service;

import com.vanter.ember.analytics.dto.AnalyticsRangeResponse;
import com.vanter.ember.billing.repository.BillActivityWindow;
import com.vanter.ember.billing.repository.BillRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final BillRepository billRepository;

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
}
