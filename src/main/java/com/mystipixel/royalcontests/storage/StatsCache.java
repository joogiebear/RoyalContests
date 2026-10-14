package com.mystipixel.royalcontests.storage;

import com.mystipixel.royalcontests.config.Settings;
import com.mystipixel.royalcontests.crop.CropRegistry;
import com.mystipixel.royalcontests.crop.CropType;
import com.mystipixel.royalcontests.storage.Records.Leader;
import com.mystipixel.royalcontests.storage.Records.PlayerStats;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stats, unclaimed counts and leaderboards held in memory, for placeholders and menus.
 *
 * <p>PlaceholderAPI asks for values on the server thread, often every tick for a scoreboard, so they
 * cannot come from the database directly. Online players' stats are loaded when they join and reloaded
 * after anything changes them; leaderboards are refreshed on a timer.
 */
public final class StatsCache implements Listener {

    private final Plugin plugin;
    private final Settings settings;
    private final CropRegistry crops;
    private Database database;
    private final Map<UUID, Map<String, PlayerStats>> stats = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> unclaimed = new ConcurrentHashMap<>();
    private final Map<String, List<Leader>> leaders = new ConcurrentHashMap<>();
    private BukkitTask refresher;

    public StatsCache(Plugin plugin, Settings settings, CropRegistry crops) {
        this.plugin = plugin;
        this.settings = settings;
        this.crops = crops;
    }

    public void start(Database database) {
        this.database = database;
        for (Player player : Bukkit.getOnlinePlayers()) {
            load(player.getUniqueId());
        }
        restartRefresh();
    }

    /** Re-read the refresh interval, e.g. after a reload. */
    public void restartRefresh() {
        if (refresher != null) {
            refresher.cancel();
        }
        long period = settings.leaderboardRefreshSeconds() * 20L;
        refresher = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshLeaderboards, 40L, period);
    }

    public void stop() {
        if (refresher != null) {
            refresher.cancel();
            refresher = null;
        }
    }

    private void refreshLeaderboards() {
        for (CropType crop : crops.all()) {
            // names missing from the results are looked up on the server thread, not the database worker
            database.leaderboard(crop.id(), settings.leaderboardSize()).thenAccept(rows -> {
                if (plugin.isEnabled()) {
                    Bukkit.getScheduler().runTask(plugin, () -> leaders.put(crop.id(), named(rows)));
                }
            });
        }
    }

    private static List<Leader> named(List<Leader> rows) {
        List<Leader> named = new ArrayList<>(rows.size());
        for (Leader row : rows) {
            String name = row.name();
            if ("?".equals(name)) {
                OfflinePlayer offline = Bukkit.getOfflinePlayer(row.player());
                name = offline.getName() == null ? "?" : offline.getName();
            }
            named.add(new Leader(row.player(), name, row.score()));
        }
        return named;
    }

    /** (Re)load one player's stats and unclaimed count. */
    public void invalidate(UUID player) {
        if (Bukkit.getPlayer(player) != null) {
            load(player);
        }
    }

    private void load(UUID player) {
        if (database == null) {
            return;
        }
        database.stats(player).thenAccept(rows -> {
            Map<String, PlayerStats> byCrop = new ConcurrentHashMap<>();
            for (PlayerStats row : rows) {
                byCrop.put(row.crop(), row);
            }
            stats.put(player, byCrop);
        });
        database.pendingCount(player).thenAccept(count -> unclaimed.put(player, count));
    }

    public PlayerStats stats(UUID player, String crop) {
        Map<String, PlayerStats> byCrop = stats.get(player);
        return byCrop == null ? null : byCrop.get(crop);
    }

    public Map<String, PlayerStats> allStats(UUID player) {
        return stats.getOrDefault(player, Map.of());
    }

    public int unclaimed(UUID player) {
        return unclaimed.getOrDefault(player, 0);
    }

    public List<Leader> leaders(String crop) {
        return leaders.getOrDefault(crop, List.of());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        load(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stats.remove(event.getPlayer().getUniqueId());
        unclaimed.remove(event.getPlayer().getUniqueId());
    }
}
