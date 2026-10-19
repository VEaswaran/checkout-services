package com.company.checkout.common.exception;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.company.checkout.common.logging.LogFields;
import com.company.checkout.common.logging.MdcKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Converts every exception into an RFC 9457 {@code application/problem+json} response:
 *
 * <pre>{@code
 * {
 *   "type": "urn:problem-type:checkout:chk-404-001",
 *   "title": "Checkout not found",
 *   "status": 404,
 *   "detail": "Checkout 'chk_123' was not found",
 *   "instance": "/api/v1/checkouts/chk_123",
 *   "errorCode": "CHK-404-001",
 *   "correlationId": "5b1f...",
 *   "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
 *   "timestamp": "2026-10-19T12:44:00.123Z"
 * }
 * }</pre>
 *
 * Logging policy: 4xx -> WARN without stack trace (client's fault, avoid noise);
 * 5xx -> ERROR with stack trace (our fault, needs attention). Each error is logged exactly once, here.
 *
 * <p>Extending {@link ResponseEntityExceptionHandler} keeps correct statuses for all Spring MVC
 * exceptions (405, 415, 404 for unknown paths, method validation, ...).
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String PROP_ERROR_CODE = "errorCode";

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ProblemDetail> handleApplicationException(ApplicationException ex, WebRequest request) {
        ErrorCode code = ex.errorCode();
        ProblemDetail body = ProblemDetail.forStatusAndDetail(code.status(),
                ex.clientDetail() != null ? ex.clientDetail() : code.title());
        body.setTitle(code.title());
        body.setProperty(PROP_ERROR_CODE, code.code());

        LoggingEventBuilder event = code.status().is5xxServerError()
                ? log.atError().setCause(ex)
                : log.atWarn();
        event.addKeyValue(LogFields.ERROR_CODE, code.code())
                .addKeyValue(LogFields.ERROR_TYPE, ex.getClass().getSimpleName());
        ex.logContext().forEach(event::addKeyValue);
        event.log("Request failed: {}", ex.getMessage());

        return ResponseEntity.status(code.status()).body(enrich(body, code, request));
    }

    /** Last resort: anything unexpected becomes a generic 500 — never leak exception messages. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, WebRequest request) {
        ErrorCode code = ErrorCode.INTERNAL_ERROR;
        log.atError().setCause(ex)
                .addKeyValue(LogFields.ERROR_CODE, code.code())
                .addKeyValue(LogFields.ERROR_TYPE, ex.getClass().getName())
                .log("Unhandled exception");
        ProblemDetail body = ProblemDetail.forStatusAndDetail(code.status(),
                "An unexpected error occurred. Quote the correlationId when contacting support.");
        body.setTitle(code.title());
        return ResponseEntity.status(code.status()).body(enrich(body, code, request));
    }

    /** Bean-validation failures on @RequestBody: return every field error, not just the first. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        List<Map<String, Object>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.<String, Object>of(
                        "field", fe.getField(),
                        "message", fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage()))
                .toList();
        ProblemDetail body = ex.getBody();
        body.setTitle(ErrorCode.VALIDATION_FAILED.title());
        body.setDetail("One or more fields are invalid");
        body.setProperty(PROP_ERROR_CODE, ErrorCode.VALIDATION_FAILED.code());
        body.setProperty("errors", errors);
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    /**
     * Single funnel for every Spring MVC exception: enrich the body and log once at WARN/ERROR.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        ProblemDetail problem = body instanceof ProblemDetail pd
                ? pd
                : ProblemDetail.forStatus(statusCode);
        ErrorCode fallback = statusCode.is4xxClientError() ? ErrorCode.MALFORMED_REQUEST : ErrorCode.INTERNAL_ERROR;
        if (problem.getProperties() == null || !problem.getProperties().containsKey(PROP_ERROR_CODE)) {
            problem.setProperty(PROP_ERROR_CODE, fallback.code());
        }
        LoggingEventBuilder event = statusCode.is5xxServerError() ? log.atError().setCause(ex) : log.atWarn();
        event.addKeyValue(LogFields.ERROR_CODE, problem.getProperties().get(PROP_ERROR_CODE))
                .addKeyValue(LogFields.ERROR_TYPE, ex.getClass().getSimpleName())
                .log("Request rejected: {}", ex.getMessage());
        return ResponseEntity.status(statusCode).headers(headers).body(enrich(problem, fallback, request));
    }

    private static ProblemDetail enrich(ProblemDetail body, ErrorCode code, WebRequest request) {
        String errorCode = body.getProperties() != null && body.getProperties().get(PROP_ERROR_CODE) != null
                ? body.getProperties().get(PROP_ERROR_CODE).toString()
                : code.code();
        body.setProperty(PROP_ERROR_CODE, errorCode);
        body.setType(URI.create("urn:problem-type:checkout:" + errorCode.toLowerCase(Locale.ROOT)));
        if (request instanceof ServletWebRequest servletRequest) {
            body.setInstance(URI.create(servletRequest.getRequest().getRequestURI()));
        }
        body.setProperty("correlationId", MDC.get(MdcKeys.CORRELATION_ID));
        body.setProperty("traceId", MDC.get(MdcKeys.TRACE_ID));
        body.setProperty("timestamp", Instant.now().toString());
        return body;
    }
}
