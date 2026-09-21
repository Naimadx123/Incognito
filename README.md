# Incognito

A plugin for Paper and Folia 1.21.8 and later (also checked against 26.x). `/incognito` hides a player's identity with a random `Anon_…` alias, a hidden or shared skin, and shifted coordinates. It operates at the packet level, covering F3, the player list, nametags, chat, scoreboards, boss bars, holograms, tab completion, and text from other plugins without requiring integration on their side.

## Features

- **Alias and skin** — replacements in the profile, TAB, nametag, and all text sent to clients; players still see their own skin. Message colors and formatting are preserved.
- **Coordinates** — a random, fixed X/Z offset for the player's entire world view (chunks, entities, sounds, world border, F3, minimaps). Coordinates in text and command suggestions receive the same offset, while coordinates entered in commands are translated back.
- **PlaceholderAPI** — placeholder results pass through the same filter (`%player_x%`, `%player_name%`, etc.), with additional built-in placeholders: `%incognito_enabled%`, `%incognito_name%`, `%incognito_realname%`, `%incognito_x/y/z/world%`.
- **Administration** — `/incognito player <player> [on|off]` requires `incognito.admin`. `incognito.reveal` reveals real names only through `%incognito_realname%`. This permission defaults to OP. All other name masking, skin masking and coordinate offsets remain active.

## Installation

Copy `build/libs/Incognito-v1.0-SNAPSHOT.jar` into `plugins` and restart the server. Configuration is stored in `plugins/Incognito/config.yml`: features (`names`, `skin`, `coordinates`, `placeholders`, tab completion) can be disabled individually, player messages are configured in the `messages` section using MiniMessage, and `debug: true` logs packet transformations. Enabling or disabling coordinate offsets requires reconnecting.

## Commands and permissions

| Command | Permission | Action |
| --- | --- | --- |
| `/incognito`, `/incognito on\|off` | `incognito.use` (OP) | Toggles incognito mode |
| `/incognito status` | `incognito.use` | Shows status and alias |
| `/incognito player <player> [on\|off]` | `incognito.admin` (OP) | Changes another player's incognito mode |

## Building

```powershell
.\gradlew.bat build
.\gradlew.bat build '-PpaperApiVersion=26.3.build.+' -PtargetJava=25
```

Kotlin 2.4, Gradle 9.7, JDK 21+ (Java 25 for the 26.x API).
