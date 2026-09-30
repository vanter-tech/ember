package com.vanter.ember.cashregister.service;

import com.vanter.ember.billing.model.BillStatus;
import com.vanter.ember.billing.model.Payment;
import com.vanter.ember.billing.repository.BillRepository;
import com.vanter.ember.billing.repository.PaymentRepository;
import com.vanter.ember.cashregister.dto.CashDrawerEventResponse;
import com.vanter.ember.cashregister.dto.CashReceiptStatusResponse;
import com.vanter.ember.cashregister.model.CashDrawerEvent;
import com.vanter.ember.cashregister.model.CashDrawerEventStatus;
import com.vanter.ember.cashregister.model.CashDrawerEventType;
import com.vanter.ember.cashregister.model.CashShift;
import com.vanter.ember.cashregister.repository.CashDrawerEventRepository;
import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.printing.model.DrawerKickState;
import com.vanter.ember.printing.model.PrintJob;
import com.vanter.ember.printing.service.CashDrawerKickService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CashDrawerService {

    private final CashDrawerEventRepository repository;
    private final CashDrawerKickService kickService;
    private final CashShiftService cashShiftService;
    private final UserRepository userRepository;
    private final BillRepository billRepository;
    private final PaymentRepository paymentRepository;

    @Transactional(readOnly = true)
    public List<CashDrawerEventResponse> listForPanel(UUID tenantId) {
        Long shiftId = cashShiftService.findCurrentOpenShift(tenantId).map(CashShift::getId).orElse(null);
        return repository.findForPanel(tenantId, shiftId).stream().map(this::toResponse).toList();
    }

    /**
     * Accepts a pending cash receipt and opens the drawer. Also retries: a RECEIVED event whose last
     * kick FAILED gets a new kick job (the original receiver is kept). Anything else is rejected, so
     * a double click can never open the drawer twice for the same cash.
     */
    @Transactional
    public CashDrawerEventResponse receive(UUID tenantId, UUID eventId, String userId) {
        CashDrawerEvent event = repository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Cash drawer event not found: " + eventId));
        if (event.getStatus() == CashDrawerEventStatus.PENDING) {
            event.setStatus(CashDrawerEventStatus.RECEIVED);
            event.setReceivedBy(userId);
            event.setReceivedAt(LocalDateTime.now());
            // Flush BEFORE kicking: with @Version, a concurrent second click fails here on the stale
            // version instead of after the pulse was already sent (two pulses for the same cash).
            repository.saveAndFlush(event);
        } else if (event.getDrawerSkippedAt() != null
                || kickService.stateOf(event.getPrintJobId()) != DrawerKickState.FAILED) {
            throw new IllegalStateException("Cash drawer event already received: " + eventId);
        }
        PrintJob job = kickService.kick(tenantId, event.getId().toString());
        event.setPrintJobId(job.getId());
        return toResponse(repository.save(event));
    }

    /**
     * Dismisses a failed drawer opening: the cash is already received, so a busy shift must not be
     * held up by it. Only a RECEIVED sale whose last kick FAILED can be skipped; who/when is kept.
     */
    @Transactional
    public CashDrawerEventResponse skipDrawer(UUID eventId, String userId) {
        CashDrawerEvent event = repository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Cash drawer event not found: " + eventId));
        if (event.getStatus() != CashDrawerEventStatus.RECEIVED
                || event.getDrawerSkippedAt() != null
                || kickService.stateOf(event.getPrintJobId()) != DrawerKickState.FAILED) {
            throw new IllegalStateException("Only a received sale with a failed drawer opening can be skipped: " + eventId);
        }
        event.setDrawerSkippedAt(LocalDateTime.now());
        event.setDrawerSkippedBy(userId);
        return toResponse(repository.save(event));
    }

    /** ACCOUNTANT needs an open shift ({@code requireOpenShift}); ADMIN may open without one. */
    @Transactional
    public CashDrawerEventResponse manualOpen(
            UUID tenantId, String userId, boolean requireOpenShift, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required to open the cash drawer manually");
        }
        Optional<CashShift> shift = cashShiftService.findCurrentOpenShift(tenantId);
        if (requireOpenShift && shift.isEmpty()) {
            throw new IllegalStateException("No open cash shift; open one before opening the drawer");
        }
        LocalDateTime now = LocalDateTime.now();
        CashDrawerEvent event = CashDrawerEvent.builder()
                .id(UUID.randomUUID())
                .type(CashDrawerEventType.MANUAL)
                .status(CashDrawerEventStatus.RECEIVED)
                .cashShiftId(shift.map(CashShift::getId).orElse(null))
                .reason(reason.trim())
                .createdBy(userId)
                .receivedBy(userId)
                .receivedAt(now)
                .createdAt(now)
                .build();
        PrintJob job = kickService.kick(tenantId, event.getId().toString());
        event.setPrintJobId(job.getId());
        return toResponse(repository.save(event));
    }

    private CashDrawerEventResponse toResponse(CashDrawerEvent e) {
        DrawerKickState state = kickService.stateOf(e.getPrintJobId());
        boolean skipped = e.getDrawerSkippedAt() != null;
        String drawer = skipped ? "SKIPPED" : state.name();
        String error = !skipped && state == DrawerKickState.FAILED ? kickService.errorOf(e.getPrintJobId()) : null;
        return new CashDrawerEventResponse(
                e.getId(), e.getType().name(), e.getStatus().name(), e.getTableNumber(), e.getAmount(),
                e.getReason(), e.getCreatedAt(), e.getReceivedAt(), drawer, creatorName(e.getCreatedBy()), error);
    }

    private String creatorName(String userId) {
        if (userId == null) return null;
        return userRepository.findById(userId).map(User::getName).orElse(null);
    }

    /** Cash-drawer state of every physical payment of a table's live bill, so the waiter sees if the accountant has it. */
    @Transactional(readOnly = true)
    public List<CashReceiptStatusResponse> statusForSession(String sessionId) {
        return billRepository.findBySessionIdAndStatusNot(sessionId, BillStatus.VOIDED)
                .map(bill -> {
                    Map<Long, Payment> payments = paymentRepository.findByBillId(bill.getId()).stream()
                            .collect(Collectors.toMap(Payment::getId, Function.identity()));
                    return repository.findByPaymentIdIn(payments.keySet()).stream()
                            .map(e -> new CashReceiptStatusResponse(
                                    payments.get(e.getPaymentId()).getParticipantName(), e.getStatus().name()))
                            .toList();
                })
                .orElse(List.of());
    }
}
