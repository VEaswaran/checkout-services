# checkout-service — reference template

Spring Boot 4.0 · Java 21 · Gradle (Kotlin DSL) · Spring MVC on virtual threads · WebClient · Micrometer Tracing / OpenTelemetry · ECS JSON logs

This project is the **feature reference** for the checkout domain: copy its shapes (package layout,
exception model, logging, downstream clients, tests) when you add new APIs.

---

## 1. Run it

```bash
./gradlew bootRun            # profile "local": readable logs + an in-app fake payment service
./gradlew test
./gradlew bootJar            # build/libs/checkout-service-0.0.1-SNAPSHOT.jar
```

Then fire the requests in `http/checkout.http` (IntelliJ / VS Code REST Client).

Deployed environments: `SPRING_PROFILES_ACTIVE=prod` + env vars (`PAYMENT_SERVICE_URL`,
`OTEL_EXPORTER_OTLP_TRACES_ENDPOINT`, `APP_ENV`, `APP_VERSION`, `TRACING_SAMPLING_PROBABILITY`).

## 2. Package structure

```
com.company.checkout
├── CheckoutApplication
├── common/                         shared kernel – no feature logic here
│   ├── client/                     AbstractDownstreamClient, CorrelationHeaderExchangeFilter, DownstreamLoggingExchangeFilter
│   ├── config/                     FilterConfig, WebClientConfig, DownstreamProperties, TimeConfig
│   ├── exception/                  ErrorCode, ApplicationException (+ subclasses), GlobalExceptionHandler
│   ├── filter/                     CorrelationIdFilter, ApiEventLoggingFilter
│   ├── logging/                    MdcKeys, LogFields, ApiEventAction, ReactorMdc
│   ├── util/                       MaskingUtil, IdGenerator
│   └── web/                        ApiHeaders
├── checkout/                       ONE FEATURE = ONE PACKAGE
│   ├── api/                        CheckoutController, CheckoutApiMapper, dto/ (request/response records)
│   ├── domain/                     Checkout (aggregate), Money, LineItem, CheckoutId, CheckoutStatus
│   ├── service/                    CheckoutService (use-case orchestration)
│   ├── repository/                 CheckoutRepository (port) + InMemoryCheckoutRepository (adapter)
│   └── client/                     PaymentClient + dto/ (downstream wire contract)
└── stub/                           @Profile("local") fake downstream services
```

Dependency direction: `api → service → domain` and `service → repository / client`.
`domain` depends on nothing but `common.exception`. DTOs never cross into `domain`; downstream DTOs never leave `client`.

## 3. End-to-end tracing — how one request is followed

```
caller ──X-Correlation-Id (optional)──▶ [ServerHttpObservationFilter]  traceId/spanId → MDC   (Micrometer)
                                        [CorrelationIdFilter]          correlationId/clientId → MDC, echo headers
                                        [ApiEventLoggingFilter]        event.action=api.request.received
                                        Controller → Service           checkoutId → MDC, business events
                                        PaymentClient (WebClient)      traceparent + X-Correlation-Id sent downstream
                                          DownstreamLoggingExchangeFilter: downstream.call.started / completed / failed
                                        [ApiEventLoggingFilter]        event.action=api.response.sent (status, duration)
caller ◀──X-Correlation-Id, X-Trace-Id──
```

* **traceId** (W3C `traceparent`) — technical trace across all services; Dynatrace / Elastic APM / OTel understand it.
* **correlationId** (`X-Correlation-Id`) — business id the caller can supply and support can quote; survives systems that drop trace headers (queues, batch, legacy).
* Both are on **every log line** and on the **error body** of every failure.
* MDC is bridged into Reactor (`ReactorMdc`) so WebClient callbacks and retries — which run on other threads — still log with the right ids. `spring.reactor.context-propagation=auto` + `io.micrometer:context-propagation` keep the trace parent across those hops.

### Local log output (profile `local`)

