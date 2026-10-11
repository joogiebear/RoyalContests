package com.mystipixel.royalcontests.command;

import com.mystipixel.royalcontests.config.Durations;
import com.mystipixel.royalcontests.config.Lang;
import com.mystipixel.royalcontests.contest.Contest;
import com.mystipixel.royalcontests.contest.ContestEvent;
import com.mystipixel.royalcontests.contest.ContestService;
import com.mystipixel.royalcontests.contest.EventRegistry;
import com.mystipixel.royalcontests.contest.Participant;
import com.mystipixel.royalcontests.contest.Ranking;
import com.mystipixel.royalcontests.crop.CropType;
import com.mystipixel.royalcontests.crop.CropRegistry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * {@code /royalcontests} (also {@code /jacobscontest}, {@code /jc}): running contests by hand.
 *
 * <pre>
 *   start [event] [now]   start a contest; "now" skips the countdown
 *   stop                  end the running contest and pay it out
 *   cancel                drop the countdown or contest, no results
 *   status                what is running and who is winning
 *   calendar              the next scheduled contests
 *   reload                re-read every config file (a running contest is left alone)
 * </pre>
 */
public final class AdminCommand implements TabExecutor {

    private final Lang lang;
    private final ContestService contests;
    private final EventRegistry events;
    private final CropRegistry crops;
    private final Runnable reload;

    public AdminCommand(Lang lang, ContestService contests, EventRegistry events, CropRegistry crops, Runnable reload) {
        this.lang = lang;
        this.contests = contests;
        this.events = events;
        this.crops = crops;
        this.reload = reload;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String verb = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (verb) {
            case "start" -> start(sender, args);
            case "stop" -> lang.send(sender, contests.stop() ? "admin-stopped" : "admin-nothing-running");
            case "cancel" -> lang.send(sender, contests.cancel() ? "admin-cancelled" : "admin-nothing-running");
            case "status" -> status(sender);
            case "calendar" -> calendar(sender);
            case "reload" -> {
                reload.run();
                lang.send(sender, "admin-reloaded");
            }
            default -> lang.sendPlain(sender, "admin-help");
        }
        return true;
    }

    private void start(CommandSender sender, String[] args) {
        ContestEvent event = null;
        boolean now = false;
        for (int i = 1; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("now")) {
                now = true;
            } else {
                event = events.get(args[i]);
                if (event == null) {
                    lang.send(sender, "admin-unknown-event", "%event%", args[i]);
                    return;
                }
            }
        }
        if (!contests.startNow(event, now)) {
            lang.send(sender, "admin-already-running");
            return;
        }
        lang.send(sender, "admin-started", "%event%", contests.contest().event().displayName());
    }

    private void status(CommandSender sender) {
        Contest contest = contests.contest();
        if (contest == null) {
            ContestService.Planned next = contests.next(System.currentTimeMillis());
            if (next == null) {
                lang.send(sender, "admin-status-manual");
            } else {
                lang.send(sender, "admin-status-idle", "%event%", next.event().displayName(),
                        "%crops%", contests.cropNames(next.crops()),
                        "%time%", Durations.format(next.startAt() - System.currentTimeMillis()));
            }
            return;
        }
        lang.send(sender, "admin-status", "%event%", contest.event().displayName(),
                "%state%", contests.state().name().toLowerCase(Locale.ROOT),
                "%crops%", contests.cropNames(contest.crops()), "%time%", Durations.format(contests.timeLeft()),
                "%participants%", String.valueOf(contest.participantCount()));
        for (String cropId : contest.crops()) {
            CropType crop = crops.get(cropId);
            List<Participant> top = new ArrayList<>(contest.entries(cropId));
            top.sort(Comparator.comparingLong(Participant::score).reversed());
            for (int i = 0; i < Math.min(3, top.size()); i++) {
                Participant entry = top.get(i);
                Ranking.Placed<Participant> placed = contests.placing(entry);
                lang.sendPlain(sender, "admin-status-line", "%crop%", crop == null ? cropId : crop.displayName(),
                        "%position%", String.valueOf(i + 1), "%player%", entry.name(),
                        "%score%", String.valueOf(entry.score()),
                        "%bracket%", placed == null || placed.bracket() == null
                                ? lang.raw("bracket-none") : placed.bracket().displayName());
            }
        }
    }

    private void calendar(CommandSender sender) {
        List<ContestService.Planned> upcoming = contests.upcoming(5);
        if (upcoming.isEmpty()) {
            lang.send(sender, "admin-status-manual");
            return;
        }
        long now = System.currentTimeMillis();
        for (ContestService.Planned planned : upcoming) {
            lang.sendPlain(sender, "calendar-line", "%event%", planned.event().displayName(),
                    "%crops%", contests.cropNames(planned.crops()), "%in%", Durations.format(planned.startAt() - now));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length <= 1) {
            options.addAll(List.of("start", "stop", "cancel", "status", "calendar", "reload"));
        } else if (args[0].equalsIgnoreCase("start")) {
            for (ContestEvent event : events.all()) {
                options.add(event.id());
            }
            options.add("now");
        }
        String typed = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(option -> !option.startsWith(typed));
        return options;
    }
}
