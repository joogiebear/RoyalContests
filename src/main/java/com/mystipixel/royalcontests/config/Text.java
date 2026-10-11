package com.mystipixel.royalcontests.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Locale;

/**
 * Text as the Royal suite writes it: {@code &} colour codes and {@code &#rrggbb} hex, with {@code %name%}
 * placeholders passed as pairs.
 */
public final class Text {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&').hexColors().build();

    private Text() {
    }

    public static Component color(String input) {
        return LEGACY.deserialize(input == null ? "" : input);
    }

    /** Coloured and not italic, for item names and lore (which render italic by default). */
    public static Component item(String input) {
        return color(input).decoration(TextDecoration.ITALIC, false);
    }

    /** Replace {@code %name%} pairs. A null value becomes empty. */
    public static String fill(String text, String... placeholders) {
        if (text == null) {
            return "";
        }
        String out = text;
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            String value = placeholders[i + 1];
            out = out.replace(placeholders[i], value == null ? "" : value);
        }
        return out;
    }

    /** {@code SUGAR_CANE} → {@code Sugar Cane}. */
    public static String titleCase(String id) {
        StringBuilder out = new StringBuilder();
        for (String word : id.toLowerCase(Locale.ROOT).split("[_ ]+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }
}
