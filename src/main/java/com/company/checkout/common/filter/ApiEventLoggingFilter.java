package com.company.checkout.common.filter;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import com.company.checkout.common.logging.ApiEventAction;
import com.company.checkout.common.logging.LogFields;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Emits exactly two structured events per API call — the "API event" timeline:
 *
 * <pre>
 * event.action=api.request.received  api.received_at=2026-10-19T12:44:00.101Z  http.request.method=POST url.path=/api/v1/checkouts
 * event.action=api.response.sent     api.completed_at=...  http.response.status_code=201  api.duration_ms=42  event.outcome=success
 * </pre>
 *
 * Uses a dedicated logger name ({@code api.event}) so these lines can be routed, retained or
 * alerted on separately in ELK/Dynatrace, and silenced locally without touching app logs.
 *
 * <p>Deliberately does NOT log bodies or query strings: they may contain PII / card data.
 */
public class ApiEventLoggingFilter extends OncePerRequestFilter {

    private static final Logger apiEventLog = LoggerFactory.getLogger("api.event");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/actuator") || uri.equals("/favicon.ico");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        long startNanos = System.nanoTime();
        Instant receivedAt = Instant.now();

        apiEventLog.atInfo()
                .addKeyValue(LogFields.EVENT_ACTION, ApiEventAction.REQUEST_RECEIVED.value())
                .addKeyValue(LogFields.API_RECEIVED_AT, receivedAt.toString())
                .addKeyValue(LogFields.HTTP_METHOD, request.getMethod())
                .addKeyValue(LogFields.URL_PATH, request.getRequestURI())
                .addKeyValue(LogFields.CLIENT_ADDRESS, request.getRemoteAddr())
                .log("API request received {} {}", request.getMethod(), request.getRequestURI());

        Throwable failure = null;
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException ex) {
            failure = ex;
            throw ex;
        } finally {
            long durationNanos = System.nanoTime() - startNanos;
            // An exception escaping the chain means the container will answer 500.
            int status = failure != null ? 500 : response.getStatus();
            apiEventLog.atInfo()
                    .addKeyValue(LogFields.EVENT_ACTION, ApiEventAction.RESPONSE_SENT.value())
                    .addKeyValue(LogFields.EVENT_OUTCOME, status < 400 ? "success" : "failure")
                    .addKeyValue(LogFields.API_RECEIVED_AT, receivedAt.toString())
                    .addKeyValue(LogFields.API_COMPLETED_AT, Instant.now().toString())
                    .addKeyValue(LogFields.API_DURATION_MS, TimeUnit.NANOSECONDS.toMillis(durationNanos))
                    .addKeyValue(LogFields.EVENT_DURATION_NS, durationNanos)
                    .addKeyValue(LogFields.HTTP_METHOD, request.getMethod())
                    .addKeyValue(LogFields.URL_PATH, request.getRequestURI())
                    .addKeyValue(LogFields.HTTP_STATUS, status)
                    .log("API response sent {} {} -> {} in {} ms", request.getMethod(), request.getRequestURI(),
                            status, TimeUnit.NANOSECONDS.toMillis(durationNanos));
        }
    }
}
