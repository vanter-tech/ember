package com.vanter.ember.billing.service;

import com.vanter.ember.billing.dto.ShiftRefundResponse;
import com.vanter.ember.billing.dto.VoidedBillResponse;
import com.vanter.ember.billing.model.Bill;
import com.vanter.ember.billing.model.Payment;
import com.vanter.ember.billing.model.Refund;
import com.vanter.ember.billing.repository.BillRepository;
import com.vanter.ember.billing.repository.RefundRepository;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Read side of the audit trail the billing module already records — refund reasons and voided
 * bills — shaped for a shift's close-out view. Nothing here writes.
 */
@Service
@RequiredArgsConstructor
public class BillingAuditService {

    private final RefundRepository refundRepository;
    private final BillRepository billRepository;
    private final UserRepository userRepository;
    private final PaymentService paymentService;

    /** Refunds of the given payments, oldest first. A refund belongs to the shift its payment was taken in. */
    public List<ShiftRefundResponse> refundsOf(List<Payment> payments) {
        if (payments.isEmpty()) {
            return List.of();
        }
        List<Refund> refunds = refundRepository.findByPaymentIdIn(payments.stream().map(Payment::getId).toList());
        Map<String, String> names = userNames(refunds.stream().map(Refund::getRefundedBy));
        return refunds.stream()
                .map(r -> new ShiftRefundResponse(
                        r.getId(), r.getPayment().getId(), r.getPayment().getBill().getId(),
                        r.getPayment().getBill().getBillCode(), r.getPayment().getParticipantName(),
                        r.getAmount(), r.getReason(),
                        names.getOrDefault(r.getRefundedBy(), r.getRefundedBy()), r.getCreatedAt()))
                .toList();
    }

    /** Bills voided between {@code from} and {@code to} (inclusive), oldest first, with table and who/why. */
    public List<VoidedBillResponse> voidedBetween(UUID tenantId, LocalDateTime from, LocalDateTime to) {
        List<Bill> bills = billRepository.findVoidedBetween(tenantId, from, to);
        if (bills.isEmpty()) {
            return List.of();
        }
        Map<String, String> names = userNames(bills.stream().map(Bill::getVoidedBy));
        Map<String, PaymentService.TableRef> tables = paymentService.tablesBySession(
                tenantId, bills.stream().map(Bill::getSessionId).toList());
        return bills.stream()
                .map(b -> {
                    PaymentService.TableRef table = tables.get(b.getSessionId());
                    return new VoidedBillResponse(
                            b.getId(), b.getBillCode(), b.getTotal(),
                            table == null ? null : table.number(), table == null ? null : table.label(),
                            b.getVoidReason(), names.getOrDefault(b.getVoidedBy(), b.getVoidedBy()), b.getVoidedAt());
                })
                .toList();
    }

    private Map<String, String> userNames(Stream<String> userIds) {
        List<String> ids = userIds.filter(java.util.Objects::nonNull).distinct().toList();
        return ids.isEmpty()
                ? Map.of()
                : userRepository.findAllById(ids).stream().collect(Collectors.toMap(User::getId, User::getName));
    }
}
