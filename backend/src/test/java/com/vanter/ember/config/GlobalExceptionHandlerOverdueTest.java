package com.vanter.ember.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanter.ember.cashregister.exception.CashShiftOverdueException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

class GlobalExceptionHandlerOverdueTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void cashShiftOverdue_maps_to_409_with_stable_code() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getRequestURI()).thenReturn("/billing/payments/physical");

        ProblemDetail problem = handler.handleCashShiftOverdue(
                new CashShiftOverdueException("Cash shift is overdue; prolong or close it"), request);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getDetail()).isEqualTo("Cash shift is overdue; prolong or close it");
        assertThat(problem.getProperties()).containsEntry("code", "CASH_SHIFT_OVERDUE");
    }
}
