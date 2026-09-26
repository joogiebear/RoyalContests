package com.mystipixel.royalcontests.hook;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * PlaceholderAPI, when installed.
 *
 * <p>Every call into PlaceholderAPI goes through here, behind an "is it enabled" check, so its classes
 * are only ever touched on a server that has it — RoyalContests runs fine without it, with placeholder
 * requirements reading as unmet and placeholder scoring unavailable.
 */
public final class Papi {

    private Papi() {
    }

    public static boolean available() {
        return Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    /** Fill placeholders, or return the text unchanged without PlaceholderAPI. */
    public static String apply(Player player, String text) {
        if (text == null || text.indexOf('%') < 0 || !available()) {
            return text;
        }
        try {
            return PlaceholderAPI.setPlaceholders(player, text);
        } catch (RuntimeException | LinkageError failed) {
            return text;
        }
    }

    /**
     * A placeholder read as a number — the digits in whatever it returns, so {@code "1,250"} and
     * {@code "Level 12"} both read sensibly. {@code NaN} if nothing numeric came back.
     */
    public static double number(Player player, String placeholder) {
        String value = apply(player, placeholder);
        if (value == null || value.equals(placeholder)) {
            return Double.NaN;
        }
        return parseNumber(value);
    }

    /** The number in a string, ignoring separators and words. {@code NaN} if there is none. */
    public static double parseNumber(String value) {
        StringBuilder digits = new StringBuilder();
        boolean seenDot = false;
        for (char ch : value.toCharArray()) {
            if (Character.isDigit(ch)) {
                digits.append(ch);
            } else if (ch == '.' && !seenDot) {
                digits.append(ch);
                seenDot = true;
            } else if (ch == '-' && digits.isEmpty()) {
                digits.append(ch);
            }
        }
        try {
            return digits.isEmpty() ? Double.NaN : Double.parseDouble(digits.toString());
        } catch (NumberFormatException notANumber) {
            return Double.NaN;
        }
    }
}