```
INFO [4bf92f35...,00f067aa...,demo-corr-003] api.event : API request received POST /api/v1/checkouts/chk_9a.../confirm
INFO [4bf92f35...,9c1d22e1...,demo-corr-003] api.event : Downstream call started payment-service POST http://localhost:8080/stub/payment-service/v1/payments/authorizations
INFO [4bf92f35...,b7ad6b71...,demo-corr-003] api.event : API request received POST /stub/payment-service/v1/payments/authorizations     <- same traceId on the "other" service
INFO [4bf92f35...,9c1d22e1...,demo-corr-003] api.event : Downstream call completed payment-service POST ... -> 200 in 31 ms
INFO [4bf92f35...,00f067aa...,demo-corr-003] c.c.c.checkout.service.CheckoutService : Checkout confirmed
INFO [4bf92f35...,00f067aa...,demo-corr-003] api.event : API response sent POST /api/v1/checkouts/chk_9a.../confirm -> 200 in 48 ms
```

### Production log output (profile `prod`, ECS JSON, one line per event — shown pretty here)

```json
{
  "@timestamp": "2026-10-19T12:44:00.149Z",
  "log.level": "INFO",
  "log.logger": "api.event",
  "message": "API response sent POST /api/v1/checkouts -> 201 in 48 ms",
  "service.name": "checkout-service", "service.version": "1.4.2", "service.environment": "prod",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736", "spanId": "00f067aa0ba902b7",
  "correlationId": "demo-corr-001", "clientId": "web-storefront",
  "event.action": "api.response.sent", "event.outcome": "success", "event.duration": 48211000,
  "api.received_at": "2026-10-19T12:44:00.101Z", "api.completed_at": "2026-10-19T12:44:00.149Z", "api.duration_ms": 48,
  "http.request.method": "POST", "url.path": "/api/v1/checkouts", "http.response.status_code": 201
}
```

## 4. Finding API events in ELK / Dynatrace

| Question | Kibana (KQL) | Dynatrace (DQL, Logs) |
|---|---|---|
| Whole flow of one call | `traceId : "4bf92f..."` or `correlationId : "demo-corr-001"` | `fetch logs \| filter trace_id == "4bf92f..."` (OneAgent log enrichment) or `filter matchesPhrase(content, "4bf92f...")` |
| When did a request arrive / leave | `event.action : ("api.request.received" or "api.response.sent") and correlationId : "..."` | `fetch logs \| filter matchesValue(content, "*api.response.sent*")` |
| Slow APIs (> 1 s) | `event.action : "api.response.sent" and api.duration_ms > 1000` | parse `api.duration_ms`, `filter > 1000` |
| Downstream failures | `event.action : "downstream.call.failed"` | `filter matchesPhrase(content, "downstream.call.failed")` |
| All 5xx | `log.level : "ERROR" and error.code : CHK-5*` | `filter loglevel == "ERROR"` |
| Everything about one checkout | `checkoutId : "chk_9a..."` | `filter matchesPhrase(content, "chk_9a...")` |

**Dynatrace options**
1. **OneAgent (most common):** it auto-instruments the JVM and ingests stdout. Set `TRACING_EXPORT_ENABLED=false` to avoid duplicate spans; OneAgent still links log lines to traces via the W3C trace context. Enable *log enrichment / trace-log correlation* in OneAgent settings.
2. **No OneAgent:** export OTLP directly — `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT=https://<env>.live.dynatrace.com/api/v2/otlp/v1/traces` with an `Authorization: Api-Token …` header (token scope `openTelemetryTrace.ingest`).

**ELK:** ship container stdout with Filebeat / Elastic Agent using the JSON (ndjson) parser — fields are already ECS, so no ingest pipeline/grok is needed. Traces can go to Elastic APM via its OTLP intake.

## 5. Conventions (the "best practices" this template encodes)

