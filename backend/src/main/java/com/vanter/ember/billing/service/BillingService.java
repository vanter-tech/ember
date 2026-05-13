package com.vanter.ember.billing.service;

import com.vanter.ember.billing.model.Bill;
import com.vanter.ember.billing.model.BillStatus;
import com.vanter.ember.billing.model.SplitMethod;
import com.vanter.ember.billing.repository.BillRepository;
import com.vanter.ember.session.model.OrderItem;
import com.vanter.ember.session.model.OrderItemStatus;
import com.vanter.ember.session.service.SessionService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BillingService {

    private final BillRepository billRepository;
    private final SessionService sessionService;

    public Bill calculateBill(String sessionId, SplitMethod splitMethod) {
        if (billRepository.findBySessionId(sessionId).isPresent()) {
            throw new IllegalStateException("Session already billed: " + sessionId);
        }

        List<OrderItem> billableItems = sessionService.findById(sessionId).getItems().stream()
                .filter(i -> i.getStatus() == OrderItemStatus.DELIVERED
                        || i.getStatus() == OrderItemStatus.READY)
                .toList();

        if (billableItems.isEmpty()) {
            throw new IllegalStateException("No billable items in session: " + sessionId);
        }

        BigDecimal total = billableItems.stream()
                .map(OrderItem::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return billRepository.save(Bill.builder()
                .sessionId(sessionId)
                .total(total)
                .splitMethod(splitMethod)
                .status(BillStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .build());
    }
}
