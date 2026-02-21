# Jacob's Contest Plugin — Specification

## Overview
A Minecraft Paper 1.21.4+ plugin for running competitive crop farming contests. Players compete to harvest the most of a specific crop within a time limit, with percentage-based bracket rewards.

## Target
- **Paper API**: 1.21.4+
- **Java**: 21
- **Build**: Maven with shade plugin
- **Dependencies** (all `provided` scope):
  - Paper API 1.21.4
  - PlaceholderAPI 2.11.3+
- **No other plugin dependencies** — rewards are granted via configurable console commands

## Core Mechanics

### Event Lifecycle
1. **Scheduling**: Events can be started manually (`/jacobscontest start`) or auto-rotated via config
2. **Countdown**: Configurable pre-event countdown (e.g., "Contest starts in 5 minutes!")
3. **Active Phase**: Players break crops, scores tracked per player
4. **Crop Lock-in**: Each event offers multiple crops. The FIRST crop a player breaks locks them into that crop for the duration. They can only score points for that crop type.
5. **End**: Timer expires or admin runs `/jacobscontest stop`
6. **Rewards**: Players ranked by score, placed into percentage brackets, rewards distributed via commands

### Crop Tracking
- Track crop breaks using Bukkit BlockBreakEvent
- Only count fully grown crops (check Ageable.getAge() == Ageable.getMaximumAge())
- For non-ageable crops (MELON, PUMPKIN), count any break
- Track per-player: which crop they're locked to + count

### Percentage Brackets (configurable)
Players are ranked by score. Their percentile determines their bracket:
- **DIAMOND**: Top 2% of participants
- **PLATINUM**: Top 5%
- **GOLD**: Top 10%
- **SILVER**: Top 30%
- **BRONZE**: Top 60%
- **NONE**: Everyone else (100% — participated but didn't place)

The percentile thresholds are fully configurable in config.yml.

### Rewards
Each bracket has a list of commands to run. Placeholders available:
- `%player%` — player name
- `%crop%` — the crop the player was farming (lowercase, e.g., "wheat")
- `%score%` — number of crops broken
- `%bracket%` — bracket name (DIAMOND, PLATINUM, etc.)
- `%position%` — player's rank number

Example reward commands (run as console):
```
eco give %player% jacobs_ticket 35
eco give %player% gold_medal 1
eco give %player% carnival_ticket 3
eco give %player% turbo_crop_%crop%_1 1
```

## Commands
- `/jacobscontest start [event_name]` — Start a contest (uses event from config, or random from rotation)
- `/jacobscontest stop` — Force stop current contest, distribute rewards
- `/jacobscontest reload` — Reload config
- `/jacobscontest status` — Show current contest info
- Permission: `jacobscontest.admin` for all commands

## Events / Scoreboard Display
- Use **Action Bar** or **BossBar** to show live contest info to participants
- Show: crop name, player's score, time remaining
- Broadcast messages: contest starting (with countdown), contest ending, bracket results

## Config Structure (config.yml)

```yaml
# General settings
settings:
  # Prefix for all messages
  prefix: "&e&lJacob's Contest &8» &r"
  # Default contest duration
  default-duration: 10m
  # Countdown before contest starts
  countdown: 5m
  # Display method: ACTION_BAR or BOSS_BAR
  display: BOSS_BAR
  # BossBar color: BLUE, GREEN, RED, YELLOW, PURPLE, PINK, WHITE
  bossbar-color: YELLOW

# Bracket definitions (percentage thresholds)
brackets:
  DIAMOND:
    threshold: 2
    color: "&b"
    commands:
      - "eco give %player% gold_medal 1"
      - "eco give %player% silver_medal 1"
      - "eco give %player% jacobs_ticket 35"
      - "eco give %player% carnival_ticket 3"
      - "eco give %player% turbo_crop_%crop%_1 1"
  PLATINUM:
    threshold: 5
    color: "&7"
    commands:
      - "eco give %player% gold_medal 1"
      - "eco give %player% bronze_medal 1"
      - "eco give %player% jacobs_ticket 30"
      - "eco give %player% carnival_ticket 2"
      - "eco give %player% turbo_crop_%crop%_1 1"
  GOLD:
    threshold: 10
    color: "&6"
    commands:
      - "eco give %player% gold_medal 1"
      - "eco give %player% jacobs_ticket 25"
      - "eco give %player% carnival_ticket 2"
      - "eco give %player% turbo_crop_%crop%_1 1"
  SILVER:
    threshold: 30
    color: "&f"
    commands:
      - "eco give %player% silver_medal 1"
      - "eco give %player% jacobs_ticket 15"
      - "eco give %player% carnival_ticket 1"
      - "eco give %player% turbo_crop_%crop%_1 1"
  BRONZE:
    threshold: 60
    color: "&c"
    commands:
      - "eco give %player% bronze_medal 1"
      - "eco give %player% jacobs_ticket 10"
      - "eco give %player% carnival_ticket 1"
      - "eco give %player% turbo_crop_%crop%_1 1"
  NONE:
    threshold: 100
    color: "&7"
    commands:
      - "eco give %player% jacobs_ticket 1"

# Event rotation — define as many events as you want
events:
  summer_harvest:
    display-name: "&eSummer Harvest"
    duration: 10m
    countdown: 2m
    crops:
      - WHEAT
      - CARROTS
      - POTATOES
    worlds:
      - SuperiorWorld
  nether_harvest:
    display-name: "&cNether Harvest"
    duration: 8m
    countdown: 2m
    crops:
      - NETHER_WART
    worlds:
      - SuperiorWorld
  melon_madness:
    display-name: "&aMelon Madness"
    duration: 5m
    countdown: 1m
    crops:
      - MELON
      - PUMPKIN
    worlds:
      - SuperiorWorld

# Rotation order (cycles through these)
rotation:
  - summer_harvest
  - nether_harvest
  - melon_madness

# Messages (all support & color codes)
messages:
  contest-starting: "&e&lJacob's Contest &fstarts in &6%time%&f! Crops: &6%crops%"
  contest-started: "&e&lJacob's Contest &fhas begun! Break &6%crops% &fto compete!"
  contest-ended: "&e&lJacob's Contest &fhas ended! Results:"
  bracket-result: "  %bracket_color%%bracket% &8» &f%player% &7(%score% crops)"
  player-locked: "&fYou are now competing with &6%crop%&f!"
  time-remaining: "&e%time% &fremaining | &6%crop%&f: &e%score%"
```

## PlaceholderAPI Expansion
Register these placeholders:
- `%jacobscontest_active%` — true/false
- `%jacobscontest_crop%` — player's locked crop (or empty)
- `%jacobscontest_score%` — player's current score
- `%jacobscontest_position%` — player's current rank
- `%jacobscontest_bracket%` — player's current bracket
- `%jacobscontest_timeleft%` — time remaining
- `%jacobscontest_event%` — current event display name

## Technical Notes
- Use Paper's Adventure API for messages (not deprecated ChatColor)
- Use BukkitRunnable for timers
- Store contest state in memory only (no persistence needed — contests are ephemeral)
- Package: `com.jacobscontest`
- Main class: `JacobsContest`
- Plugin name in plugin.yml: `JacobsContest`
- Use Paper plugin.yml format with `api-version: '1.21'`
