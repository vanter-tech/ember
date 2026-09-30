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
import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.session.model.OrderItem;
import com.vanter.ember.session.model.OrderItemStatus;
import com.vanter.ember.session.model.Session;
import com.vanter.ember.session.model.SessionStatus;
import com.vanter.ember.session.service.SessionService;
import com.vanter.ember.settings.service.SettingService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
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
    private final SettingService settingService;

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

        BigDecimal subtotal = billableItems.stream()
                .map(OrderItem::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = subtotal.multiply(taxMultiplier()).setScale(2, RoundingMode.HALF_UP);

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

    /**
     * QA_SIMULATION_REPORT.md E-05: {@code calculateBill} used to ignore the tenant's configured
     * Settings &gt; Billing &gt; "Tax rate (%)" entirely — every bill was invoiced at the bare
     * item subtotal no matter what the admin configured. {@code taxRate} is a percentage (e.g.
     * {@code 16} means 16%), matching the admin UI's "Tax rate (%)" field and {@code TaxRule.rate}
     * ({@code @Min(0) @Max(100)}), not a 0-1 fraction.
     */
    private BigDecimal taxMultiplier() {
        Double taxRatePercent = settingService.getSettings(TenantContextHolder.requireTenantId())
                .getPayload().getBilling().getTaxRate();
        BigDecimal rate = taxRatePercent == null ? BigDecimal.ZERO : BigDecimal.valueOf(taxRatePercent);
        return BigDecimal.ONE.add(rate.divide(BigDecimal.valueOf(100)));
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

        // Same multiplier calculateBill applied to the bill's own total (E-05) — otherwise the
        // sum of per-participant splits would silently drift below the tax-inclusive bill total.
        List<BillSplit> splits = splitsSummingToTotal(bill, amountByParticipant, taxMultiplier());

        return billSplitRepository.saveAll(splits);
    }

    /**
     * Rounding each diner's taxed share on its own can leave the splits a cent off the bill total
     * (two diners at 1.05 with 10% tax: 1.155 -> 1.16 each = 2.32, while the bill rounds 2.31 once).
     * So shares are floored to cents and the cents still missing to reach the rounded grand total
     * go to the diners with the largest fractional remainder (ties keep name order).
     */
    private List<BillSplit> splitsSummingToTotal(
            Bill bill, Map<String, BigDecimal> subtotalByParticipant, BigDecimal taxMultiplier) {
        List<String> names = subtotalByParticipant.keySet().stream().sorted().toList();
        Map<String, BigDecimal> exact = new HashMap<>();
        Map<String, BigDecimal> floored = new HashMap<>();
        BigDecimal exactSum = BigDecimal.ZERO;
        BigDecimal flooredSum = BigDecimal.ZERO;
        for (String name : names) {
            BigDecimal value = subtotalByParticipant.get(name).multiply(taxMultiplier);
            BigDecimal down = value.setScale(2, RoundingMode.FLOOR);
            exact.put(name, value);
            floored.put(name, down);
            exactSum = exactSum.add(value);
            flooredSum = flooredSum.add(down);
        }
        int missingCents = exactSum.setScale(2, RoundingMode.HALF_UP).subtract(flooredSum)
                .movePointRight(2).intValueExact();
        List<String> byRemainder = new ArrayList<>(names);
        byRemainder.sort((a, b) -> exact.get(b).subtract(floored.get(b))
                .compareTo(exact.get(a).subtract(floored.get(a))));
        for (int i = 0; i < missingCents; i++) {
            String name = byRemainder.get(i);
            floored.put(name, floored.get(name).add(new BigDecimal("0.01")));
        }
        return names.stream()
                .map(name -> BillSplit.builder()
                        .bill(bill)
                        .participantName(name)
                        .amount(floored.get(name))
                        .status(BillSplitStatus.UNPAID)
                        .build())
                .toList();
    }

    @Transactional
    public List<BillSplit> splitEqually(Long billId, int participantCount) {
        if (participantCount < 1) {
            throw new IllegalArgumentException(
                    "participantCount must be at least 1, got " + participantCount);
        }

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
