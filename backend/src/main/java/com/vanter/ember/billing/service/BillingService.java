package com.vanter.ember.billing.service;

import com.vanter.ember.billing.model.Bill;
import com.vanter.ember.billing.model.BillSplit;
import com.vanter.ember.billing.model.BillStatus;
import com.vanter.ember.billing.model.SplitMethod;
import com.vanter.ember.billing.repository.BillRepository;
import com.vanter.ember.billing.repository.BillSplitRepository;
import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.session.model.OrderItem;
import com.vanter.ember.session.model.OrderItemStatus;
import com.vanter.ember.session.service.SessionService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BillingService {

    private final BillRepository billRepository;
    private final BillSplitRepository billSplitRepository;
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

    public List<BillSplit> splitByConsumption(Long billId) {
        Bill bill = billRepository.findById(billId)
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found: " + billId));

        Map<String, BigDecimal> amountByParticipant = sessionService.findById(bill.getSessionId())
                .getItems().stream()
                .filter(i -> i.getStatus() == OrderItemStatus.DELIVERED
                        || i.getStatus() == OrderItemStatus.READY)
                .collect(Collectors.groupingBy(
                        OrderItem::getParticipantName,
                        Collectors.reducing(BigDecimal.ZERO, OrderItem::getPrice, BigDecimal::add)));

        List<BillSplit> splits = amountByParticipant.entrySet().stream()
                .map(e -> BillSplit.builder()
                        .bill(bill)
                        .participantName(e.getKey())
                        .amount(e.getValue())
                        .paid(false)
                        .build())
                .toList();

        return billSplitRepository.saveAll(splits);
    }
}
