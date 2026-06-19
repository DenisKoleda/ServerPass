# ServerPass

ServerPass is a Paper/Purpur 26.2 server-side plugin that gates each join behind one shared server password. It is not AuthMe, does not create per-user accounts, and does not store sessions on disk.

## Security Model

- The shared password is never stored as plaintext.
- `config.yml` stores only `algorithm`, `iterations`, `salt`, and `hash`.
- Hashing uses standard Java PBKDF2 (`PBKDF2WithHmacSHA256` by default).
- Player `/login <password>` commands are intercepted in `PlayerCommandPreprocessEvent` at `LOWEST` priority and cancelled before normal command dispatch.
- Player `/serverpass set <password>` is also intercepted to avoid exposing a newly set password through player command logging.
- On Paper/Spigot, ServerPass also disables `commands.log` at runtime and persists `commands.log: false` in `spigot.yml`, because Paper 26.2 can log raw player commands before plugins receive `PlayerCommandPreprocessEvent`.
- `audit.log` records only player, result, reason, and timestamp.

ServerPass does not replace whitelist and does not fully solve offline-mode impersonation. If someone knows the shared password and can use another whitelisted nickname, they can still try to impersonate that player. Keep whitelist and private network access such as Tailscale.

## Build

```powershell
cd D:\Documents\Minecraft\ServerPass
.\scripts\gradle-local.ps1 build
```

The jar is written to:

```text
D:\Documents\Minecraft\ServerPass\build\libs\ServerPass-0.1.0.jar
```

For local testing only, copy it to:

```text
D:\Documents\Minecraft\server-dev\plugins\ServerPass.jar
```

## Commands

```text
/login <password>
/login
/serverpass set <password>
/serverpass reload
/serverpass status
/serverpass logout <player>
/serverpass forceauth <player>
/serverpass selftest [keep]
/serverpass help
```

`/serverpass status` deliberately avoids printing `salt` or `hash`.

## Permissions

```text
serverpass.admin
serverpass.set
serverpass.reload
serverpass.bypass
serverpass.forceauth
serverpass.selftest
```

`serverpass.admin` does not grant `serverpass.bypass`. Bypass is intentionally separate.

## Setup

On first start `password.hash` is empty. There is no default password.

Set the password from console:

```text
serverpass set <password>
```

After that, every non-bypassed player must use:

```text
/login <password>
```

## Lockdown Before Login

Before login, ServerPass blocks movement, block break/place, interaction, inventory actions, item drop/pickup, offhand swap, item consume, hotbar switch, book edit, armor stand manipulation, fishing, bucket fill/empty, vehicle enter, chat, non-login commands, player damage, and damage to the player. Head rotation is allowed by default.

Configuration lives in `config.yml`; text lives in `messages.yml`.
