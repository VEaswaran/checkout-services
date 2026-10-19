package com.company.checkout.common.filter;

import java.io.IOException;
import java.util.regex.Pattern;

import com.company.checkout.common.logging.MdcKeys;
import com.company.checkout.common.util.IdGenerator;
import com.company.checkout.common.web.ApiHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Establishes request-scoped log context.
 *
 * <ol>
 *   <li>Reads {@code X-Correlation-Id} from the caller (or generates one) and validates it —
 *       raw header values in logs are a log-injection vector.</li>
 *   <li>Puts correlationId / clientId in MDC so every log line of the request carries them.</li>
 *   <li>Echoes {@code X-Correlation-Id} and {@code X-Trace-Id} on the response so a consumer can
 *       hand support a single id that finds the request in ELK and the trace in Dynatrace.</li>
 * </ol>
 *
 * Runs right after Micrometer's observation filter, so {@code traceId} is already in MDC here.
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9._:-]{1,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String correlationId = sanitize(request.getHeader(ApiHeaders.X_CORRELATION_ID));
        if (correlationId == null) {
            correlationId = IdGenerator.correlationId();
        }
        String clientId = sanitize(request.getHeader(ApiHeaders.X_CLIENT_ID));

        MDC.put(MdcKeys.CORRELATION_ID, correlationId);
        if (clientId != null) {
            MDC.put(MdcKeys.CLIENT_ID, clientId);
        }
        // Set before the body is written; headers cannot be added once the response is committed.
        response.setHeader(ApiHeaders.X_CORRELATION_ID, correlationId);
        String traceId = MDC.get(MdcKeys.TRACE_ID);
        if (traceId != null) {
            response.setHeader(ApiHeaders.X_TRACE_ID, traceId);
        }
        try {
            chain.doFilter(request, response);
        } finally {
            // Servlet threads (and virtual-thread carriers) are reused: always clean up.
            MDC.remove(MdcKeys.CORRELATION_ID);
            MDC.remove(MdcKeys.CLIENT_ID);
        }
    }

    private static String sanitize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return SAFE_ID.matcher(trimmed).matches() ? trimmed : null;
    }
}
