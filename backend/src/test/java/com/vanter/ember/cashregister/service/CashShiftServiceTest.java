package com.vanter.ember.cashregister.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.vanter.ember.billing.dto.PaymentResponse;
import com.vanter.ember.billing.model.Payment;
import com.vanter.ember.billing.service.PaymentService;
import com.vanter.ember.cashregister.dto.CashShiftDetailResponse;
import com.vanter.ember.cashregister.dto.CashShiftResponse;
import com.vanter.ember.cashregister.event.CashShiftProlonged;
import com.vanter.ember.cashregister.exception.CashShiftOverdueException;
import com.vanter.ember.cashregister.model.CashMovement;
import com.vanter.ember.cashregister.model.CashMovementType;
import com.vanter.ember.cashregister.model.CashShift;
import com.vanter.ember.cashregister.model.CashShiftStatus;
import com.vanter.ember.cashregister.repository.CashMovementRepository;
import com.vanter.ember.cashregister.repository.CashShiftRepository;
import com.vanter.ember.billing.repository.PaymentRepository;
import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.session.model.SessionStatus;
import com.vanter.ember.session.repository.SessionRepository;
import com.vanter.ember.settings.model.RestaurantSettings;
import com.vanter.ember.settings.model.SettingsPayload;
import com.vanter.ember.settings.service.SettingService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class CashShiftServiceTest {

    @Mock CashShiftRepository cashShiftRepository;
    @Mock CashMovementRepository cashMovementRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock UserRepository userRepository;
    @Mock SessionRepository sessionRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock PaymentService paymentService;
    @Mock SettingService settingService;
    @Mock CashShiftDeadlineService deadlineService;
    @InjectMocks CashShiftService cashShiftService;

    private static final UUID TENANT_ID = UUID.randomUUID();

    private CashShift openShift() {
        return CashShift.builder()
                .id(1L).tenantId(TENANT_ID).shiftNumber(3).status(CashShiftStatus.OPEN)
                .openingFloat(new BigDecimal("100.00")).openedBy("user-1")
                .openedAt(LocalDateTime.now().minusHours(2)).build();
    }

    private RestaurantSettings settingsWithPayload() {
        RestaurantSettings settings = new RestaurantSettings();
        settings.setPayload(new SettingsPayload());
        return settings;
    }

    @Test
    void openShift_createsShiftWithNextSequentialNumberAndPublishesEvent() {
        when(cashShiftRepository.findByTenantIdAndStatus(TENANT_ID, CashShiftStatus.OPEN))
                .thenReturn(Optional.empty());
        when(cashShiftRepository.findMaxShiftNumber(TENANT_ID)).thenReturn(4);
        when(settingService.getSettings(TENANT_ID)).thenReturn(settingsWithPayload());
        when(cashShiftRepository.save(any())).thenAnswer(inv -> {
            CashShift toSave = inv.getArgument(0);
            toSave.setId(9L);
            return toSave;
        });

        CashShift shift = cashShiftService.openShift(TENANT_ID, "user-1", new BigDecimal("50.00"));

        assertThat(shift.getShiftNumber()).isEqualTo(5);
        assertThat(shift.getStatus()).isEqualTo(CashShiftStatus.OPEN);
        assertThat(shift.getOpenedBy()).isEqualTo("user-1");
    }

    @Test
    void openShift_throwsWhenAShiftIsAlreadyOpen() {
        when(cashShiftRepository.findByTenantIdAndStatus(TENANT_ID, CashShiftStatus.OPEN))
                .thenReturn(Optional.of(openShift()));

        assertThatThrownBy(() -> cashShiftService.openShift(TENANT_ID, "user-1", new BigDecimal("50.00")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void recordMovement_throwsWhenShiftIsNotOpen() {
        CashShift closed = openShift();
        closed.setStatus(CashShiftStatus.CLOSED);
        when(cashShiftRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(closed));

        assertThatThrownBy(() -> cashShiftService.recordMovement(
                1L, "user-1", CashMovementType.CASH_OUT, new BigDecimal("10.00"), "safe drop"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void recordMovement_savesAndPublishesEvent() {
        CashShift shift = openShift();
        when(cashShiftRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(shift));
        when(cashMovementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CashMovement movement = cashShiftService.recordMovement(
                1L, "user-1", CashMovementType.CASH_OUT, new BigDecimal("10.00"), "safe drop");

        assertThat(movement.getCashShiftId()).isEqualTo(1L);
        assertThat(movement.getType()).isEqualTo(CashMovementType.CASH_OUT);
    }

    @Test
    void closeShift_computesExpectedCashAndVariance() {
        CashShift shift = openShift();
        when(cashShiftRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(shift));
        when(sessionRepository.countByTenantIdAndStatus(TENANT_ID, SessionStatus.OPEN)).thenReturn(0L);
        when(cashMovementRepository.sumCashIn(1L)).thenReturn(new BigDecimal("20.00"));
        when(cashMovementRepository.sumCashOut(1L)).thenReturn(new BigDecimal("5.00"));
        when(paymentRepository.sumConfirmedPhysicalForShift(TENANT_ID, 1L))
                .thenReturn(new BigDecimal("150.00"));
        when(paymentRepository.sumConfirmedDigitalInWindow(any(), any(), any()))
                .thenReturn(new BigDecimal("40.00"));
        when(cashShiftRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CashShift closed = cashShiftService.closeShift(1L, "user-2", new BigDecimal("260.00"));

        // expected = 100 (float) + 20 (in) - 5 (out) + 150 (cash sales) = 265
        assertThat(closed.getExpectedCash()).isEqualByComparingTo("265.00");
        assertThat(closed.getCountedCash()).isEqualByComparingTo("260.00");
        assertThat(closed.getVariance()).isEqualByComparingTo("-5.00");
        assertThat(closed.getTotalDigitalSales()).isEqualByComparingTo("40.00");
        assertThat(closed.getStatus()).isEqualTo(CashShiftStatus.CLOSED);
        assertThat(closed.getClosedBy()).isEqualTo("user-2");
    }

    @Test
    void closeShift_throwsWhenTablesStillHaveOpenSessions() {
        CashShift shift = openShift();
        when(cashShiftRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(shift));
        when(sessionRepository.countByTenantIdAndStatus(TENANT_ID, SessionStatus.OPEN)).thenReturn(3L);

        assertThatThrownBy(() -> cashShiftService.closeShift(1L, "user-2", new BigDecimal("260.00")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void closeShift_throwsWhenShiftIsNotOpen() {
        CashShift closed = openShift();
        closed.setStatus(CashShiftStatus.CLOSED);
        when(cashShiftRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(closed));

        assertThatThrownBy(() -> cashShiftService.closeShift(1L, "user-2", new BigDecimal("0.00")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void getCurrentOpenShift_throwsResourceNotFoundWhenNoneOpen() {
        when(cashShiftRepository.findByTenantIdAndStatus(TENANT_ID, CashShiftStatus.OPEN))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> cashShiftService.getCurrentOpenShift(TENANT_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getDetail_includesPaymentsForTheShift() {
        CashShift shift = openShift();
        when(cashShiftRepository.findById(1L)).thenReturn(Optional.of(shift));
        when(cashMovementRepository.findByCashShiftIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());
        when(paymentRepository.findByCashShiftId(1L)).thenReturn(List.of(mock(Payment.class)));
        PaymentResponse response = new PaymentResponse(
                20L, 1L, "Alice", new BigDecimal("25.00"), "PHYSICAL", "CONFIRMED",
                LocalDateTime.now(), BigDecimal.ZERO, new BigDecimal("25.00"));
        when(paymentService.toResponses(anyList())).thenReturn(List.of(response));
        when(userRepository.findAllById(any())).thenReturn(List.of());

        CashShiftDetailResponse detail = cashShiftService.getDetail(1L);

        assertThat(detail.payments()).hasSize(1);
        assertThat(detail.payments().get(0).participantName()).isEqualTo("Alice");
    }

    @Test
    void openShift_stampsExpiresAtFromDeadlineService() {
        when(cashShiftRepository.findByTenantIdAndStatus(TENANT_ID, CashShiftStatus.OPEN))
                .thenReturn(Optional.empty());
        when(cashShiftRepository.findMaxShiftNumber(TENANT_ID)).thenReturn(0);
        when(settingService.getSettings(TENANT_ID)).thenReturn(settingsWithPayload());
        LocalDateTime stamped = LocalDateTime.now().plusHours(9);
        when(deadlineService.computeExpiresAt(any(), any())).thenReturn(stamped);
        when(cashShiftRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CashShift shift = cashShiftService.openShift(TENANT_ID, "user-1", new BigDecimal("50.00"));

        assertThat(shift.getExpiresAt()).isEqualTo(stamped);
    }

    @Test
    void prolongShift_pushesDeadlineIncrementsCountAndPublishes() {
        CashShift open = openShift();
        open.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        when(cashShiftRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(open));
        when(cashShiftRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        LocalDateTime pushed = LocalDateTime.now().plusHours(1);
        when(deadlineService.prolong(any(), any())).thenReturn(pushed);

        CashShift result = cashShiftService.prolongShift(1L, "user-7");

        assertThat(result.getProlongedUntil()).isEqualTo(pushed);
        assertThat(result.getProlongedBy()).isEqualTo("user-7");
        assertThat(result.getProlongCount()).isEqualTo(1);
        org.mockito.Mockito.verify(eventPublisher)
                .publishEvent(any(CashShiftProlonged.class));
    }

    @Test
    void prolongShift_throwsWhenShiftNotOpen() {
        CashShift closed = openShift();
        closed.setStatus(CashShiftStatus.CLOSED);
        when(cashShiftRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(closed));

        assertThatThrownBy(() -> cashShiftService.prolongShift(1L, "user-7"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void recordMovement_throwsWhenShiftIsOverdue() {
        CashShift open = openShift();
        when(cashShiftRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(open));
        when(deadlineService.isOverdue(org.mockito.ArgumentMatchers.eq(open), any())).thenReturn(true);

        assertThatThrownBy(() -> cashShiftService.recordMovement(
                1L, "user-1", CashMovementType.CASH_IN, new BigDecimal("10.00"), "tip"))
                .isInstanceOf(CashShiftOverdueException.class);
    }

    @Test
    void toResponse_fillsOverdueAndBusinessDay() {
        CashShift open = openShift();
        open.setExpiresAt(open.getOpenedAt().plusHours(9));
        when(deadlineService.isOverdue(org.mockito.ArgumentMatchers.eq(open), any())).thenReturn(true);
        when(userRepository.findAllById(any())).thenReturn(List.of());

        CashShiftResponse response = cashShiftService.toResponse(open);

        assertThat(response.overdue()).isTrue();
        assertThat(response.businessDay()).isEqualTo(open.getOpenedAt().toLocalDate());
        assertThat(response.effectiveDeadline()).isEqualTo(open.getExpiresAt());
    }
}
