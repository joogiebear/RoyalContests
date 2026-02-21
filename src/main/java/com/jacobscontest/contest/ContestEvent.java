package com.jacobscontest.contest;

import org.bukkit.Material;

import java.util.List;

/**
 * Represents a configured contest event (e.g., "Summer Harvest").
 */
public record ContestEvent(
        String id,
        String displayName,
        long durationTicks,
        long countdownTicks,
        List<Material> crops,
        List<String> worlds
) {

    public long durationSeconds() {
        return durationTicks / 20;
    }

    public long countdownSeconds() {
        return countdownTicks / 20;
    }

    public boolean isCropValid(Material material) {
        return crops.contains(material);
    }

    public boolean isWorldValid(String worldName) {
        return worlds.contains(worldName);
    }

    public String cropsDisplayString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < crops.size(); i++) {
            if (i > 0) sb.append(", ");
            String name = crops.get(i).name();
            sb.append(name.substring(0, 1).toUpperCase())
              .append(name.substring(1).toLowerCase().replace('_', ' '));
        }
        return sb.toString();
    }
}
