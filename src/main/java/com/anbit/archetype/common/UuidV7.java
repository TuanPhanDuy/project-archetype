package com.anbit.archetype.common;

import java.security.SecureRandom;
import java.util.UUID;

/** Creates RFC 9562 version 7 UUIDs: time-ordered, so new primary keys append to the index instead of scattering. */
public final class UuidV7 {

    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidV7() {
    }

    /** A new v7 id stamped with the current time. */
    public static UUID generate() {
        return generate(System.currentTimeMillis());
    }

    static UUID generate(long epochMillis) {
        long msb = (epochMillis << 16) | 0x7000L | (RANDOM.nextInt() & 0x0FFFL);
        long lsb = (RANDOM.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(msb, lsb);
    }
}
