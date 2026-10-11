package com.mystipixel.royalcontests.config;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Every line a player can read, from {@code messages.yml}.
 *
 * <p>Set a message to {@code ""} to switch it off; empty messages are never sent.
 */
public final class Lang extends YamlFile {

    public Lang(Plugin plugin) {
        super(plugin, "messages.yml", true);
    }

    /** A message with placeholders filled; the key itself if neither the file nor the jar has it. */
    public String raw(String key, String... placeholders) {
        String text = yaml().getString(key);
        return Text.fill(text == null ? key : text, placeholders);
    }

    public void send(CommandSender to, String key, String... placeholders) {
        String text = raw(key, placeholders);
        if (!text.isEmpty()) {
            to.sendMessage(Text.color(prefix() + text));
        }
    }

    /** Send without the prefix, for lines of a list or a block of results. */
    public void sendPlain(CommandSender to, String key, String... placeholders) {
        String text = raw(key, placeholders);
        if (!text.isEmpty()) {
            to.sendMessage(Text.color(text));
        }
    }

    public void broadcast(String key, String... placeholders) {
        String text = raw(key, placeholders);
        if (text.isEmpty()) {
            return;
        }
        var line = Text.color(prefix() + text);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(line);
        }
        Bukkit.getConsoleSender().sendMessage(line);
    }

    private String prefix() {
        String prefix = yaml().getString("prefix");
        return prefix == null ? "" : prefix;
    }

    public void broadcastPlain(String key, String... placeholders) {
        String text = raw(key, placeholders);
        if (text.isEmpty()) {
            return;
        }
        var line = Text.color(text);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(line);
        }
    }
}
