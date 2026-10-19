package com.company.checkout.common.client;

import java.util.concurrent.TimeoutException;

import com.company.checkout.common.config.DownstreamProperties;
import com.company.checkout.common.exception.DownstreamServiceException;
import com.company.checkout.common.exception.ErrorCode;
import com.company.checkout.common.logging.ReactorMdc;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * Base class for every downstream adapter. Gives each client the same, tested behaviour:
 *
 * <ul>
 *   <li>overall timeout per logical call (on top of Netty connect/response timeouts)</li>
 *   <li>exponential-backoff retry for transient failures only, and only when the caller says the
 *       operation is safe to repeat (GET, or POST with an Idempotency-Key)</li>
 *   <li>every failure translated into {@link DownstreamServiceException} with a proper {@link ErrorCode}</li>
 *   <li>MDC propagated into Reactor so filters log with correlationId / traceId</li>
 * </ul>
 *
 * <p>The service runs Spring MVC on virtual threads, so {@code block()} is cheap and keeps service
 * code simple and synchronous. If you move to WebFlux, return the {@code Mono} instead.
 */
public abstract class AbstractDownstreamClient {

    private static final int MAX_LOGGED_BODY = 500;

    protected final String serviceName;
    protected final DownstreamProperties.Endpoint endpoint;

    protected AbstractDownstreamClient(String serviceName, DownstreamProperties.Endpoint endpoint) {
        this.serviceName = serviceName;
        this.endpoint = endpoint;
    }

    /**
     * Executes a reactive call synchronously with timeout, optional retry and error translation.
     *
     * @param operation logical name used in logs and errors, e.g. {@code "authorizePayment"}
     * @param call      the WebClient call (lazy; resubscribed on retry)
     * @param retryable whether it is safe to repeat this call
     */
    protected <T> T execute(String operation, Mono<T> call, boolean retryable) {
        Mono<T> pipeline = call;
        if (retryable && endpoint.retry().maxAttempts() > 0) {
            pipeline = pipeline.retryWhen(Retry.backoff(endpoint.retry().maxAttempts(), endpoint.retry().initialBackoff())
                    .jitter(0.5)
                    .filter(AbstractDownstreamClient::isTransient)
                    .onRetryExhaustedThrow((spec, signal) -> signal.failure()));
        }
        T result = pipeline
                .timeout(endpoint.overallTimeout())
                .onErrorMap(ex -> !(ex instanceof DownstreamServiceException), ex -> translate(operation, ex))
                .contextWrite(ReactorMdc.capture())
                .block();
        if (result == null) {
            throw new DownstreamServiceException(ErrorCode.DOWNSTREAM_ERROR, serviceName, operation, null,
                    false, "Empty response body", null);
        }
        return result;
    }

    /**
     * Default mapping of a non-2xx response; use in {@code retrieve().onStatus(HttpStatusCode::isError, ...)}.
     * Override in subclasses for service-specific codes (e.g. 409 -> domain conflict).
     */
    protected Mono<DownstreamServiceException> mapErrorResponse(String operation, ClientResponse response) {
        int status = response.statusCode().value();
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(body -> {
                    boolean transientError = status == 502 || status == 503 || status == 504 || status == 429;
                    ErrorCode code = status == 503 || status == 429 ? ErrorCode.DOWNSTREAM_UNAVAILABLE
                            : status == 504 ? ErrorCode.DOWNSTREAM_TIMEOUT
                            : ErrorCode.DOWNSTREAM_ERROR;
                    return new DownstreamServiceException(code, serviceName, operation, status, transientError,
                            "HTTP " + status + " body=" + truncate(body), null);
                });
    }

    private DownstreamServiceException translate(String operation, Throwable ex) {
        if (ex instanceof TimeoutException) {
            return new DownstreamServiceException(ErrorCode.DOWNSTREAM_TIMEOUT, serviceName, operation, null,
                    true, "Timed out after " + endpoint.overallTimeout(), ex);
        }
        if (ex instanceof WebClientRequestException) {
            // connection refused, DNS failure, connect/response timeout from Netty
            return new DownstreamServiceException(ErrorCode.DOWNSTREAM_UNAVAILABLE, serviceName, operation, null,
                    true, "I/O error: " + ex.getMessage(), ex);
        }
        if (ex instanceof WebClientResponseException wcre) {
            return new DownstreamServiceException(ErrorCode.DOWNSTREAM_ERROR, serviceName, operation,
                    wcre.getStatusCode().value(), wcre.getStatusCode().is5xxServerError(), wcre.getMessage(), ex);
        }
        return new DownstreamServiceException(ErrorCode.DOWNSTREAM_ERROR, serviceName, operation, null,
                false, "Unexpected error: " + ex, ex);
    }

    private static boolean isTransient(Throwable ex) {
        return switch (ex) {
            case DownstreamServiceException dse -> dse.retryable();
            case WebClientRequestException ignored -> true;
            case TimeoutException ignored -> true;
            default -> false;
        };
    }

    private static String truncate(String body) {
        return body.length() <= MAX_LOGGED_BODY ? body : body.substring(0, MAX_LOGGED_BODY) + "...";
    }
}
