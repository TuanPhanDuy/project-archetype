package com.anbit.archetype.common.error;

/**
 * Thrown when an {@code Idempotency-Key} header is reused with a request body that hashes
 * differently from the one originally associated with that key. Mapped to HTTP 409 by
 * {@link GlobalExceptionHandler}. The message (key + hash mismatch context) is for server
 * logs only — it is never echoed back to the client.
 */
public class IdempotencyKeyConflictException extends RuntimeException {

    public IdempotencyKeyConflictException(String message) {
        super(message);
    }

    public static IdempotencyKeyConflictException of(String key) {
        return new IdempotencyKeyConflictException(
                "Idempotency key " + key + " was already used with a different request body");
    }
}
