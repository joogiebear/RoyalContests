package com.jacobscontest.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Utility for converting legacy color code strings to Adventure components.
 */
public final class MessageUtil {

    private static final LegacyComponentSerializer SERIALIZER =
            LegacyComponentSerializer.legacyAmpersand();

    private MessageUtil() {}

    /**
     * Convert a string with & color codes to an Adventure Component.
     */
    public static Component toComponent(String message) {
        if (message == null || message.isEmpty()) return Component.empty();
        return SERIALIZER.deserialize(message);
    }

    /**
     * Convert a string with & color codes to a legacy § string.
     * Use only when Adventure components aren't supported.
     */
    public static String colorize(String message) {
        if (message == null) return "";
        return message.replace('&', '§');
    }
}
