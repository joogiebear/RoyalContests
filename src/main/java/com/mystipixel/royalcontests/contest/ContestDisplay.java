package com.mystipixel.royalcontests.contest;

import com.mystipixel.royalcontests.config.Lang;
import com.mystipixel.royalcontests.config.Settings;
import com.mystipixel.royalcontests.config.Text;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * What players see while a contest counts down and runs: a boss bar or an action bar, one per player.
 *
 * <p>One bar <em>per player</em>. The old plugin shared a single bar and renamed it once per player in a
 * loop, so everyone ended up reading the last player's score.
 */
public final class ContestDisplay {

    private final Settings settings;
    private final Lang lang;
    private final Map<UUID, BossBar> bars = new HashMap<>();

    public ContestDisplay(Settings settings, Lang lang) {
        this.settings = settings;
        this.lang = lang;
    }

    /**
     * Refresh every online player's display.
     *
     * @param line     the text for one player, already filled, or null to show them nothing
     * @param progress how full the bar is, 0 to 1
     */
    public void update(Function<Player, String> line, float progress) {
        Settings.Display type = settings.display();
        if (type == Settings.Display.NONE) {
            clear();
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            String text = line.apply(player);
            if (text == null || text.isEmpty()) {
                hide(player);
                continue;
            }
            if (type == Settings.Display.ACTION_BAR) {
                hide(player);
                player.sendActionBar(Text.color(text));
                continue;
            }
            BossBar bar = bars.computeIfAbsent(player.getUniqueId(), id -> {
                BossBar created = BossBar.bossBar(Text.color(text), 1f, color(), overlay());
                player.showBossBar(created);
                return created;
            });
            bar.name(Text.color(text));
            bar.progress(Math.clamp(progress, 0f, 1f));
            bar.color(color());
            bar.overlay(overlay());
            player.showBossBar(bar);
        }
        // Players who left keep no bar.
        Iterator<Map.Entry<UUID, BossBar>> it = bars.entrySet().iterator();
        while (it.hasNext()) {
            if (Bukkit.getPlayer(it.next().getKey()) == null) {
                it.remove();
            }
        }
    }

    private void hide(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }
    }

    public void clear() {
        for (Map.Entry<UUID, BossBar> entry : bars.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                player.hideBossBar(entry.getValue());
            }
        }
        bars.clear();
    }

    private BossBar.Color color() {
        try {
            return BossBar.Color.valueOf(settings.bossBarColor().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return BossBar.Color.YELLOW;
        }
    }

    private BossBar.Overlay overlay() {
        try {
            return BossBar.Overlay.valueOf(settings.bossBarOverlay().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return BossBar.Overlay.PROGRESS;
        }
    }

    public Lang lang() {
        return lang;
    }
}
