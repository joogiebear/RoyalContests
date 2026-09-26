package com.mystipixel.royalcontests.config;

import java.util.Locale;

/**
 * Durations as admins write them: {@code 20m}, {@code 1h30m}, {@code 45s}, or {@code 0} for none — the
 * same shorthand the rest of the Royal suite uses.
 */
public final class Durations {

    private Durations() {
    }

    /**
     * Parse into milliseconds. A bare number is seconds. Returns {@code fallback} for blank or unreadable
     * input, so a typo falls back to a sensible value instead of a zero-length contest.
     */
    public static long parseMillis(String raw, long fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        long total = 0;
        long number = 0;
        boolean digits = false;
        boolean any = false;
        for (char ch : s.toCharArray()) {
            if (Character.isDigit(ch)) {
                number = number * 10 + (ch - '0');
                digits = true;
                continue;
            }
            if (ch == ' ') {
                continue;
            }
            long unit = switch (ch) {
                case 'd' -> 86_400_000L;
                case 'h' -> 3_600_000L;
                case 'm' -> 60_000L;
                case 's' -> 1_000L;
                default -> -1L;
            };
            if (unit < 0 || !digits) {
                return fallback;
            }
            total += number * unit;
            number = 0;
            digits = false;
            any = true;
        }
        if (digits) {
            total += number * 1_000L;
            any = true;
        }
        return any ? total : fallback;
    }

    /** Format for players: {@code 1h 5m}, {@code 30s} — never more than two units. */
    public static String format(long millis) {
        long seconds = Math.max(0, (millis + 999) / 1000);
        long[][] units = {{seconds / 86_400, 'd'}, {seconds % 86_400 / 3_600, 'h'},
                {seconds % 3_600 / 60, 'm'}, {seconds % 60, 's'}};
        StringBuilder out = new StringBuilder();
        int parts = 0;
        for (long[] unit : units) {
            if (unit[0] > 0 && parts < 2) {
                if (!out.isEmpty()) {
                    out.append(' ');
                }
                out.append(unit[0]).append((char) unit[1]);
                parts++;
            }
        }
        return out.isEmpty() ? "0s" : out.toString();
    }
}
