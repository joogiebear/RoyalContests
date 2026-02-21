package com.jacobscontest.commands;

import com.jacobscontest.JacobsContest;
import com.jacobscontest.contest.ContestEvent;
import com.jacobscontest.contest.ContestManager;
import com.jacobscontest.contest.PlayerContestData;
import com.jacobscontest.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles /jacobscontest commands.
 */
public class ContestCommand implements CommandExecutor, TabCompleter {

    private final JacobsContest plugin;

    public ContestCommand(JacobsContest plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        ContestManager manager = plugin.getContestManager();
        String sub = args[0].toLowerCase();

        switch (sub) {
            case "start" -> {
                if (manager.isContestActive() || manager.isCountdownActive()) {
                    sender.sendMessage(MessageUtil.toComponent(
                            getMsg("contest-already-active")));
                    return true;
                }

                String eventId;
                if (args.length >= 2) {
                    eventId = args[1].toLowerCase();
                    if (!manager.getEvents().containsKey(eventId)) {
                        sender.sendMessage(MessageUtil.toComponent(
                                getMsg("event-not-found").replace("%event%", args[1])));
                        return true;
                    }
                } else {
                    // Use next in rotation
                    if (!manager.startNextRotation()) {
                        sender.sendMessage(MessageUtil.toComponent(
                                "&cNo events configured in rotation!"));
                        return true;
                    }
                    return true;
                }

                if (manager.startContest(eventId)) {
                    sender.sendMessage(MessageUtil.toComponent(
                            "&aStarting contest: " + eventId));
                } else {
                    sender.sendMessage(MessageUtil.toComponent(
                            "&cFailed to start contest."));
                }
            }

            case "stop" -> {
                if (!manager.isContestActive() && !manager.isCountdownActive()) {
                    sender.sendMessage(MessageUtil.toComponent(
                            getMsg("no-active-contest")));
                    return true;
                }
                manager.forceStop();
                sender.sendMessage(MessageUtil.toComponent("&aContest stopped."));
            }

            case "reload" -> {
                if (manager.isContestActive()) {
                    manager.forceStop();
                }
                manager.loadConfig();
                sender.sendMessage(MessageUtil.toComponent("&aJacob's Contest config reloaded!"));
            }

            case "status" -> {
                if (!manager.isContestActive()) {
                    sender.sendMessage(MessageUtil.toComponent(
                            getMsg("no-active-contest")));
                    return true;
                }

                ContestEvent event = manager.getActiveEvent();
                long remaining = manager.getTimeRemaining();
                int participants = manager.getParticipants().size();

                sender.sendMessage(MessageUtil.toComponent("&e&l═══ Contest Status ═══"));
                sender.sendMessage(MessageUtil.toComponent("&fEvent: " + event.displayName()));
                sender.sendMessage(MessageUtil.toComponent("&fCrops: &6" + event.cropsDisplayString()));
                sender.sendMessage(MessageUtil.toComponent("&fTime remaining: &6" + ContestManager.formatTime(remaining)));
                sender.sendMessage(MessageUtil.toComponent("&fParticipants: &6" + participants));

                // Top 5
                List<PlayerContestData> top = manager.getParticipants().values().stream()
                        .sorted(Comparator.comparingInt(PlayerContestData::getScore).reversed())
                        .limit(5)
                        .collect(Collectors.toList());

                if (!top.isEmpty()) {
                    sender.sendMessage(MessageUtil.toComponent("&fTop players:"));
                    for (int i = 0; i < top.size(); i++) {
                        PlayerContestData d = top.get(i);
                        sender.sendMessage(MessageUtil.toComponent(
                                "  &6#" + (i + 1) + " &f" + d.getPlayerName()
                                        + " &7(" + d.getScore() + " " + d.getCropName() + ")"));
                    }
                }
            }

            default -> sendHelp(sender);
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(MessageUtil.toComponent("&e&lJacob's Contest Commands:"));
        sender.sendMessage(MessageUtil.toComponent("  &6/jc start [event] &8- &fStart a contest"));
        sender.sendMessage(MessageUtil.toComponent("  &6/jc stop &8- &fStop the current contest"));
        sender.sendMessage(MessageUtil.toComponent("  &6/jc status &8- &fShow contest status"));
        sender.sendMessage(MessageUtil.toComponent("  &6/jc reload &8- &fReload config"));

        // List available events
        Map<String, ContestEvent> events = plugin.getContestManager().getEvents();
        if (!events.isEmpty()) {
            sender.sendMessage(MessageUtil.toComponent("  &fAvailable events:"));
            for (Map.Entry<String, ContestEvent> entry : events.entrySet()) {
                sender.sendMessage(MessageUtil.toComponent(
                        "    &7- &f" + entry.getKey() + " &7(" + entry.getValue().displayName() + "&7)"));
            }
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return filterStartsWith(List.of("start", "stop", "reload", "status"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("start")) {
            return filterStartsWith(
                    new ArrayList<>(plugin.getContestManager().getEvents().keySet()), args[1]);
        }
        return Collections.emptyList();
    }

    private List<String> filterStartsWith(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        return options.stream()
                .filter(s -> s.toLowerCase().startsWith(lower))
                .collect(Collectors.toList());
    }

    private String getMsg(String key) {
        return plugin.getConfigManager().getMessage(key);
    }
}
