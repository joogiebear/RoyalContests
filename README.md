# RoyalContests

Jacob's-style farming contests for the Royal suite. This replaces **JacobsContest** and carries its
data over.

Contests run on a schedule, in the worlds you choose (every RoyalGarden Garden by default), about a
few crops at a time. Each crop is ranked on its own. You reach a bracket by percentile *or* by place,
and rewards wait in `/contests` until you claim them. Every value is in a YAML file.

Targets the same stack as the rest of the suite: **Paper 26.2**, **Java 25**, optional PlaceholderAPI.

## Building

```
mvn clean package      # -> target/RoyalContests.jar
```

CI builds every push (`.github/workflows/build.yml`).

## Files

| File | What it holds |
|---|---|
| `config.yml` | schedule, contest rules, scoring mode, requirements, display, sounds, storage |
| `crops.yml` | what counts as each crop: blocks, drops, collection id, points, ripeness and placement rules |
| `events.yml` | the kinds of contest: crops (or N random of them), duration, countdown, worlds, rotation |
| `brackets.yml` | reward brackets, best first: top-percent / top-positions, commands, messages |
| `messages.yml` | every player-facing line |
| `menus.yml` | every menu item, title and slot |

`/royalcontests reload` re-reads them all. A contest already running keeps the rules it started with.

## How it works

**Schedule.** `interval` runs contests on the clock (every `schedule.interval`, shifted by `offset`).
Each slot's event and crops are fixed by the slot number, so the calendar is exact and restarts change
nothing. `break` waits `schedule.break` after each contest instead. `manual` only runs contests when
an admin starts one.

**What counts.** A block counts only if a contest is running in a contest world, the block is one of
that contest's crops, it is fully grown (for crops with an age), no player placed it (for crops
marked `natural-only`), the player isn't in creative, and it isn't inside a RoyalRegen zone (set
`scoring.count-regen-zones: true` to count those). Sugar cane and cactus count the whole
column that falls. Placed blocks are tracked per chunk and follow pistons.

**Scoring modes.** `blocks`: points per block. `drops`: crop items actually dropped, so Fortune
counts. `placeholder`: the change in a collection placeholder (AuroraCollections, EcoCollections…)
since the player joined.

**Lock-in.** With `contest.lock-in`, your first scoring crop is the only one you compete in, as on
Hypixel. Without it, you compete in every crop you farm, each ranked separately.

**Brackets.** Each crop is ranked among players who reached `contest.minimum-score`. Tied scores
share a place. You get the first bracket in `brackets.yml` you reach, by `top-percent` or
`top-positions`.

**Rewards.** A reward is saved with its commands already filled in. Claiming is atomic in the
database, so a reward pays out once however fast someone clicks. `rewards.auto-claim` gives rewards
straight away to players who are online when the contest ends.

## Commands and permissions

| Command | Permission | |
|---|---|---|
| `/contests [claim\|stats\|calendar\|top]` | `royalcontests.menu` (everyone) | hub, rewards, stats, calendar, leaderboards |
| `/royalcontests start [event] [now]` | `royalcontests.admin` (op) | start now; `now` skips the countdown |
| `/royalcontests stop` / `cancel` | admin | end and pay out / drop with no results |
| `/royalcontests status` / `calendar` / `reload` | admin | |

`/jacobscontest` and `/jc` still work as aliases.

## Placeholders

`%royalcontests_<x>%`, also answered as `%jacobscontest_<x>%` for old scoreboards:

`active`, `state`, `event`, `crops`, `time_left`, `crop`, `score`, `position`, `participants`,
`bracket`, `next_event`, `next_crops`, `next_in`, `unclaimed`, `pb_<crop>`, `total_<crop>`,
`contests_<crop>`, `best_bracket_<crop>`, `top_<crop>_<n>_name`, `top_<crop>_<n>_score`.

## Coming from JacobsContest

- On first start, `plugins/JacobsContest/data.db` is **copied** to `plugins/RoyalContests/`. The
  tables are shared, so stats and unclaimed rewards carry over. For MySQL, point `storage.mysql` at
  the same database.
- The config files have a new layout and are **not** migrated. The shipped ones reproduce the old
  events and bracket rewards. Brackets are now lowercase ids with `top-percent`, plus the new
  `top-positions`.
- Remove JacobsContest before installing, or both will run contests.
