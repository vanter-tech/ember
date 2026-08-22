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
        "GET,  /api/catalog/categories",
        "GET,  /api/catalog/categories/1",
        "POST, /api/catalog/categories",
        "PUT,  /api/catalog/categories/1",
        "DELETE, /api/catalog/categories/1",
        "GET,  /api/catalog/items",
        "GET,  /api/catalog/items/1",
        "POST, /api/catalog/items",
        "DELETE, /api/catalog/items/1",
        "GET,  /api/catalog/tables",
        "GET,  /api/catalog/tables/1",
        "POST, /api/catalog/tables",
        "PUT,  /api/catalog/tables/1",
        "DELETE, /api/catalog/tables/1",
        "POST, /api/sessions",
        "GET,  /api/sessions/sess-1",
        "GET,  /api/sessions/sess-1/qr",
        "POST, /api/sessions/sess-1/join",
        "POST, /api/sessions/sess-1/items",
        "DELETE, /api/sessions/sess-1/items/item-1",
        "GET,  /api/kitchen/orders",
        "GET,  /api/kitchen/display",
        "POST, /api/billing/sessions/sess-1/request",
        "POST, /api/billing/sessions/sess-1/bill",
        "POST, /api/billing/bills/1/split",
        "POST, /api/billing/payments/physical",
        "POST, /api/billing/payments/digital",
        "POST, /api/billing/payments/1/confirm",
        "POST, /billing/bills/1/void",
        "GET,  /billing/bills/1/payments",
        "POST, /billing/payments/1/refund",
        "GET,  /billing/payments/1/refunds",
        "PATCH, /api/admin/users/u-1/role",
        "POST, /api/admin/staff",
        "PATCH, /api/admin/users/u-1/role",
        "GET,  /api/admin/staff",
        "PATCH, /api/admin/staff/u-1",
        "GET,  /api/admin/analytics/range",
        "GET,  /api/admin/analytics/summary",
        "GET,  /api/admin/analytics/sales",
        "GET,  /api/admin/analytics/products",
        "GET,  /api/admin/analytics/tables",
        "POST, /cash-shifts/open",
        "GET,  /cash-shifts/current",
        "GET,  /cash-shifts",
        "GET,  /cash-shifts/1",
        "POST, /cash-shifts/1/movements",
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
