package com.vanter.ember.billing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.vanter.ember.billing.dto.ShiftRefundResponse;
import com.vanter.ember.billing.dto.VoidedBillResponse;
import com.vanter.ember.billing.model.Bill;
import com.vanter.ember.billing.model.Payment;
import com.vanter.ember.billing.model.Refund;
import com.vanter.ember.billing.repository.BillRepository;
import com.vanter.ember.billing.repository.RefundRepository;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BillingAuditServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final LocalDateTime AT = LocalDateTime.of(2026, 10, 2, 14, 30);

    @Mock RefundRepository refundRepository;
    @Mock BillRepository billRepository;
    @Mock UserRepository userRepository;
    @Mock PaymentService paymentService;
    @InjectMocks BillingAuditService service;

    private static User user(String id, String name) {
        return User.builder().id(id).name(name).build();
    }

    @Test
    void refundsOf_noPayments_queriesNothing() {
        assertThat(service.refundsOf(List.of())).isEmpty();

        verifyNoInteractions(refundRepository, userRepository);
    }

    @Test
    void refundsOf_carriesReasonWhoAndTheBillItBelongsTo() {
        Bill bill = Bill.builder().id(7L).billCode("ELPO-000007").build();
        Payment payment = Payment.builder().id(20L).bill(bill).participantName("Alice").build();
        Refund refund = Refund.builder().id(5L).payment(payment).amount(new BigDecimal("2.00"))
                .reason("cold food").refundedBy("u-2").createdAt(AT).build();
        when(refundRepository.findByPaymentIdIn(List.of(20L))).thenReturn(List.of(refund));
        when(userRepository.findAllById(List.of("u-2"))).thenReturn(List.of(user("u-2", "Carla")));

        List<ShiftRefundResponse> out = service.refundsOf(List.of(payment));

        assertThat(out).singleElement().satisfies(r -> {
            assertThat(r.id()).isEqualTo(5L);
            assertThat(r.paymentId()).isEqualTo(20L);
            assertThat(r.billId()).isEqualTo(7L);
            assertThat(r.billCode()).isEqualTo("ELPO-000007");
            assertThat(r.participantName()).isEqualTo("Alice");
            assertThat(r.amount()).isEqualByComparingTo("2.00");
            assertThat(r.reason()).isEqualTo("cold food");
            assertThat(r.refundedByName()).isEqualTo("Carla");
            assertThat(r.createdAt()).isEqualTo(AT);
        });
    }

    @Test
    void refundsOf_unknownUser_fallsBackToTheStoredId() {
        Bill bill = Bill.builder().id(7L).build();
        Payment payment = Payment.builder().id(20L).bill(bill).participantName("Alice").build();
        Refund refund = Refund.builder().id(5L).payment(payment).amount(BigDecimal.ONE)
                .reason("x").refundedBy("gone-user").createdAt(AT).build();
        when(refundRepository.findByPaymentIdIn(List.of(20L))).thenReturn(List.of(refund));
        when(userRepository.findAllById(List.of("gone-user"))).thenReturn(List.of());

        assertThat(service.refundsOf(List.of(payment)).get(0).refundedByName()).isEqualTo("gone-user");
    }

    @Test
    void voidedBetween_noVoids_resolvesNoUsersOrTables() {
        when(billRepository.findVoidedBetween(TENANT_ID, AT, AT.plusHours(1))).thenReturn(List.of());

        assertThat(service.voidedBetween(TENANT_ID, AT, AT.plusHours(1))).isEmpty();

        verifyNoInteractions(userRepository, paymentService);
    }

    @Test
    void voidedBetween_carriesReasonWhoWhenAndTable() {
        Bill voided = Bill.builder().id(9L).billCode("ELPO-000009").sessionId("sess-1")
                .total(new BigDecimal("30.00")).voidedBy("u-1").voidedAt(AT).voidReason("wrong table").build();
        when(billRepository.findVoidedBetween(TENANT_ID, AT, AT.plusHours(1))).thenReturn(List.of(voided));
        when(userRepository.findAllById(List.of("u-1"))).thenReturn(List.of(user("u-1", "Ana")));
        when(paymentService.tablesBySession(any(), any()))
                .thenReturn(Map.of("sess-1", new PaymentService.TableRef(5, "M5+M6")));

        List<VoidedBillResponse> out = service.voidedBetween(TENANT_ID, AT, AT.plusHours(1));

        assertThat(out).singleElement().satisfies(v -> {
            assertThat(v.id()).isEqualTo(9L);
            assertThat(v.billCode()).isEqualTo("ELPO-000009");
            assertThat(v.total()).isEqualByComparingTo("30.00");
            assertThat(v.tableNumber()).isEqualTo(5);
            assertThat(v.tableLabel()).isEqualTo("M5+M6");
            assertThat(v.voidReason()).isEqualTo("wrong table");
            assertThat(v.voidedByName()).isEqualTo("Ana");
            assertThat(v.voidedAt()).isEqualTo(AT);
        });
    }

    @Test
    void voidedBetween_billWhoseSessionIsGone_hasNoTable() {
        Bill voided = Bill.builder().id(9L).sessionId("sess-gone").total(BigDecimal.TEN)
                .voidedBy("u-1").voidedAt(AT).voidReason("x").build();
        when(billRepository.findVoidedBetween(TENANT_ID, AT, AT.plusHours(1))).thenReturn(List.of(voided));
        when(userRepository.findAllById(List.of("u-1"))).thenReturn(List.of(user("u-1", "Ana")));
        when(paymentService.tablesBySession(any(), any())).thenReturn(Map.of());

        VoidedBillResponse out = service.voidedBetween(TENANT_ID, AT, AT.plusHours(1)).get(0);

        assertThat(out.tableNumber()).isNull();
        assertThat(out.tableLabel()).isNull();
    }
}
