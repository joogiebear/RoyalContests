## 2026.41.0 - 2026-10-10

### Features
- add an optional command button to the contests hub (`ad3cc9e`)

### Fixes
- treat an unresolved scoring placeholder as no reading (`8a344f7`)

### Refactors
- read the missing menu item text from config (`2fac335`)
- read all player-facing text from config (`4215172`)

### Documentation
- add AGENTS.md with the suite's development standards (`ee2d855`)

## 2026.39.0 — 2026-09-26

### 🐛 Fixes
- correct EcoSkills placeholder from ecoskills_farming_level to ecoskills_farming (`7bb1257`)

### ♻️ Refactors
- rewrite JacobsContest as RoyalContests for the Royal suite (`bc25d43`)

### 🔧 Other
- Add MEMORY.md with project context and status (`501f94c`)
- v1.1.0: MySQL storage support with HikariCP connection pooling (`08f2712`)
- Pluggable scoring modes - AURORA_COLLECTIONS, PLACEHOLDER, DROPS, BLOCK_BREAK. Works standalone or with any plugin. (`ee76719`)
- AuroraCollections-based scoring - snapshot on lock-in, compare throughout contest. Bulletproof with all plugins. (`b9516c1`)
- Hybrid scoring - baseline from getDrops() + upgrade from BlockDropItemEvent, handles virtual bags (`5a5a632`)
- Two-phase drop scoring - captures real drops after all Eco plugins modify them (`88f0e9a`)
- Fix scoring - use block.getDrops() instead of BlockDropItemEvent for reliable drop counting (`5bdd114`)
- Claim All button in rewards menu (`471941c`)
- SQLite storage, player stats + personal bests, pending claim-based rewards, GUI menus (/contests) (`c06007b`)
- Split config into config.yml, lang.yml, brackets.yml, events.yml (`ecad447`)
- Skill requirements, minimum score threshold, auto-rotation with break periods (`c4e91f3`)
- Drop-based scoring - works with Eco enchants, pets, reforges, talismans + configurable point values (`ce83ccf`)
- full plugin build (`edacc9f`)
- Initial spec (`1ffa700`)

