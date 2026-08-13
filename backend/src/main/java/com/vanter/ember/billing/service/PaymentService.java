package com.vanter.ember.billing.service;

import com.vanter.ember.billing.event.PaymentCompleted;
import com.vanter.ember.billing.model.Bill;
import com.vanter.ember.billing.model.BillSplit;
import com.vanter.ember.billing.model.Payment;
import com.vanter.ember.billing.model.PaymentMethod;
import com.vanter.ember.billing.model.PaymentStatus;
import com.vanter.ember.billing.repository.BillRepository;
import com.vanter.ember.billing.repository.BillSplitRepository;
import com.vanter.ember.billing.repository.PaymentRepository;
import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.session.service.SessionService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final BillRepository billRepository;
    private final BillSplitRepository billSplitRepository;
    private final PaymentRepository paymentRepository;
    private final SessionService sessionService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Payment registerPhysicalPayment(Long billId, String participantName, BigDecimal amount) {
        Bill bill = billRepository.findById(billId)
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found: " + billId));

        BillSplit split = billSplitRepository.findByBillIdAndParticipantName(billId, participantName)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Split not found for participant: " + participantName));

        if (amount.compareTo(split.getAmount()) != 0) {
            throw new IllegalArgumentException(
                    "Payment amount " + amount + " does not match split amount " + split.getAmount());
        }

        split.setPaid(true);
        billSplitRepository.save(split);

        Payment payment = paymentRepository.save(Payment.builder()
                .bill(bill)
                .participantName(participantName)
                .amount(amount)
                .method(PaymentMethod.PHYSICAL)
                .status(PaymentStatus.CONFIRMED)
                .createdAt(LocalDateTime.now())
                .build());

        List<BillSplit> allSplits = billSplitRepository.findByBillId(billId);
        boolean allPaid = allSplits.stream().allMatch(BillSplit::isPaid);
        if (allPaid) {
            UUID tableId = sessionService.findById(bill.getSessionId()).getTableId();
            eventPublisher.publishEvent(new PaymentCompleted(bill.getSessionId(), tableId, billId));
        }

        return payment;
    }

    @Transactional
    public Payment initiateDigitalPayment(Long billId, String participantName, BigDecimal amount) {
        Bill bill = billRepository.findById(billId)
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found: " + billId));

        BillSplit split = billSplitRepository.findByBillIdAndParticipantName(billId, participantName)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Split not found for participant: " + participantName));

        if (amount.compareTo(split.getAmount()) != 0) {
            throw new IllegalArgumentException(
                    "Payment amount " + amount + " does not match split amount " + split.getAmount());
        }

        return paymentRepository.save(Payment.builder()
                .bill(bill)
                .participantName(participantName)
                .amount(amount)
                .method(PaymentMethod.DIGITAL)
                .status(PaymentStatus.PENDING)
                .gatewayRef("STUB-" + UUID.randomUUID())
                .createdAt(LocalDateTime.now())
                .build());
    }

    @Transactional
    public Payment confirmDigitalPayment(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));

        BillSplit split = billSplitRepository
                .findByBillIdAndParticipantName(payment.getBill().getId(), payment.getParticipantName())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Split not found for participant: " + payment.getParticipantName()));

        split.setPaid(true);
        billSplitRepository.save(split);

        payment.setStatus(PaymentStatus.CONFIRMED);
        Payment saved = paymentRepository.save(payment);

        List<BillSplit> allSplits = billSplitRepository.findByBillId(payment.getBill().getId());
        boolean allPaid = allSplits.stream().allMatch(BillSplit::isPaid);
        if (allPaid) {
            UUID tableId = sessionService.findById(payment.getBill().getSessionId()).getTableId();
            eventPublisher.publishEvent(
                    new PaymentCompleted(payment.getBill().getSessionId(), tableId,
                            payment.getBill().getId()));
        }

        return saved;
    }
}
