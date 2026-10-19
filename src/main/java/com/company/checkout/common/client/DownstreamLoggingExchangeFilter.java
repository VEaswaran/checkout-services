package com.company.checkout.common.client;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.company.checkout.common.logging.ApiEventAction;
import com.company.checkout.common.logging.LogFields;
import com.company.checkout.common.logging.ReactorMdc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

/**
 * Logs every outbound HTTP attempt (each retry is its own attempt) as structured events with
 * start timestamp, status and duration, under the request's correlationId / traceId.
 *
 * <p>Logs scheme+host+path only: query strings and bodies may hold tokens or PII.
 */
public final class DownstreamLoggingExchangeFilter implements ExchangeFilterFunction {

    private static final Logger apiEventLog = LoggerFactory.getLogger("api.event");

    private final String serviceName;

    public DownstreamLoggingExchangeFilter(String serviceName) {
        this.serviceName = serviceName;
    }

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        return Mono.deferContextual(context -> {
            Map<String, String> mdc = ReactorMdc.from(context);
            String method = request.method().name();
            String url = safeUrl(request.url());
            long startNanos = System.nanoTime();
            Instant startedAt = Instant.now();

            ReactorMdc.run(mdc, () -> apiEventLog.atInfo()
                    .addKeyValue(LogFields.EVENT_ACTION, ApiEventAction.DOWNSTREAM_STARTED.value())
                    .addKeyValue(LogFields.DOWNSTREAM_SERVICE, serviceName)
                    .addKeyValue(LogFields.DOWNSTREAM_STARTED_AT, startedAt.toString())
                    .addKeyValue(LogFields.HTTP_METHOD, method)
                    .addKeyValue(LogFields.URL_FULL, url)
                    .log("Downstream call started {} {} {}", serviceName, method, url));

            return next.exchange(request)
                    .doOnNext(response -> ReactorMdc.run(mdc, () -> {
                        long ms = elapsedMs(startNanos);
                        int status = response.statusCode().value();
                        apiEventLog.atInfo()
                                .addKeyValue(LogFields.EVENT_ACTION, ApiEventAction.DOWNSTREAM_COMPLETED.value())
                                .addKeyValue(LogFields.EVENT_OUTCOME, status < 400 ? "success" : "failure")
                                .addKeyValue(LogFields.DOWNSTREAM_SERVICE, serviceName)
                                .addKeyValue(LogFields.DOWNSTREAM_STARTED_AT, startedAt.toString())
                                .addKeyValue(LogFields.DOWNSTREAM_DURATION_MS, ms)
                                .addKeyValue(LogFields.HTTP_METHOD, method)
                                .addKeyValue(LogFields.URL_FULL, url)
                                .addKeyValue(LogFields.HTTP_STATUS, status)
                                .log("Downstream call completed {} {} {} -> {} in {} ms",
                                        serviceName, method, url, status, ms);
                    }))
                    .doOnError(error -> ReactorMdc.run(mdc, () -> apiEventLog.atWarn()
                            .addKeyValue(LogFields.EVENT_ACTION, ApiEventAction.DOWNSTREAM_FAILED.value())
                            .addKeyValue(LogFields.EVENT_OUTCOME, "failure")
                            .addKeyValue(LogFields.DOWNSTREAM_SERVICE, serviceName)
                            .addKeyValue(LogFields.DOWNSTREAM_STARTED_AT, startedAt.toString())
                            .addKeyValue(LogFields.DOWNSTREAM_DURATION_MS, elapsedMs(startNanos))
                            .addKeyValue(LogFields.HTTP_METHOD, method)
                            .addKeyValue(LogFields.URL_FULL, url)
                            .addKeyValue(LogFields.ERROR_TYPE, error.getClass().getSimpleName())
                            .log("Downstream call failed {} {} {}: {}", serviceName, method, url, error.toString())))
                    .doOnCancel(() -> ReactorMdc.run(mdc, () -> apiEventLog.atWarn()
                            .addKeyValue(LogFields.EVENT_ACTION, ApiEventAction.DOWNSTREAM_CANCELLED.value())
                            .addKeyValue(LogFields.DOWNSTREAM_SERVICE, serviceName)
                            .addKeyValue(LogFields.DOWNSTREAM_DURATION_MS, elapsedMs(startNanos))
                            .addKeyValue(LogFields.URL_FULL, url)
                            .log("Downstream call cancelled (overall timeout?) {} {} {}", serviceName, method, url)));
        });
    }

    private static long elapsedMs(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }

    private static String safeUrl(URI uri) {
        return uri.getScheme() + "://" + uri.getAuthority() + uri.getRawPath();
    }
}
