package com.mystipixel.royalcontests.command;

import com.mystipixel.royalcontests.config.Lang;
import com.mystipixel.royalcontests.gui.Menus;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /contests [claim|stats|calendar|top]} — the contest hub, or straight to one of its pages.
 */
public final class PlayerCommand implements TabExecutor {

    private static final List<String> PAGES = List.of("claim", "stats", "calendar", "top");

    private final Lang lang;
    private final Menus menus;

    public PlayerCommand(Lang lang, Menus menus) {
        this.lang = lang;
        this.menus = menus;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            lang.send(sender, "players-only");
            return true;
        }
        String page = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (page) {
            case "claim" -> menus.openClaim(player);
            case "stats" -> menus.openStats(player);
            case "calendar" -> menus.openCalendar(player);
            case "top" -> menus.openLeaderboards(player);
            default -> menus.openMain(player);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        List<String> out = new ArrayList<>(PAGES);
        out.removeIf(page -> !page.startsWith(args[0].toLowerCase(Locale.ROOT)));
        return out;
    }
}
