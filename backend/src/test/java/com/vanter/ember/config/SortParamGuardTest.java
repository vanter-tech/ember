package com.vanter.ember.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SortParamGuardTest {

    private final SortParamGuard guard = new SortParamGuard();

    private boolean allows(String... sorts) throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (sorts.length > 0) {
            request.setParameter("sort", sorts);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean allowed = guard.preHandle(request, response, null);
        if (!allowed) {
            assertEquals(400, response.getStatus());
        }
        return allowed;
    }

    @Test
    void allowsRequestsWithoutSort() throws IOException {
        assertTrue(allows());
    }

    @Test
    void allowsPlainPropertiesWithOptionalDirection() throws IOException {
        assertTrue(allows("createdAt"));
        assertTrue(allows("createdAt,desc"));
        assertTrue(allows("openedAt,ASC", "user.name"));
    }

    @Test
    void rejectsAnythingThatIsNotAnIdentifier() throws IOException {
        assertFalse(allows("id;drop table users"));
        assertFalse(allows("(select 1)"));
        assertFalse(allows("id,desc,extra"));
        assertFalse(allows("createdAt", "1=1"));
        assertFalse(allows(""));
    }
}
