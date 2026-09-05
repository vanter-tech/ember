package com.vanter.ember.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityAuditTest {

    @Autowired MockMvc mockMvc;

    @ParameterizedTest
    @CsvSource({
        "GET,  /catalog/categories",
        "GET,  /catalog/categories/1",
        "POST, /catalog/categories",
        "PUT,  /catalog/categories/1",
        "DELETE, /catalog/categories/1",
        "GET,  /catalog/items",
        "GET,  /catalog/items/1",
        "POST, /catalog/items",
        "DELETE, /catalog/items/1",
        "GET,  /catalog/modifier-groups",
        "POST, /catalog/modifier-groups",
        "PATCH, /catalog/modifier-groups/1",
        "POST, /catalog/modifier-groups/1/options",
        "PATCH, /catalog/items/1/modifier-groups",
        "GET,  /catalog/inventory",
        "POST, /catalog/inventory",
        "PATCH, /catalog/inventory/1",
        "POST, /catalog/inventory/1/restock",
        "DELETE, /catalog/inventory/1",
        "GET,  /catalog/tables",
        "GET,  /catalog/tables/1",
        "POST, /catalog/tables",
        "PUT,  /catalog/tables/1",
        "DELETE, /catalog/tables/1",
        "POST, /sessions",
        "GET,  /sessions/sess-1",
        "GET,  /sessions/sess-1/qr",
        "POST, /sessions/sess-1/join",
        "POST, /sessions/sess-1/items",
        "DELETE, /sessions/sess-1/items/item-1",
        "POST, /sessions/sess-1/transfer",
        "POST, /sessions/sess-1/waiter-items",
        "DELETE, /sessions/sess-1/cancel",
        "POST, /sessions/sess-1/leave",
        "POST, /sessions/sess-1/resume",
        "GET,  /kitchen/orders",
        "GET,  /kitchen/display",
        "GET,  /menu",
        "GET,  /settings",
        "PUT,  /settings",
        "GET,  /dashboard/status",
        "GET,  /identity/waiters",
        "GET,  /admin/restaurant",
        "POST, /billing/sessions/sess-1/request",
        "POST, /billing/sessions/sess-1/bill",
        "POST, /billing/bills/1/split",
        "POST, /billing/bills/1/settle",
        "POST, /billing/bills/1/splits/redistribute",
        "POST, /billing/payments/physical",
        "POST, /billing/payments/digital",
        "POST, /billing/payments/1/confirm",
        "POST, /billing/bills/1/void",
        "GET,  /billing/bills/1/payments",
        "POST, /billing/payments/1/refund",
        "GET,  /billing/payments/1/refunds",
        "POST, /printing/bills/1/receipt",
        "PATCH, /admin/users/u-1/role",
        "POST, /admin/staff",
        "PATCH, /admin/users/u-1/role",
        "GET,  /admin/staff",
        "PATCH, /admin/staff/u-1",
        "PUT,  /admin/staff/u-1/pin",
        "DELETE, /admin/staff/u-1/pin",
        "GET,  /admin/analytics/range",
        "GET,  /admin/analytics/summary",
        "GET,  /admin/analytics/sales",
        "GET,  /admin/analytics/products",
        "GET,  /admin/analytics/tables",
        "POST, /cash-shifts/open",
        "GET,  /cash-shifts/current",
        "GET,  /cash-shifts",
        "GET,  /cash-shifts/1",
        "POST, /cash-shifts/1/movements",
        "POST, /cash-shifts/1/prolong",
        "POST, /cash-shifts/1/close",
        "GET,  /cash-shifts/daily-report",
        "POST, /loyalty/rewards",
        "GET,  /loyalty/rewards",
        "PATCH, /loyalty/rewards/1",
        "GET,  /loyalty/accounts/me",
        "GET,  /loyalty/accounts/me/visits",
        "POST, /printing/admin/agents",
        "GET,  /printing/admin/agents",
        "PATCH, /printing/admin/agents/1",
        "POST, /printing/admin/agents/1/regenerate-key",
        "DELETE, /printing/admin/agents/1",
        "POST, /printing/admin/agents/1/printers",
        "GET,  /printing/admin/agents/1/printers",
        "PATCH, /printing/admin/agents/printers/1",
        "GET,  /printing/jobs",
        "POST, /printing/jobs/1/retry"
    })
    void unauthenticated_returns401(String method, String path) throws Exception {
        var request = switch (method.trim()) {
            case "GET" -> get(path.trim());
            case "POST" -> post(path.trim()).contentType(MediaType.APPLICATION_JSON).content("{}");
            case "PUT" -> org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .put(path.trim()).contentType(MediaType.APPLICATION_JSON).content("{}");
            case "PATCH" -> patch(path.trim()).contentType(MediaType.APPLICATION_JSON).content("{}");
            case "DELETE" -> delete(path.trim());
            default -> throw new IllegalArgumentException("Unknown method: " + method);
        };

        mockMvc.perform(request).andExpect(status().isUnauthorized());
    }
}
