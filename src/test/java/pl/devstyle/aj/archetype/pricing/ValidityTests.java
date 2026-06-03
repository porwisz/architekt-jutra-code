package pl.devstyle.aj.archetype.pricing;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidityTests {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant T1 = Instant.parse("2026-02-01T00:00:00Z");
    private static final Instant T2 = Instant.parse("2026-03-01T00:00:00Z");
    private static final Instant T3 = Instant.parse("2026-04-01T00:00:00Z");

    @Test
    void covers_instantAtValidFrom_returnsTrue() {
        var validity = new Validity(T0, T2);

        assertThat(validity.covers(T0)).isTrue();
    }

    @Test
    void covers_instantAtValidTo_returnsFalse() {
        var validity = new Validity(T0, T2);

        assertThat(validity.covers(T2)).isFalse();
    }

    @Test
    void assertNonOverlapping_overlappingWindows_throwsIllegalArgumentException() {
        var a = new Validity(T0, T2);
        var b = new Validity(T1, T3); // overlaps with a
        var c = new Validity(T3, Instant.parse("2026-05-01T00:00:00Z"));

        assertThatThrownBy(() -> Validity.assertNonOverlapping(List.of(a, b, c)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(a.toString())
                .hasMessageContaining(b.toString());
    }
}