**Exceptions**
- One catalogue: `ErrorCode` (`CHK-<status>-<seq>`), codes are a public contract — never renumber.
- Throw `ResourceNotFoundException`, `BusinessRuleException`, `DownstreamServiceException`; never `RuntimeException("...")`.
- No try/catch in controllers. `GlobalExceptionHandler` returns RFC 9457 `application/problem+json` with `errorCode`, `correlationId`, `traceId`, `timestamp`.
- Log once, where handled: 4xx → WARN (no stack), 5xx → ERROR (with stack). Don't log-and-rethrow.
- Never expose internal/downstream messages to clients (`clientDetail` vs `getMessage()`).

**Logging**
- SLF4J fluent API with key/values (`log.atInfo().addKeyValue(...)`) — structured fields, not string parsing.
- Field names in `LogFields` (ECS-aligned), event names in `ApiEventAction`, MDC keys in `MdcKeys`.
- Never log bodies, query strings, tokens, card data or raw emails — use `MaskingUtil`, and override `toString()` on records holding secrets.
- `api.event` logger = timeline events; class loggers = business events.

**WebClient / downstream**
- Always build from the injected `WebClient.Builder` (`.clone()`), one client + pool per downstream (bulkhead).
- Connect, response and overall timeouts are mandatory and configured in `DownstreamProperties` (validated at startup).
- Retry only transient failures (5xx gateway, 429, I/O, timeout) and only idempotent calls (GET, or POST + `Idempotency-Key`).
- Extend `AbstractDownstreamClient`; map downstream errors to `DownstreamServiceException`.

**Domain**
- Aggregates with intention-revealing methods and explicit state machine (`CheckoutStatus`), no setters.
- Value objects as records (`Money` with `BigDecimal` + `Currency`, `CheckoutId`); validate in compact constructors.
- No Spring/JSON annotations in `domain`. Inject `Clock` for time.

**API**
- Versioned paths (`/api/v1`), `201 + Location` on create, bean validation on DTOs, separate request/response records.
- Money as strings in JSON; ISO-8601 UTC instants.

**Testing**
- Domain: plain JUnit (fast, most tests). Web: `@WebMvcTest` slice with `@MockitoBean`. Clients: stubbed `ExchangeFunction` (add WireMock for HTTP-level). One `@SpringBootTest` smoke test.

## 6. Adding a new feature (e.g. `promotion`)

1. `com.company.checkout.promotion.{api,domain,service,repository,client}` — mirror `checkout`.
2. New errors → add entries to `ErrorCode`.
3. New downstream → add an `Endpoint` field to `DownstreamProperties`, a `@Bean WebClient` in `WebClientConfig`, a client extending `AbstractDownstreamClient`, and YAML under `checkout.downstream.<name>`.
4. Log business milestones with `LogFields` keys; add new keys there, not inline strings.
5. Tests: domain unit test + `@WebMvcTest` for the controller.

## 7. Before production

- Replace `InMemoryCheckoutRepository` with a real store (JPA/Redis…), add Flyway/Liquibase.
- Add Spring Security (OAuth2 resource server) — filters are ordered so 401/403 still get API events.
- Consider Resilience4j circuit breaker on top of the retry policy for high-volume dependencies.
- Generate OpenAPI docs (springdoc) and contract tests with downstream teams.
- Tune `management.tracing.sampling.probability` for traffic volume.

## 8. First-build notes

Written against **Spring Boot 4.0.8** (Aug 2026). Boot 4 modularised starters and packages; if your
build reports a missing class, check these first against your exact Boot version:
`spring-boot-starter-webmvc` / `-webclient` / `-opentelemetry` / `-webmvc-test`,
`org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`,
`org.springframework.boot.web.servlet.FilterRegistrationBean`,
and the OTLP property names in `application-prod.yml`.
Bump to the latest 4.0.x patch when you start. To stay on Boot 3.5 instead, rename the starters
(`spring-boot-starter-web`, `spring-boot-starter-webflux` for WebClient, `micrometer-tracing-bridge-otel`)
and the test annotation package (`org.springframework.boot.test.autoconfigure.web.servlet`).
