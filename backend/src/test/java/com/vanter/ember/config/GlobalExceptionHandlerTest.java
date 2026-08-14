package com.vanter.ember.config;

import com.vanter.ember.session.exception.TooManyParticipantsException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void unexpectedException_returns500ProblemDetailWithTraceId() throws Exception {
        mockMvc.perform(get("/boom/runtime"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.title").value(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase()))
                .andExpect(jsonPath("$.detail").value(GlobalExceptionHandler.GENERIC_ERROR_DETAIL))
                .andExpect(jsonPath("$.instance").value("/boom/runtime"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void unexpectedException_doesNotLeakInternalMessage() throws Exception {
        String body = mockMvc.perform(get("/boom/runtime"))
                .andExpect(status().isInternalServerError())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("jdbc:postgresql://secret-host");
    }

    @Test
    void resourceNotFound_returns404WithDetailAndInstance() throws Exception {
        mockMvc.perform(get("/boom/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Category not found: 99"))
                .andExpect(jsonPath("$.instance").value("/boom/not-found"));
    }

    @Test
    void accessDenied_returns403() throws Exception {
        mockMvc.perform(get("/boom/denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value("Access denied"));
    }

    @Test
    void badCredentials_returns401WithoutEchoingTheMessage() throws Exception {
        mockMvc.perform(get("/boom/bad-credentials"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid credentials"));
    }

    @Test
    void illegalArgument_returns409() throws Exception {
        mockMvc.perform(get("/boom/illegal-argument"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Email already in use"));
    }

    @Test
    void illegalState_returns409() throws Exception {
        mockMvc.perform(get("/boom/illegal-state"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Session already closed"));
    }

    @Test
    void tooManyParticipants_returns409() throws Exception {
        mockMvc.perform(get("/boom/too-many"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Table is full"));
    }

    // --- the catch-all must not shadow the standard Spring MVC exceptions ---

    @Test
    void responseStatusException_keepsItsOwnStatus() throws Exception {
        mockMvc.perform(get("/boom/response-status"))
                .andExpect(status().isNotFound());
    }

    @Test
    void typeMismatchOnPathVariable_stillReturns400() throws Exception {
        mockMvc.perform(get("/boom/typed/not-a-number"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void beanValidationFailure_returns400WithFirstErrorMessage() throws Exception {
        mockMvc.perform(post("/boom/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("name must not be blank"));
    }

    @Test
    void unreadableBody_stillReturns400() throws Exception {
        mockMvc.perform(post("/boom/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest());
    }

    @RestController
    static class ThrowingController {

        @GetMapping("/boom/runtime")
        void runtime() {
            throw new RuntimeException("connection refused to jdbc:postgresql://secret-host:5432/ember");
        }

        @GetMapping("/boom/not-found")
        void notFound() {
            throw new ResourceNotFoundException("Category not found: 99");
        }

        @GetMapping("/boom/denied")
        void denied() {
            throw new AccessDeniedException("Not authorized to view this session");
        }

        @GetMapping("/boom/bad-credentials")
        void badCredentials() {
            throw new BadCredentialsException("Bad credentials for ana@test.com");
        }

        @GetMapping("/boom/illegal-argument")
        void illegalArgument() {
            throw new IllegalArgumentException("Email already in use");
        }

        @GetMapping("/boom/illegal-state")
        void illegalState() {
            throw new IllegalStateException("Session already closed");
        }

        @GetMapping("/boom/too-many")
        void tooMany() {
            throw new TooManyParticipantsException("Table is full");
        }

        @GetMapping("/boom/response-status")
        void responseStatus() {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Code not found");
        }

        @GetMapping("/boom/typed/{id}")
        void typed(@PathVariable long id) {
        }

        @PostMapping("/boom/validated")
        void validated(@Valid @RequestBody Payload payload) {
        }
    }

    record Payload(@NotBlank(message = "name must not be blank") String name) {
    }
}
