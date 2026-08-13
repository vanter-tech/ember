package com.vanter.ember.config;

import com.vanter.ember.session.exception.TooManyParticipantsException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.UUID;

/**
 * Translates every exception escaping a controller into an RFC 7807 {@link ProblemDetail}.
 *
 * <p>Extends {@link ResponseEntityExceptionHandler} so the standard Spring MVC exceptions
 * (binding, message conversion, type mismatch, {@code ResponseStatusException}, ...) keep their
 * dedicated status codes instead of being absorbed by the {@link Exception} catch-all below.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    static final String GENERIC_ERROR_DETAIL =
            "An unexpected error occurred. Quote the traceId when contacting support.";

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleResourceNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(TooManyParticipantsException.class)
    public ProblemDetail handleTooManyParticipants(TooManyParticipantsException ex, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegalState(IllegalStateException ex, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        return problem(HttpStatus.UNAUTHORIZED, "Invalid credentials", request.getRequestURI());
    }

    /**
     * Without this, the {@link Exception} catch-all would turn every authentication failure raised
     * inside a controller into a 500 instead of letting it surface as a 401.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return problem(HttpStatus.UNAUTHORIZED, "Authentication required", request.getRequestURI());
    }

    /**
     * {@code @PreAuthorize} denials and explicit {@code AccessDeniedException}s used to reach
     * Spring Security's {@code ExceptionTranslationFilter}; the catch-all now shadows it, so the
     * 403 is produced here (with a body) instead.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, "Access denied", request.getRequestURI());
    }

    /**
     * Last-resort handler. The cause is logged in full and correlated to the client through an
     * opaque {@code traceId}; the response body never carries the original message, which may
     * expose internals (SQL, host names, class names).
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        String traceId = UUID.randomUUID().toString();
        log.error("Unhandled exception [traceId={}] on {} {}",
                traceId, request.getMethod(), request.getRequestURI(), ex);

        ProblemDetail problem = problem(
                HttpStatus.INTERNAL_SERVER_ERROR, GENERIC_ERROR_DETAIL, request.getRequestURI());
        problem.setProperty("traceId", traceId);
        return problem;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        String detail = ex.getBindingResult().getAllErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("Validation failed");

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(problem(HttpStatus.BAD_REQUEST, detail, requestUri(request)));
    }

    ProblemDetail problem(HttpStatus status, String detail, String path) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        if (path != null) {
            problem.setInstance(URI.create(path));
        }
        return problem;
    }

    private String requestUri(WebRequest request) {
        return request instanceof ServletWebRequest servletRequest
                ? servletRequest.getRequest().getRequestURI()
                : null;
    }
}
