package com.company.checkout.common.util;

import java.util.UUID;

/**
 * Central place for id formats. Prefixed ids ({@code chk_...}) are self-describing in logs and
 * support tickets — you immediately know what kind of id someone pasted.
 */
public final class IdGenerator {

    private IdGenerator() {
    }

    public static String checkoutId() {
        return "chk_" + compactUuid();
    }

    public static String correlationId() {
        return compactUuid();
    }

    private static String compactUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
