# Changelog

All notable changes to ServerPass are recorded here.

## Unreleased

- Keep future user-visible changes here before cutting a release or local deployment.

## 0.1.0 - 2026-06-19

### Added

- Initial local Paper/Purpur 26.2 plugin baseline.
- Shared server password gate using PBKDF2 salt/hash storage and in-memory sessions.
- `/login` and `/serverpass` command surface with admin, status, force-auth, reload, and selftest commands.
- Pre-login lockdown for movement, inventory, interaction, combat, chat, and non-login commands.
- Command-log guard to avoid writing login passwords to Paper/Spigot command logs.
- Local Java 25 Gradle build and `server-dev` validation workflow.
