package com.vanter.ember.billing.service;

import com.vanter.ember.billing.dto.BillVoidedMessage;
import com.vanter.ember.billing.model.Bill;
import com.vanter.ember.billing.model.BillSplit;
import com.vanter.ember.billing.model.BillSplitStatus;
import com.vanter.ember.billing.model.BillStatus;
import com.vanter.ember.billing.model.PaymentStatus;
import com.vanter.ember.billing.model.SplitMethod;
import com.vanter.ember.billing.repository.BillRepository;
import com.vanter.ember.billing.repository.BillSplitRepository;
import com.vanter.ember.billing.repository.PaymentRepository;
import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.session.model.OrderItem;
import com.vanter.ember.session.model.OrderItemStatus;
import com.vanter.ember.session.model.Session;
import com.vanter.ember.session.model.SessionStatus;
import com.vanter.ember.session.service.SessionService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BillingService {

    private final BillRepository billRepository;
    private final BillSplitRepository billSplitRepository;
    private final SessionService sessionService;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public Bill calculateBill(String sessionId, SplitMethod splitMethod) {
        if (billRepository.findBySessionIdAndStatusNot(sessionId, BillStatus.VOIDED).isPresent()) {
            throw new IllegalStateException("Session already billed: " + sessionId);
        }

        Session session = sessionService.findById(sessionId);
        if (session.getStatus() != SessionStatus.OPEN) {
            throw new IllegalStateException("Session is not open: " + sessionId);
        }

        List<OrderItem> billableItems = session.getItems().stream()
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

    @Transactional
    public Bill voidBill(Long billId, String reason, String voidedByEmail) {
        Bill bill = billRepository.findByIdForUpdate(billId)
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found: " + billId));
        if (bill.getStatus() != BillStatus.OPEN) {
            throw new IllegalStateException("Bill is not open: " + billId);
        }
        if (paymentRepository.existsByBillIdAndStatus(billId, PaymentStatus.CONFIRMED)) {
            throw new IllegalStateException(
                    "Cannot void a bill with a confirmed payment; refund it instead: " + billId);
        }

        bill.setStatus(BillStatus.VOIDED);
        bill.setVoidedBy(resolveUserId(voidedByEmail));
        bill.setVoidedAt(LocalDateTime.now());
        bill.setVoidReason(reason);
        Bill saved = billRepository.save(bill);

        messagingTemplate.convertAndSend(
                "/topic/session/" + bill.getSessionId(), BillVoidedMessage.of(billId, reason));

        return saved;
    }

    private String resolveUserId(String email) {
        return userRepository.findByEmail(email)
                .map(User::getId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }

    @Transactional
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
                        .status(BillSplitStatus.UNPAID)
                        .build())
                .toList();

        return billSplitRepository.saveAll(splits);
    }

    @Transactional
    public List<BillSplit> splitEqually(Long billId, int participantCount) {
        Bill bill = billRepository.findById(billId)
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found: " + billId));

        Session session = sessionService.findById(bill.getSessionId());
        if (participantCount > session.getParticipants().size()) {
            throw new IllegalArgumentException(
                    "participantCount " + participantCount +
                    " exceeds actual participant count " + session.getParticipants().size());
        }

        List<String> names = session.getParticipants().stream()
                .map(p -> p.getName())
                .limit(participantCount)
                .collect(Collectors.toList());

        BigDecimal share = bill.getTotal()
                .divide(BigDecimal.valueOf(participantCount), 2, RoundingMode.FLOOR);

        List<BillSplit> splits = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            BigDecimal amount = (i == names.size() - 1)
                    ? bill.getTotal().subtract(share.multiply(BigDecimal.valueOf(names.size() - 1)))
                    : share;
            splits.add(BillSplit.builder()
                    .bill(bill)
                    .participantName(names.get(i))
                    .amount(amount)
                    .status(BillSplitStatus.UNPAID)
                    .build());
        }

        return billSplitRepository.saveAll(splits);
    }
}
