# RoyalContests — project notes

Formerly JacobsContest. Rewritten as **RoyalContests** (`com.mystipixel.royalcontests`) to join the
Royal suite: Paper 26.2, Java 25, calendar versioning, same CI as RoyalGarden.

## Why the rewrite
JacobsContest 1.x could be farmed for free and paid out twice:
- DROPS mode scored any block (breaking dirt = 1 point); BLOCK_BREAK counted unripe crops; placed
  melons/pumpkins/cane counted.
- `/jc reload` re-registered the scoring listener (double points) and force-ended the contest.
- One shared boss bar showed everyone the last player's score.
- Claims ran commands before marking the reward claimed, so double-clicks duplicated rewards.
- All crops ranked together (Hypixel ranks each crop separately); DB calls on the main thread.

## Layout
- `contest/` ContestService (one 1s ticker: schedule → countdown → running → results), Contest,
  Participant, Ranking (pure), Schedule (pure), EventRegistry, BracketTable, ContestDisplay
- `scoring/HarvestListener` all counting rules; `crop/` CropRegistry + PlacedBlocks (chunk PDC)
- `storage/` Database (Hikari, single worker thread, SQLite/MySQL, old tables + contest_results),
  RewardService (atomic claims), StatsCache (for PAPI/menus)
- `gui/` holder-based menus from menus.yml; `command/`; `hook/` PAPI

## Status
- Builds in CI. Not yet run on a live server — test scoring modes, the interval schedule and the
  data.db migration on the test instance first.
