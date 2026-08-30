package com.vanter.ember.billing.service;

import com.vanter.ember.billing.dto.DigitalPaymentInitiatedMessage;
import com.vanter.ember.billing.dto.PaymentResponse;
import com.vanter.ember.billing.dto.RefundResponse;
import com.vanter.ember.billing.dto.SplitPaidMessage;
import com.vanter.ember.billing.dto.SplitRefundedMessage;
import com.vanter.ember.billing.dto.SplitsRedistributedMessage;
import com.vanter.ember.billing.dto.WaiterBillStateResponse;
import com.vanter.ember.billing.event.PaymentCompleted;
import com.vanter.ember.billing.model.Bill;
import com.vanter.ember.billing.model.BillSplit;
import com.vanter.ember.billing.model.BillSplitStatus;
import com.vanter.ember.billing.model.BillStatus;
import com.vanter.ember.billing.model.Payment;
import com.vanter.ember.billing.model.PaymentMethod;
import com.vanter.ember.billing.model.PaymentStatus;
import com.vanter.ember.billing.model.Refund;
import com.vanter.ember.billing.repository.BillRepository;
import com.vanter.ember.billing.repository.BillSplitRepository;
import com.vanter.ember.billing.repository.PaymentRepository;
import com.vanter.ember.billing.repository.RefundRepository;
import com.vanter.ember.cashregister.event.CashMovementRecorded;
import com.vanter.ember.cashregister.exception.CashShiftOverdueException;
import com.vanter.ember.cashregister.model.CashMovement;
import com.vanter.ember.cashregister.model.CashMovementType;
import com.vanter.ember.cashregister.model.CashShift;
import com.vanter.ember.cashregister.repository.CashMovementRepository;
import com.vanter.ember.cashregister.repository.CashShiftRepository;
import com.vanter.ember.cashregister.service.CashShiftDeadlineService;
import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.session.service.SessionService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final BillRepository billRepository;
    private final BillSplitRepository billSplitRepository;
    private final PaymentRepository paymentRepository;
    private final SessionService sessionService;
    private final CashShiftRepository cashShiftRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final SimpMessagingTemplate messagingTemplate;
    private final RefundRepository refundRepository;
    private final CashMovementRepository cashMovementRepository;
    private final CashShiftDeadlineService deadlineService;

    @Transactional
    public Payment registerPhysicalPayment(
            Long billId, String participantName, BigDecimal amount, String processedByEmail) {
        CashShift shift = cashShiftRepository.findOpenForUpdate(TenantContextHolder.requireTenantId())
                .orElseThrow(() -> new IllegalStateException(
                        "No open cash shift; open one before registering a physical payment"));
        if (deadlineService.isOverdue(shift, LocalDateTime.now())) {
            throw new CashShiftOverdueException(
                    "Cash shift is overdue; prolong or close it before registering a physical payment");
        }

        Bill bill = billRepository.findByIdForUpdate(billId)
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found: " + billId));
        if (bill.getStatus() == BillStatus.VOIDED) {
            throw new IllegalStateException("Cannot register a payment against a voided bill: " + billId);
        }

        BillSplit split = billSplitRepository.findByBillIdAndParticipantName(billId, participantName)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Split not found for participant: " + participantName));

        if (amount.compareTo(split.getAmount()) != 0) {
            throw new IllegalArgumentException(
                    "Payment amount " + amount + " does not match split amount " + split.getAmount());
        }

        split.setStatus(BillSplitStatus.PAID);
        billSplitRepository.save(split);
        messagingTemplate.convertAndSend(
                "/topic/session/" + bill.getSessionId(),
                SplitPaidMessage.of(billId, participantName, split.getStatus().name()));

        Payment payment = paymentRepository.save(Payment.builder()
                .bill(bill)
                .participantName(participantName)
                .amount(amount)
                .method(PaymentMethod.PHYSICAL)
                .status(PaymentStatus.CONFIRMED)
                .cashShiftId(shift.getId())
                .processedBy(resolveUserId(processedByEmail))
                .createdAt(LocalDateTime.now())
                .build());

        List<BillSplit> allSplits = billSplitRepository.findByBillId(billId);
        boolean allPaid = allSplits.stream().allMatch(s -> s.getStatus() == BillSplitStatus.PAID);
        if (allPaid) {
            bill.setStatus(BillStatus.PAID);
            billRepository.save(bill);
            UUID tableId = sessionService.findById(bill.getSessionId()).getTableId();
            eventPublisher.publishEvent(new PaymentCompleted(bill.getSessionId(), tableId, billId));
        }

        return payment;
    }

    @Transactional
    public Payment initiateDigitalPayment(
            Long billId, String participantName, BigDecimal amount, String processedByEmail) {
        Bill bill = billRepository.findById(billId)
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found: " + billId));
        if (bill.getStatus() == BillStatus.VOIDED) {
            throw new IllegalStateException("Cannot register a payment against a voided bill: " + billId);
        }

        BillSplit split = billSplitRepository.findByBillIdAndParticipantName(billId, participantName)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Split not found for participant: " + participantName));

        if (amount.compareTo(split.getAmount()) != 0) {
            throw new IllegalArgumentException(
                    "Payment amount " + amount + " does not match split amount " + split.getAmount());
        }

        Payment payment = paymentRepository.save(Payment.builder()
                .bill(bill)
                .participantName(participantName)
                .amount(amount)
                .method(PaymentMethod.DIGITAL)
                .status(PaymentStatus.PENDING)
                .gatewayRef("STUB-" + UUID.randomUUID())
                .processedBy(resolveUserId(processedByEmail))
                .createdAt(LocalDateTime.now())
                .build());

        messagingTemplate.convertAndSend(
                "/topic/session/" + bill.getSessionId(),
                DigitalPaymentInitiatedMessage.of(payment.getId(), billId, participantName, amount));

        return payment;
    }

    @Transactional
    public Payment confirmDigitalPayment(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));

        Bill bill = billRepository.findByIdForUpdate(payment.getBill().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found: " + payment.getBill().getId()));
        if (bill.getStatus() == BillStatus.VOIDED) {
            throw new IllegalStateException(
                    "Cannot confirm a payment against a voided bill: " + payment.getBill().getId());
        }

        BillSplit split = billSplitRepository
                .findByBillIdAndParticipantName(payment.getBill().getId(), payment.getParticipantName())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Split not found for participant: " + payment.getParticipantName()));

        split.setStatus(BillSplitStatus.PAID);
        billSplitRepository.save(split);
        messagingTemplate.convertAndSend(
                "/topic/session/" + bill.getSessionId(),
                SplitPaidMessage.of(bill.getId(), payment.getParticipantName(), split.getStatus().name()));

        payment.setStatus(PaymentStatus.CONFIRMED);
        Payment saved = paymentRepository.save(payment);

        List<BillSplit> allSplits = billSplitRepository.findByBillId(payment.getBill().getId());
        boolean allPaid = allSplits.stream().allMatch(s -> s.getStatus() == BillSplitStatus.PAID);
        if (allPaid) {
            bill.setStatus(BillStatus.PAID);
            billRepository.save(bill);
            UUID tableId = sessionService.findById(payment.getBill().getSessionId()).getTableId();
            eventPublisher.publishEvent(
                    new PaymentCompleted(payment.getBill().getSessionId(), tableId,
                            payment.getBill().getId()));
        }

        return saved;
    }

    @Transactional
    public Refund refundPayment(Long paymentId, BigDecimal amount, String reason, String refundedByEmail) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));
        if (payment.getStatus() != PaymentStatus.CONFIRMED) {
            throw new IllegalStateException("Payment is not confirmed: " + paymentId);
        }

        BigDecimal remaining = payment.getAmount().subtract(refundRepository.sumByPaymentId(paymentId));
        BigDecimal refundAmount = amount != null ? amount : remaining;
        if (refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Refund amount must be positive: " + refundAmount);
        }
        if (refundAmount.compareTo(remaining) > 0) {
            throw new IllegalArgumentException(
                    "Refund amount " + refundAmount + " exceeds remaining balance " + remaining);
        }

        String refundedBy = resolveUserId(refundedByEmail);

        if (payment.getMethod() == PaymentMethod.PHYSICAL) {
            CashShift openShift = cashShiftRepository.findOpenForUpdate(TenantContextHolder.requireTenantId())
                    .orElseThrow(() -> new IllegalStateException(
                            "No open cash shift; open one before refunding a physical payment"));
            // Built directly rather than via CashShiftService.recordMovement to avoid a circular
            // billing<->cashregister service dependency — CashShiftService.getDetail (Task 4)
            // depends on PaymentService to list a shift's payments, so this direction must not
            // depend back on CashShiftService.
            cashMovementRepository.save(CashMovement.builder()
                    .cashShiftId(openShift.getId())
                    .type(CashMovementType.CASH_OUT)
                    .amount(refundAmount)
                    .reason("Refund of payment #" + paymentId + ": " + reason)
                    .createdBy(refundedBy)
                    .createdAt(LocalDateTime.now())
                    .build());
            eventPublisher.publishEvent(new CashMovementRecorded(openShift.getTenantId(), openShift.getId()));
        }

        Refund refund = refundRepository.save(Refund.builder()
                .payment(payment)
                .amount(refundAmount)
                .reason(reason)
                .refundedBy(refundedBy)
                .createdAt(LocalDateTime.now())
                .build());

        Long billId = payment.getBill().getId();
        BillSplit split = updateSplitStatus(billId, payment.getParticipantName());

        messagingTemplate.convertAndSend(
                "/topic/session/" + payment.getBill().getSessionId(),
                SplitRefundedMessage.of(billId, payment.getParticipantName(), split.getStatus().name(), refundAmount));

        return refund;
    }

    private BillSplit updateSplitStatus(Long billId, String participantName) {
        BillSplit split = billSplitRepository.findByBillIdAndParticipantName(billId, participantName)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Split not found for participant: " + participantName));

        List<Payment> confirmedPayments = paymentRepository.findByBillId(billId).stream()
                .filter(p -> p.getParticipantName().equals(participantName)
                        && p.getStatus() == PaymentStatus.CONFIRMED)
                .toList();
        BigDecimal netPaid = confirmedPayments.stream()
                .map(p -> p.getAmount().subtract(refundRepository.sumByPaymentId(p.getId())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BillSplitStatus status;
        if (netPaid.compareTo(BigDecimal.ZERO) <= 0) {
            status = BillSplitStatus.UNPAID;
        } else if (netPaid.compareTo(split.getAmount()) >= 0) {
            status = BillSplitStatus.PAID;
        } else {
            status = BillSplitStatus.PARTIALLY_PAID;
        }
        split.setStatus(status);
        return billSplitRepository.save(split);
    }

    /**
     * Spread a departing diner's still-unpaid share across the participants still present.
     * The bill total is unchanged — only how it is divided. The departing split must be
     * {@code UNPAID} (a partially-/fully-paid split has money attached and must be refunded
     * first). Broadcasts {@link SplitsRedistributedMessage} with the full post-redistribution
     * split list so every client can replace its view.
     */
    @Transactional
    public List<BillSplit> redistributeSplit(Long billId, String departingParticipantName) {
        Bill bill = billRepository.findByIdForUpdate(billId)
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found: " + billId));
        if (bill.getStatus() != BillStatus.OPEN) {
            throw new IllegalStateException("Bill is not open: " + billId);
        }

        BillSplit departing = billSplitRepository.findByBillIdAndParticipantName(billId, departingParticipantName)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Split not found for participant: " + departingParticipantName));
        if (departing.getStatus() != BillSplitStatus.UNPAID) {
            throw new IllegalStateException(
                    "Cannot redistribute the share of '" + departingParticipantName
                            + "': their split is already " + departing.getStatus()
                            + " — refund it first");
        }

        List<BillSplit> recipients = billSplitRepository.findByBillId(billId).stream()
                .filter(s -> !s.getParticipantName().equals(departingParticipantName))
                .filter(s -> s.getStatus() != BillSplitStatus.PAID)
                .toList();
        if (recipients.isEmpty()) {
            throw new IllegalStateException(
                    "No remaining participants to absorb the share of '" + departingParticipantName + "'");
        }

        BigDecimal amount = departing.getAmount();
        int n = recipients.size();
        BigDecimal share = amount.divide(BigDecimal.valueOf(n), 2, RoundingMode.FLOOR);
        for (int i = 0; i < n; i++) {
            BigDecimal portion = (i == n - 1)
                    ? amount.subtract(share.multiply(BigDecimal.valueOf(n - 1)))
                    : share;
            BillSplit recipient = recipients.get(i);
            recipient.setAmount(recipient.getAmount().add(portion));
        }
        billSplitRepository.saveAll(recipients);
        billSplitRepository.delete(departing);

        List<BillSplit> remaining = billSplitRepository.findByBillId(billId);
        messagingTemplate.convertAndSend(
                "/topic/session/" + bill.getSessionId(),
                SplitsRedistributedMessage.of(billId, departingParticipantName, remaining));
        return remaining;
    }

    /**
     * Close a partially-paid session from the waiter side. Every split must be {@code PAID}
     * (the waiter redistributes or collects any outstanding share first) — otherwise a 409.
     * On success the bill is marked {@code PAID} and a {@link PaymentCompleted} event drives
     * the existing session-close + {@code SESSION_CLOSED} broadcast.
     */
    @Transactional
    public Bill settleAndClose(Long billId) {
        Bill bill = billRepository.findByIdForUpdate(billId)
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found: " + billId));
        if (bill.getStatus() == BillStatus.VOIDED) {
            throw new IllegalStateException("Cannot settle a voided bill: " + billId);
        }
        if (bill.getStatus() != BillStatus.OPEN) {
            throw new IllegalStateException("Bill is not open: " + billId);
        }

        List<BillSplit> splits = billSplitRepository.findByBillId(billId);
        if (splits.isEmpty()) {
            throw new IllegalStateException("Bill has no splits to settle: " + billId);
        }
        long unpaid = splits.stream().filter(s -> s.getStatus() != BillSplitStatus.PAID).count();
        if (unpaid > 0) {
            throw new IllegalStateException(
                    "Cannot settle: " + unpaid + " split(s) still unpaid — redistribute or collect them first");
        }

        bill.setStatus(BillStatus.PAID);
        billRepository.save(bill);
        UUID tableId = sessionService.findById(bill.getSessionId()).getTableId();
        eventPublisher.publishEvent(new PaymentCompleted(bill.getSessionId(), tableId, billId));
        return bill;
    }

    public List<PaymentResponse> listPayments(Long billId) {
        return toResponses(paymentRepository.findByBillId(billId));
    }

    /**
     * The current non-voided bill for a session (or {@code null} if none exists yet), shaped for a
     * page that needs to rebuild its bill view without a live {@code BILL_READY} frame — a waiter
     * reopening the table, or a diner rejoining after a reload/re-login.
     */
    public WaiterBillStateResponse getBillState(String sessionId) {
        Bill bill = billRepository.findBySessionIdAndStatusNot(sessionId, BillStatus.VOIDED).orElse(null);
        if (bill == null) {
            return null;
        }
        List<BillSplit> splits = billSplitRepository.findByBillId(bill.getId());
        List<WaiterBillStateResponse.PendingDigitalPayment> pending = paymentRepository.findByBillId(bill.getId())
                .stream()
                .filter(p -> p.getMethod() == PaymentMethod.DIGITAL && p.getStatus() == PaymentStatus.PENDING)
                .map(p -> new WaiterBillStateResponse.PendingDigitalPayment(
                        p.getId(), p.getParticipantName(), p.getAmount()))
                .toList();
        return new WaiterBillStateResponse(bill.getId(), bill.getTotal(), splits, pending);
    }

    public List<PaymentResponse> toResponses(List<Payment> payments) {
        return payments.stream().map(p -> {
            BigDecimal refunded = refundRepository.sumByPaymentId(p.getId());
            return new PaymentResponse(
                    p.getId(), p.getBill().getId(), p.getParticipantName(), p.getAmount(),
                    p.getMethod().name(), p.getStatus().name(), p.getCreatedAt(),
                    refunded, p.getAmount().subtract(refunded));
        }).toList();
    }

    public List<RefundResponse> listRefunds(Long paymentId) {
        List<Refund> refunds = refundRepository.findByPaymentId(paymentId);
        Set<String> userIds = refunds.stream().map(Refund::getRefundedBy).collect(Collectors.toSet());
        Map<String, String> names = userIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(userIds).stream()
                        .collect(Collectors.toMap(User::getId, User::getName));
        return refunds.stream()
                .map(r -> new RefundResponse(
                        r.getId(), r.getAmount(), r.getReason(),
                        names.getOrDefault(r.getRefundedBy(), r.getRefundedBy()), r.getCreatedAt()))
                .toList();
    }

    private String resolveUserId(String email) {
        return userRepository.findByEmail(email)
                .map(User::getId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }
}
