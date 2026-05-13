package com.vanter.ember.billing.controller;

import com.vanter.ember.billing.dto.CalculateBillRequest;
import com.vanter.ember.billing.dto.DigitalPaymentRequest;
import com.vanter.ember.billing.dto.PhysicalPaymentRequest;
import com.vanter.ember.billing.dto.SplitBillRequest;
import com.vanter.ember.billing.model.Bill;
import com.vanter.ember.billing.model.BillSplit;
import com.vanter.ember.billing.model.Payment;
import com.vanter.ember.billing.model.SplitMethod;
import com.vanter.ember.billing.service.BillingService;
import com.vanter.ember.billing.service.PaymentService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/billing")
@RequiredArgsConstructor
public class BillingController {

    private final BillingService billingService;
    private final PaymentService paymentService;

    @PostMapping("/sessions/{sessionId}/bill")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('WAITER','CUSTOMER')")
    public Bill calculateBill(@PathVariable String sessionId,
                              @Valid @RequestBody CalculateBillRequest request) {
        return billingService.calculateBill(sessionId, request.splitMethod());
    }

    @PostMapping("/bills/{id}/split")
    @PreAuthorize("hasAnyRole('WAITER','CUSTOMER')")
    public List<BillSplit> splitBill(@PathVariable Long id,
                                     @Valid @RequestBody SplitBillRequest request) {
        if (request.splitMethod() == SplitMethod.BY_CONSUMPTION) {
            return billingService.splitByConsumption(id);
        }
        return billingService.splitEqually(id, request.participantCount());
    }

    @PostMapping("/payments/physical")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('WAITER')")
    public Payment registerPhysicalPayment(@Valid @RequestBody PhysicalPaymentRequest request) {
        return paymentService.registerPhysicalPayment(
                request.billId(), request.participantName(), request.amount());
    }

    @PostMapping("/payments/digital")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('WAITER','CUSTOMER')")
    public Payment initiateDigitalPayment(@Valid @RequestBody DigitalPaymentRequest request) {
        return paymentService.initiateDigitalPayment(
                request.billId(), request.participantName(), request.amount());
    }

    @PostMapping("/payments/{id}/confirm")
    @PreAuthorize("hasRole('WAITER')")
    public Payment confirmDigitalPayment(@PathVariable Long id) {
        return paymentService.confirmDigitalPayment(id);
    }
}
