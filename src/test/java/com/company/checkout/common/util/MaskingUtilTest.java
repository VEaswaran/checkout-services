package com.company.checkout.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MaskingUtilTest {

    @ParameterizedTest
    @CsvSource({
            "john.doe@example.com, j******e@example.com",
            "ab@example.com,       **@example.com",
            "not-an-email,         ************"
    })
    void masksEmails(String input, String expected) {
        assertThat(MaskingUtil.maskEmail(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "4111111111111111,    ************1111",
            "4111 1111 1111 1111, ************1111"
    })
    void masksCardNumbersKeepingLastFour(String input, String expected) {
        assertThat(MaskingUtil.maskCard(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "tok_9f8e7d6c5b, tok_******",
            "secretvalue,    ******"
    })
    void masksTokens(String input, String expected) {
        assertThat(MaskingUtil.maskToken(input)).isEqualTo(expected);
    }
}
