package com.vanter.ember.billing.controller;

import com.vanter.ember.billing.dto.CalculateBillRequest;
import com.vanter.ember.billing.dto.DigitalPaymentRequest;
import com.vanter.ember.billing.dto.PaymentResponse;
import com.vanter.ember.billing.dto.PhysicalPaymentRequest;
import com.vanter.ember.billing.dto.RefundPaymentRequest;
import com.vanter.ember.billing.dto.RefundResponse;
import com.vanter.ember.billing.dto.RequestBillingRequest;
import com.vanter.ember.billing.dto.SplitBillRequest;
import com.vanter.ember.billing.dto.VoidBillRequest;
import com.vanter.ember.billing.event.BillingRequested;
import com.vanter.ember.billing.model.Bill;
import com.vanter.ember.billing.model.BillSplit;
import com.vanter.ember.billing.model.Payment;
import com.vanter.ember.billing.model.Refund;
import com.vanter.ember.billing.model.SplitMethod;
import com.vanter.ember.billing.service.BillingService;
import com.vanter.ember.billing.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Billing", description = "Bill calculation and payment processing")
@RestController
@RequestMapping("/billing")
@RequiredArgsConstructor
public class BillingController {

    private final BillingService billingService;
    private final PaymentService paymentService;
    private final ApplicationEventPublisher eventPublisher;

    @Operation(summary = "Calculate and split the bill for a session in one step, "
            + "broadcasting it to everyone on the session's WebSocket topic (WAITER)")
    @PostMapping("/sessions/{sessionId}/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasRole('WAITER')")
    public void requestBilling(@PathVariable String sessionId,
                                @Valid @RequestBody RequestBillingRequest request) {
        eventPublisher.publishEvent(new BillingRequested(
                sessionId, request.splitMethod(),
                request.participantCount() == null ? 0 : request.participantCount()));
    }

    @Operation(summary = "Calculate bill for a session (WAITER)")
    @PostMapping("/sessions/{sessionId}/bill")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('WAITER')")
    public Bill calculateBill(@PathVariable String sessionId,
                              @Valid @RequestBody CalculateBillRequest request) {
        return billingService.calculateBill(sessionId, request.splitMethod());
    }

    @Operation(summary = "Split a bill (WAITER)")
    @PostMapping("/bills/{id}/split")
    @PreAuthorize("hasRole('WAITER')")
    public List<BillSplit> splitBill(@PathVariable Long id,
                                     @Valid @RequestBody SplitBillRequest request) {
        if (request.splitMethod() == SplitMethod.BY_CONSUMPTION) {
            return billingService.splitByConsumption(id);
        }
        return billingService.splitEqually(id, request.participantCount());
    }

    @Operation(summary = "Register physical payment (WAITER)")
    @PostMapping("/payments/physical")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('WAITER')")
    public Payment registerPhysicalPayment(
            @Valid @RequestBody PhysicalPaymentRequest request, Authentication authentication) {
        return paymentService.registerPhysicalPayment(
                request.billId(), request.participantName(), request.amount(), authentication.getName());
    }

    @Operation(summary = "Initiate digital payment (WAITER/CUSTOMER)")
    @PostMapping("/payments/digital")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('WAITER','CUSTOMER')")
    public Payment initiateDigitalPayment(
            @Valid @RequestBody DigitalPaymentRequest request, Authentication authentication) {
        return paymentService.initiateDigitalPayment(
                request.billId(), request.participantName(), request.amount(), authentication.getName());
    }

    @Operation(summary = "Confirm digital payment (WAITER)")
    @PostMapping("/payments/{id}/confirm")
    @PreAuthorize("hasRole('WAITER')")
    public Payment confirmDigitalPayment(@PathVariable Long id) {
        return paymentService.confirmDigitalPayment(id);
    }

    @Operation(summary = "Void a bill before any payment lands (WAITER)")
    @PostMapping("/bills/{id}/void")
    @PreAuthorize("hasRole('WAITER')")
    public Bill voidBill(
            @PathVariable Long id, @Valid @RequestBody VoidBillRequest request, Authentication authentication) {
        return billingService.voidBill(id, request.reason(), authentication.getName());
    }

    @Operation(summary = "List a bill's payments with refund status (WAITER/ADMIN)")
    @GetMapping("/bills/{id}/payments")
    @PreAuthorize("hasAnyRole('WAITER','ADMIN')")
    public List<PaymentResponse> listPayments(@PathVariable Long id) {
        return paymentService.listPayments(id);
    }

    @Operation(summary = "Refund a confirmed payment, full or partial (WAITER)")
    @PostMapping("/payments/{id}/refund")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('WAITER')")
    public Refund refundPayment(
            @PathVariable Long id, @Valid @RequestBody RefundPaymentRequest request, Authentication authentication) {
        return paymentService.refundPayment(id, request.amount(), request.reason(), authentication.getName());
    }

    @Operation(summary = "List refunds issued against a payment (WAITER/ADMIN)")
    @GetMapping("/payments/{id}/refunds")
    @PreAuthorize("hasAnyRole('WAITER','ADMIN')")
    public List<RefundResponse> listRefunds(@PathVariable Long id) {
        return paymentService.listRefunds(id);
    }
}
