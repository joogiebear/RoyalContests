# JacobsContest - Project Memory

## What It Is
Jacob's Contest — a Paper/Minecraft plugin that runs Hypixel Skyblock-style crop farming contests.
Players compete to harvest the most crops during timed events and earn bracket-based rewards.

## GitHub
https://github.com/joogiebear/JacobsContest
- **main** → v1.0.0 (stable, SQLite only)
- **development** → v1.1.0 (MySQL support added, needs testing)

## Build
- Maven project (`pom.xml`)
- Java 21, Paper API 1.21.4
- `mvn clean package` → jar in `target/`
- HikariCP 5.1.0 shaded in for MySQL connection pooling

## Current State
- v1.0.0: Fully working, SQLite storage, shipped
- v1.1.0 (dev branch): MySQL storage added — NOT yet tested
  - Config toggle: `storage.type: sqlite` or `storage.type: mysql`
  - MySQL config block in config.yml (host/port/database/username/password)

## Architecture
- `StorageProvider` interface — implemented by `SQLiteStorage` and `MySQLStorage`
- `ContestManager` — runs contests in-memory (local to each server)
- `ContestEvent` — loaded from events.yml, defines crop + duration
- `Bracket` system — DIAMOND > PLATINUM > GOLD > SILVER > BRONZE based on score thresholds
- Scoring modes: `AURORA_COLLECTIONS`, `PLACEHOLDER`, `DROPS`, `BLOCK_BREAK`
- PlaceholderAPI integration for skill requirements and scoring

## Multi-Server Design
- Garden/farming is its own dedicated server — only one server runs contests
- MySQL allows external access (web leaderboards, cross-server stats lookup)
- Redis NOT needed — single server handles all contests
- If someone wanted true multi-server contests in the future → would need Redis pub/sub

## TODO
- [ ] Test MySQL branch on a real server
- [ ] Merge development → main once MySQL confirmed working
- [ ] Consider leaderboard GUI using PlaceholderAPI + MySQL queries

## Key Config Files
- `config.yml` — storage type, contest settings, scoring mode, skill requirements
- `events.yml` — contest event rotation definitions
- `brackets.yml` — bracket thresholds and reward commands
- `lang.yml` — all player-facing messages
