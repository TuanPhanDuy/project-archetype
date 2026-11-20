package com.anbit.archetype.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class UuidV7Test {

    @Test
    void setsVersion7AndRfcVariant() {
        UUID id = UuidV7.generate();

        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    void carriesTheTimestampInTheTop48Bits() {
        long millis = 1_795_000_000_000L;

        UUID id = UuidV7.generate(millis);

        assertThat(id.getMostSignificantBits() >>> 16).isEqualTo(millis);
    }

    @Test
    void idsFromLaterMillisecondsSortAfterEarlierOnes() {
        UUID earlier = UuidV7.generate(1_795_000_000_000L);
        UUID later = UuidV7.generate(1_795_000_000_001L);

        assertThat(earlier.toString()).isLessThan(later.toString());
    }
}
