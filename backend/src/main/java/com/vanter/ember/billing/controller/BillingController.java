package com.vanter.ember.billing.controller;

import com.vanter.ember.billing.dto.CalculateBillRequest;
import com.vanter.ember.billing.dto.DigitalPaymentRequest;
import com.vanter.ember.billing.dto.PhysicalPaymentRequest;
import com.vanter.ember.billing.dto.RequestBillingRequest;
import com.vanter.ember.billing.dto.SplitBillRequest;
import com.vanter.ember.billing.event.BillingRequested;
import com.vanter.ember.billing.model.Bill;
import com.vanter.ember.billing.model.BillSplit;
import com.vanter.ember.billing.model.Payment;
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
}
