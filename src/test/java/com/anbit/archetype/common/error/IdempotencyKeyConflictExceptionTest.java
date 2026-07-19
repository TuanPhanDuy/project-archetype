package com.anbit.archetype.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The exception's message is server-log-only (see {@link GlobalExceptionHandler}), but the
 * {@code key} it embeds is an arbitrary, attacker-controlled header value — these tests
 * guard against log forging/spoofing via control or format characters smuggled in the key.
 */
class IdempotencyKeyConflictExceptionTest {

    // Built from numeric code points at runtime, deliberately not written as a literal
    // character (or an escape sequence for one) anywhere in this source file: static
    // analyzers rightly flag a raw bidirectional-override character on sight as a potential
    // "Trojan Source" attack, and a test fixture proving we strip it is no exception.
    // RIGHT-TO-LEFT OVERRIDE (Cf)
    private static final String RIGHT_TO_LEFT_OVERRIDE = String.valueOf((char) 0x202E);
    // LINE SEPARATOR (Zl)
    private static final String UNICODE_LINE_SEPARATOR = String.valueOf((char) 0x2028);

    @Test
    void stripsAsciiControlCharactersFromLoggedMessage() {
        String key = "abc\ndef\r\tghi";

        String message = IdempotencyKeyConflictException.of(key).getMessage();

        assertThat(message).doesNotContain("\n", "\r", "\t").contains("abcdefghi");
    }

    @Test
    void stripsBidiOverrideAndUnicodeLineSeparatorCharacters() {
        // These can spoof/forge log output even though neither is a classic ASCII control
        // character. An ordinary space (U+0020) is deliberately NOT stripped by this code.
        String key = "abc" + RIGHT_TO_LEFT_OVERRIDE + "def" + UNICODE_LINE_SEPARATOR + "ghi";

        String message = IdempotencyKeyConflictException.of(key).getMessage();

        assertThat(message)
                .doesNotContain(RIGHT_TO_LEFT_OVERRIDE, UNICODE_LINE_SEPARATOR)
                .contains("abcdefghi");
    }

    @Test
    void leavesOrdinaryKeysUnchanged() {
        String key = "3fa85f64-5717-4562-b3fc-2c963f66afa6";

        String message = IdempotencyKeyConflictException.of(key).getMessage();

        assertThat(message).contains(key);
    }
}
