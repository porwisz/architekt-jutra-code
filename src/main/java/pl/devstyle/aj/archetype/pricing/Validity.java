package pl.devstyle.aj.archetype.pricing;

import java.time.Instant;
import java.util.List;

public record Validity(Instant validFrom, Instant validTo) {

    public boolean covers(Instant t) {
        return !t.isBefore(validFrom) && t.isBefore(validTo);
    }

    public static boolean overlaps(Validity a, Validity b) {
        return a.validFrom.isBefore(b.validTo) && b.validFrom.isBefore(a.validTo);
    }

    public static void assertNonOverlapping(List<Validity> windows) {
        for (int i = 0; i < windows.size(); i++) {
            for (int j = i + 1; j < windows.size(); j++) {
                var a = windows.get(i);
                var b = windows.get(j);
                if (overlaps(a, b)) {
                    throw new IllegalArgumentException(
                            "Overlapping validity windows: " + a + " and " + b);
                }
            }
        }
    }
}
