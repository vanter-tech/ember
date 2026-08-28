package com.vanter.ember.cashregister.service;

import com.vanter.ember.billing.dto.PaymentResponse;
import com.vanter.ember.billing.model.Payment;
import com.vanter.ember.billing.repository.PaymentRepository;
import com.vanter.ember.billing.service.PaymentService;
import com.vanter.ember.cashregister.dto.CashMovementResponse;
import com.vanter.ember.cashregister.dto.CashShiftDetailResponse;
import com.vanter.ember.cashregister.dto.CashShiftResponse;
import com.vanter.ember.cashregister.dto.DailyReportResponse;
import com.vanter.ember.cashregister.event.CashMovementRecorded;
import com.vanter.ember.cashregister.event.CashShiftClosed;
import com.vanter.ember.cashregister.event.CashShiftOpened;
import com.vanter.ember.cashregister.event.CashShiftProlonged;
import com.vanter.ember.cashregister.exception.CashShiftOverdueException;
import com.vanter.ember.cashregister.model.CashMovement;
import com.vanter.ember.cashregister.model.CashMovementType;
import com.vanter.ember.cashregister.model.CashShift;
import com.vanter.ember.cashregister.model.CashShiftStatus;
import com.vanter.ember.cashregister.repository.CashMovementRepository;
import com.vanter.ember.cashregister.repository.CashShiftRepository;
import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.session.model.SessionStatus;
import com.vanter.ember.session.repository.SessionRepository;
import com.vanter.ember.settings.service.SettingService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CashShiftService {

    private static final LocalDateTime EPOCH_FLOOR = LocalDateTime.of(1970, 1, 1, 0, 0);

    private final CashShiftRepository cashShiftRepository;
    private final CashMovementRepository cashMovementRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PaymentService paymentService;
    private final SettingService settingService;
    private final CashShiftDeadlineService deadlineService;

    @Transactional
    public CashShift openShift(UUID tenantId, String openedByUserId, BigDecimal openingFloat) {
        if (cashShiftRepository.findByTenantIdAndStatus(tenantId, CashShiftStatus.OPEN).isPresent()) {
            throw new IllegalStateException("A cash shift is already open for this tenant");
        }

        int nextShiftNumber = cashShiftRepository.findMaxShiftNumber(tenantId) + 1;

        var businessHours = settingService.getSettings(tenantId).getPayload().getBusinessHours();
        LocalDateTime openedAt = LocalDateTime.now();
        LocalDateTime expiresAt = deadlineService.computeExpiresAt(openedAt, businessHours);

        CashShift shift;
        try {
            shift = cashShiftRepository.save(CashShift.builder()
                    .shiftNumber(nextShiftNumber)
                    .status(CashShiftStatus.OPEN)
                    .openingFloat(openingFloat)
                    .openedBy(openedByUserId)
                    .openedAt(openedAt)
                    .expiresAt(expiresAt)
                    .build());
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalStateException("A cash shift is already open for this tenant");
        }

        eventPublisher.publishEvent(new CashShiftOpened(tenantId, shift.getId()));
        return shift;
    }

    @Transactional
    public CashMovement recordMovement(
            Long shiftId, String createdByUserId, CashMovementType type, BigDecimal amount, String reason) {
        CashShift shift = cashShiftRepository.findByIdForUpdate(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Cash shift not found: " + shiftId));
        if (shift.getStatus() != CashShiftStatus.OPEN) {
            throw new IllegalStateException("Cash shift is not open: " + shiftId);
        }
        if (deadlineService.isOverdue(shift, LocalDateTime.now())) {
            throw new CashShiftOverdueException(
                    "Cash shift " + shiftId + " is overdue; prolong or close it before recording a movement");
        }

        CashMovement movement = cashMovementRepository.save(CashMovement.builder()
                .cashShiftId(shiftId)
                .type(type)
                .amount(amount)
                .reason(reason)
                .createdBy(createdByUserId)
                .createdAt(LocalDateTime.now())
                .build());

        eventPublisher.publishEvent(new CashMovementRecorded(shift.getTenantId(), shiftId));
        return movement;
    }

    @Transactional
    public CashShift prolongShift(Long shiftId, String userId) {
        CashShift shift = cashShiftRepository.findByIdForUpdate(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Cash shift not found: " + shiftId));
        if (shift.getStatus() != CashShiftStatus.OPEN) {
            throw new IllegalStateException("Cash shift is not open: " + shiftId);
        }
        shift.setProlongedUntil(deadlineService.prolong(shift, LocalDateTime.now()));
        shift.setProlongedBy(userId);
        shift.setProlongCount(shift.getProlongCount() + 1);
        CashShift saved = cashShiftRepository.save(shift);
        eventPublisher.publishEvent(new CashShiftProlonged(shift.getTenantId(), shiftId));
        return saved;
    }

    @Transactional
    public CashShift closeShift(Long shiftId, String closedByUserId, BigDecimal countedCash) {
        CashShift shift = cashShiftRepository.findByIdForUpdate(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Cash shift not found: " + shiftId));
        if (shift.getStatus() != CashShiftStatus.OPEN) {
            throw new IllegalStateException("Cash shift is not open: " + shiftId);
        }

        long activeTables = sessionRepository.countByTenantIdAndStatus(shift.getTenantId(), SessionStatus.OPEN);
        if (activeTables > 0) {
            throw new IllegalStateException(
                    "Cannot close cash shift: " + activeTables + " table(s) still have an open session");
        }

        BigDecimal cashIn = cashMovementRepository.sumCashIn(shiftId);
        BigDecimal cashOut = cashMovementRepository.sumCashOut(shiftId);
        BigDecimal cashSales = paymentRepository.sumConfirmedPhysicalForShift(shift.getTenantId(), shiftId);
        LocalDateTime closedAt = LocalDateTime.now();
        BigDecimal digitalSales = paymentRepository.sumConfirmedDigitalInWindow(
                shift.getTenantId(), shift.getOpenedAt(), closedAt);

        BigDecimal expectedCash = shift.getOpeningFloat().add(cashIn).subtract(cashOut).add(cashSales);
        BigDecimal variance = countedCash.subtract(expectedCash);

        shift.setStatus(CashShiftStatus.CLOSED);
        shift.setClosedBy(closedByUserId);
        shift.setClosedAt(closedAt);
        shift.setExpectedCash(expectedCash);
        shift.setCountedCash(countedCash);
        shift.setVariance(variance);
        shift.setTotalCashSales(cashSales);
        shift.setTotalDigitalSales(digitalSales);
        shift.setTotalCashIn(cashIn);
        shift.setTotalCashOut(cashOut);

        CashShift saved = cashShiftRepository.save(shift);
        eventPublisher.publishEvent(new CashShiftClosed(shift.getTenantId(), shiftId));
        return saved;
    }

    public CashShift getCurrentOpenShift(UUID tenantId) {
        return cashShiftRepository.findByTenantIdAndStatus(tenantId, CashShiftStatus.OPEN)
                .orElseThrow(() -> new ResourceNotFoundException("No open cash shift for this tenant"));
    }

    public CashShift getById(Long id) {
        return cashShiftRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cash shift not found: " + id));
    }

    public CashShiftDetailResponse getDetail(Long id) {
        CashShift shift = getById(id);
        List<CashMovement> movements = cashMovementRepository.findByCashShiftIdOrderByCreatedAtAsc(id);
        List<Payment> payments = paymentRepository.findByCashShiftId(id);

        Set<String> userIds = new HashSet<>();
        userIds.add(shift.getOpenedBy());
        if (shift.getClosedBy() != null) userIds.add(shift.getClosedBy());
        movements.forEach(m -> userIds.add(m.getCreatedBy()));
        Map<String, String> names = resolveNames(userIds);

        List<CashMovementResponse> movementResponses =
                movements.stream().map(m -> toMovementResponse(m, names)).toList();
        List<PaymentResponse> paymentResponses = paymentService.toResponses(payments);

        return new CashShiftDetailResponse(toResponse(shift, names), movementResponses, paymentResponses);
    }

    public Page<CashShift> getHistory(UUID tenantId, LocalDateTime from, LocalDateTime to, Pageable pageable) {
        LocalDateTime resolvedFrom = from != null ? from : EPOCH_FLOOR;
        LocalDateTime resolvedTo = to != null ? to : LocalDateTime.now();
        return cashShiftRepository.findByTenantIdAndOpenedAtBetweenOrderByOpenedAtDesc(
                tenantId, resolvedFrom, resolvedTo, pageable);
    }

    public DailyReportResponse getDailyReport(UUID tenantId, LocalDate date) {
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.plusDays(1).atStartOfDay().minusNanos(1);
        List<CashShift> shifts = cashShiftRepository
                .findByTenantIdAndStatusAndClosedAtBetween(tenantId, CashShiftStatus.CLOSED, from, to);

        return new DailyReportResponse(
                date,
                sumField(shifts, CashShift::getTotalCashSales),
                sumField(shifts, CashShift::getTotalDigitalSales),
                sumField(shifts, CashShift::getVariance),
                sumField(shifts, CashShift::getTotalCashIn),
                sumField(shifts, CashShift::getTotalCashOut),
                shifts.stream().map(this::toResponse).toList());
    }

    public CashShiftResponse toResponse(CashShift shift) {
        Set<String> userIds = new HashSet<>();
        userIds.add(shift.getOpenedBy());
        if (shift.getClosedBy() != null) userIds.add(shift.getClosedBy());
        return toResponse(shift, resolveNames(userIds));
    }

    public CashMovementResponse toMovementResponse(CashMovement movement) {
        return toMovementResponse(movement, resolveNames(Set.of(movement.getCreatedBy())));
    }

    private CashShiftResponse toResponse(CashShift shift, Map<String, String> names) {
        return new CashShiftResponse(
                shift.getId(), shift.getShiftNumber(), shift.getStatus().name(), shift.getOpeningFloat(),
                names.getOrDefault(shift.getOpenedBy(), shift.getOpenedBy()), shift.getOpenedAt(),
                shift.getClosedBy() == null ? null : names.getOrDefault(shift.getClosedBy(), shift.getClosedBy()),
                shift.getClosedAt(), shift.getExpectedCash(), shift.getCountedCash(), shift.getVariance(),
                shift.getTotalCashSales(), shift.getTotalDigitalSales(), shift.getTotalCashIn(),
                shift.getTotalCashOut(),
                shift.getExpiresAt(), shift.effectiveDeadline(),
                deadlineService.isOverdue(shift, LocalDateTime.now()),
                shift.businessDay(), shift.getProlongCount());
    }

    private CashMovementResponse toMovementResponse(CashMovement movement, Map<String, String> names) {
        return new CashMovementResponse(
                movement.getId(), movement.getType().name(), movement.getAmount(), movement.getReason(),
                names.getOrDefault(movement.getCreatedBy(), movement.getCreatedBy()), movement.getCreatedAt());
    }

    private Map<String, String> resolveNames(Set<String> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getName));
    }

    private BigDecimal sumField(List<CashShift> shifts, Function<CashShift, BigDecimal> extractor) {
        return shifts.stream().map(extractor).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
