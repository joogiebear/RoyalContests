package com.mystipixel.royalcontests;

import com.mystipixel.royalcontests.command.AdminCommand;
import com.mystipixel.royalcontests.command.PlayerCommand;
import com.mystipixel.royalcontests.config.Lang;
import com.mystipixel.royalcontests.config.Settings;
import com.mystipixel.royalcontests.contest.BracketTable;
import com.mystipixel.royalcontests.contest.ContestDisplay;
import com.mystipixel.royalcontests.contest.ContestService;
import com.mystipixel.royalcontests.contest.EventRegistry;
import com.mystipixel.royalcontests.crop.CropRegistry;
import com.mystipixel.royalcontests.crop.PlacedBlocks;
import com.mystipixel.royalcontests.gui.Menu;
import com.mystipixel.royalcontests.gui.Menus;
import com.mystipixel.royalcontests.hook.ContestExpansion;
import com.mystipixel.royalcontests.hook.Papi;
import com.mystipixel.royalcontests.scoring.HarvestListener;
import com.mystipixel.royalcontests.storage.Database;
import com.mystipixel.royalcontests.storage.RewardService;
import com.mystipixel.royalcontests.storage.StatsCache;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.logging.Level;

/**
 * RoyalContests: Jacob's-style farming contests for the Royal suite.
 *
 * <p>Replaces JacobsContest: its database tables are read as they are, and its {@code data.db} is
 * copied across on first start.
 */
public final class RoyalContests extends JavaPlugin {

    private Settings settings;
    private Lang lang;
    private CropRegistry crops;
    private EventRegistry events;
    private BracketTable brackets;
    private Database database;
    private StatsCache cache;
    private ContestService contests;
    private HarvestListener harvests;
    private Menus menus;

    @Override
    public void onEnable() {
        migrateFromJacobsContest();

        this.settings = new Settings(this);
        this.lang = new Lang(this);
        this.crops = new CropRegistry(this);
        this.events = new EventRegistry(this, settings, crops);
        this.brackets = new BracketTable(this);

        this.database = new Database(this);
        try {
            database.open(settings.storage());
        } catch (Exception failed) {
            getLogger().log(Level.SEVERE, "Could not open the database; RoyalContests is disabled.", failed);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.cache = new StatsCache(this, settings, crops);
        RewardService rewards = new RewardService(this, database, settings, lang, brackets, cache);
        ContestDisplay display = new ContestDisplay(settings, lang);
        this.contests = new ContestService(this, settings, lang, crops, events, brackets, rewards, display);
        PlacedBlocks placed = new PlacedBlocks(this, crops);
        this.harvests = new HarvestListener(settings, crops, placed, contests);
        this.menus = new Menus(this, settings, lang, contests, crops, brackets, rewards, cache);

        PluginManager plugins = getServer().getPluginManager();
        plugins.registerEvents(placed, this);
        plugins.registerEvents(harvests, this);
        plugins.registerEvents(cache, this);
        plugins.registerEvents(new Menu.Clicks(), this);

        command("royalcontests", new AdminCommand(lang, contests, events, crops, this::reloadAll));
        command("contests", new PlayerCommand(lang, menus));

        if (Papi.available()) {
            new ContestExpansion(this, "royalcontests", lang, contests, crops, brackets, cache).register();
            new ContestExpansion(this, "jacobscontest", lang, contests, crops, brackets, cache).register();
        }

        cache.start(database);
        contests.start();
        getServer().getScheduler().runTaskTimer(this, harvests::clearStale, 20L, 20L);
        getLogger().info("Ready: " + crops.all().size() + " crops, " + events.all().size() + " events, "
                + brackets.all().size() + " brackets; schedule " + settings.scheduleMode().name().toLowerCase()
                + ", scoring " + settings.scoring().name().toLowerCase() + ".");
    }

    private void command(String name, TabExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command != null) {
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
    }

    /**
     * Re-read every file. A contest already counting down or running keeps the event, crops and times it
     * was announced with; the schedule, rewards and messages pick up the new values straight away.
     */
    public void reloadAll() {
        settings.reload();
        lang.reload();
        crops.reload();
        events.reload();
        brackets.reload();
        menus.reload();
        cache.restartRefresh();
    }

    @Override
    public void onDisable() {
        if (contests != null) {
            contests.shutdown();
        }
        if (cache != null) {
            cache.stop();
        }
        if (database != null) {
            database.close();
        }
    }

    // copy (never move) JacobsContest's data.db on first start; the table layout is shared
    private void migrateFromJacobsContest() {
        File old = new File(getDataFolder().getParentFile(), "JacobsContest/data.db");
        File ours = new File(getDataFolder(), "data.db");
        if (!old.isFile() || ours.exists()) {
            return;
        }
        try {
            Files.createDirectories(getDataFolder().toPath());
            Files.copy(old.toPath(), ours.toPath());
            getLogger().info("Copied JacobsContest's data.db; stats and unclaimed rewards carry over. "
                    + "Its config files use a different layout and were not copied; see the new ones.");
        } catch (IOException failed) {
            getLogger().log(Level.WARNING, "Could not copy JacobsContest's data.db", failed);
        }
    }
}
