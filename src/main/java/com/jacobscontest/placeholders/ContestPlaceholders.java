package com.jacobscontest.placeholders;

import com.jacobscontest.JacobsContest;
import com.jacobscontest.contest.Bracket;
import com.jacobscontest.contest.ContestManager;
import com.jacobscontest.contest.ContestEvent;
import com.jacobscontest.contest.PlayerContestData;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PlaceholderAPI expansion for Jacob's Contest.
 * 
 * Placeholders:
 *   %jacobscontest_active%    - true/false
 *   %jacobscontest_crop%      - player's locked crop name
 *   %jacobscontest_score%     - player's current score
 *   %jacobscontest_position%  - player's current rank
 *   %jacobscontest_bracket%   - player's current bracket name
 *   %jacobscontest_timeleft%  - time remaining
 *   %jacobscontest_event%     - current event display name
 */
public class ContestPlaceholders extends PlaceholderExpansion {

    private final JacobsContest plugin;

    public ContestPlaceholders(JacobsContest plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "jacobscontest";
    }

    @Override
    public @NotNull String getAuthor() {
        return "VoltexaLeader";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String identifier) {
        ContestManager manager = plugin.getContestManager();

        return switch (identifier.toLowerCase()) {
            case "active" -> String.valueOf(manager.isContestActive());

            case "crop" -> {
                if (player == null || !manager.isContestActive()) yield "";
                PlayerContestData data = manager.getPlayerData(player.getUniqueId());
                yield data != null ? data.getCropName() : "";
            }

            case "score" -> {
                if (player == null || !manager.isContestActive()) yield "0";
                PlayerContestData data = manager.getPlayerData(player.getUniqueId());
                yield data != null ? String.valueOf(data.getScore()) : "0";
            }

            case "position" -> {
                if (player == null || !manager.isContestActive()) yield "-";
                int pos = manager.getPlayerPosition(player.getUniqueId());
                yield pos > 0 ? String.valueOf(pos) : "-";
            }

            case "bracket" -> {
                if (player == null || !manager.isContestActive()) yield "";
                Bracket bracket = manager.getPlayerBracket(player.getUniqueId());
                yield bracket != null ? bracket.name() : "";
            }

            case "timeleft" -> {
                if (!manager.isContestActive()) yield "0s";
                yield ContestManager.formatTime(manager.getTimeRemaining());
            }

            case "event" -> {
                ContestEvent event = manager.getActiveEvent();
                yield event != null ? event.displayName() : "";
            }

            default -> null;
        };
    }
}
