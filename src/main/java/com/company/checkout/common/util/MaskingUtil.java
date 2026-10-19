package com.company.checkout.common.util;

/**
 * Masks sensitive values before they reach logs. Checkout handles PII and payment data, so the
 * rule is: never log a raw email, phone, card number or payment token — log the masked form.
 *
 * <pre>
 * MaskingUtil.maskEmail("john.doe@example.com") -> "j******e@example.com"
 * MaskingUtil.maskCard("4111111111111111")      -> "************1111"
 * MaskingUtil.maskToken("tok_9f8e7d6c5b")        -> "tok_******"
 * </pre>
 */
public final class MaskingUtil {

    private static final char MASK = '*';

    private MaskingUtil() {
    }

    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return email;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return repeat(email.length());
        }
        String local = email.substring(0, at);
        String domain = email.substring(at);
        if (local.length() <= 2) {
            return repeat(local.length()) + domain;
        }
        return local.charAt(0) + repeat(local.length() - 2) + local.charAt(local.length() - 1) + domain;
    }

    /** Keeps the last 4 digits only (PCI DSS allows at most first 6 / last 4). */
    public static String maskCard(String pan) {
        if (pan == null) {
            return null;
        }
        String digits = pan.replaceAll("\\D", "");
        if (digits.length() <= 4) {
            return repeat(digits.length());
        }
        return repeat(digits.length() - 4) + digits.substring(digits.length() - 4);
    }

    /** Keeps a short, non-secret prefix (e.g. {@code tok_}) so the token type is still visible. */
    public static String maskToken(String token) {
        if (token == null) {
            return null;
        }
        int underscore = token.indexOf('_');
        String prefix = underscore > 0 && underscore < 6 ? token.substring(0, underscore + 1) : "";
        return prefix + repeat(6);
    }

    private static String repeat(int count) {
        return String.valueOf(MASK).repeat(Math.max(count, 0));
    }
}
