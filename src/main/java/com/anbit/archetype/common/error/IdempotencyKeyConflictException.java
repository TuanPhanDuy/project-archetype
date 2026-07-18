package com.anbit.archetype.common.error;

import java.util.regex.Pattern;

/**
 * Thrown when an {@code Idempotency-Key} header is reused with a request body that hashes
 * differently from the one originally associated with that key. Mapped to HTTP 409 by
 * {@link GlobalExceptionHandler}. The message (key + hash mismatch context) is for server
 * logs only — it is never echoed back to the client.
 */
public class IdempotencyKeyConflictException extends RuntimeException {

    // Control, format (e.g. bidi overrides), and line/paragraph-separator characters — the
    // key is an arbitrary, attacker-controlled header value, so strip anything that could
    // forge a fake log line or spoof terminal output before it ever reaches a log message.
    private static final Pattern UNSAFE_FOR_LOG = Pattern.compile("[\\p{Cntrl}\\p{Cf}\\p{Zl}\\p{Zp}]");

    public IdempotencyKeyConflictException(String message) {
        super(message);
    }

    public static IdempotencyKeyConflictException of(String key) {
        return new IdempotencyKeyConflictException(
                "Idempotency key " + sanitizeForLog(key) + " was already used with a different request body");
    }

    private static String sanitizeForLog(String key) {
        return UNSAFE_FOR_LOG.matcher(key).replaceAll("");
    }
}
