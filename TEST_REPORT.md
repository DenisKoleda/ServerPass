# ServerPass Test Report

Date: 2026-06-19

Status: build and server-side Paper 26.2 verification passed. Automated client login could not be completed because the installed `minecraft-protocol`/`mineflayer` data does not support Minecraft `26.2`.

## Automated Build

```powershell
.\scripts\gradle-local.ps1 build
```

Result: passed.

JUnit coverage:

- `PasswordHasherTest`: PBKDF2 hash generation, correct password validates, wrong password fails, empty password rejected, same password uses different salts, salt/hash do not contain the plaintext marker.
- `AuthSessionTest`: session starts locked, records failures, unlocks, and timeout expires unauthenticated sessions.

Additional lockdown handlers added after initial verification:

- offhand swap
- item consume
- hotbar slot switch
- book edit
- armor stand manipulation
- fishing
- bucket fill/empty
- vehicle enter

Build after these additions: passed.

## Local Paper/Purpur Server

Installed jar:

```text
D:\Documents\Minecraft\server-dev\plugins\ServerPass.jar
```

Server: local Paper `26.2-23-dev`, port `25566`, Java 25.

Console selftest result:

```text
ServerPass selftest: PASS
PASS hash generation
PASS correct password validates
PASS wrong password fails
PASS empty password rejected
PASS config reload
PASS session lock/unlock basic logic
PASS runtime player command logging disabled
PASS spigot.yml commands.log disabled
PASS unique password marker absent from latest.log
PASS raw login commands absent from latest.log
```

## Security Checks

Passed:

- `config.yml` stores only PBKDF2 algorithm, iterations, salt, and hash.
- `/login <password>` is intercepted before command dispatch.
- `/serverpass set <password>` from players is intercepted before command dispatch.
- ServerPass disables Spigot player command logging at runtime and persists `commands.log: false` in `server-dev\spigot.yml`.
- Generated password markers were absent from `server-dev\logs\latest.log`.
- Generated password markers were absent from `server-dev\plugins\ServerPass\config.yml`.
- `latest.log` did not contain raw `issued server command: /login` or raw `issued server command: /serverpass set` entries after the command-log guard was added.
- `TEST_REPORT.md` contains no plaintext password.
- Final server selftest stopped Paper cleanly with exit code `0`.

Note: an early manual dev run on Paper with `commands.log: true` showed that Paper 26.2 can log raw player commands before `PlayerCommandPreprocessEvent` prevents dispatch. The plugin now disables that logging, and the affected local dev log entries were redacted.

## Client E2E Attempt

Attempted with the existing local `mineflayer`/`minecraft-protocol` dependency set from `D:\Documents\Minecraft\Mechanisms`.

Result: blocked by client tooling, not by ServerPass:

```text
Error: No data available for version 26.2
```

Because of that dependency limitation, automated bot verification of movement blocking, wrong-password kick, and correct-password unlock was not completed in this run.

## Limitations

- Real client gameplay checks still need a Minecraft 26.2 client or updated protocol test tooling.
- ServerPass is a shared password gate, not per-user auth.
- ServerPass does not replace whitelist and does not fully solve offline-mode impersonation.
