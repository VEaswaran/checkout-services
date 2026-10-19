package com.company.checkout.common.logging;

import java.util.Map;

import org.slf4j.MDC;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

/**
 * Bridges the servlet thread's MDC into Reactor so WebClient callbacks — which run on Netty
 * event-loop threads, and on timer threads during retries — still log with the same
 * correlationId / traceId.
 *
 * <p>Usage: capture once at the edge of the reactive chain ({@code contextWrite(ReactorMdc.capture())})
 * and restore inside callbacks ({@code ReactorMdc.run(mdc, () -> log...)}).
 */
public final class ReactorMdc {

    private static final String CONTEXT_KEY = ReactorMdc.class.getName() + ".MDC";

    private ReactorMdc() {
    }

    /** Snapshot of the current thread's MDC as a Reactor context. */
    public static Context capture() {
        Map<String, String> current = MDC.getCopyOfContextMap();
        return Context.of(CONTEXT_KEY, current == null ? Map.<String, String>of() : Map.copyOf(current));
    }

    /** Reads the MDC snapshot stored by {@link #capture()}; empty map if none. */
    public static Map<String, String> from(ContextView context) {
        return context.getOrDefault(CONTEXT_KEY, Map.of());
    }

    /** Runs {@code action} with the given MDC, then restores whatever the thread had before. */
    public static void run(Map<String, String> mdc, Runnable action) {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        try {
            MDC.setContextMap(mdc);
            action.run();
        } finally {
            if (previous == null) {
                MDC.clear();
            } else {
                MDC.setContextMap(previous);
            }
        }
    }
}
