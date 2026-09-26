# Incognito

Incognito is a Minecraft plugin for Paper and Folia that lets players hide their identity behind a random alias. It masks names and skins in data sent to clients and shifts visible world coordinates. Administrators can manage players' incognito settings and receive notifications about identity changes.

## Features

- A random alias on each incognito login, used in the profile, player list and nametag.
- Name masking in chat, scoreboards, boss bars, holograms and supported text sent by other plugins.
- Hidden skins or a shared replacement skin for other viewers. Players still see their own skin.
- Player head masking in inventories, equipment, dropped items and placed blocks. Original item profiles remain stored on the server.
- Books signed with the player's alias while incognito is active.
- X/Z offsets in the world view, F3, minimaps and supported text. Coordinates entered in supported commands are translated back to server coordinates.
- Player preferences or server rules for showing join and quit messages.
- Administrative notifications containing real names and aliases.
- Optional PlaceholderAPI and MiniPlaceholders integrations, with SQLite, MySQL or PostgreSQL storage.

## Requirements and installation

The project builds against Paper API `1.21.8` by default and targets Java 21. Folia support is declared in the plugin metadata. Packet filtering uses internal server classes, so compatibility with other Minecraft versions should be verified before deployment.

1. Build the project or obtain a packaged `Incognito-v<version>.jar`.
2. Place the JAR in the server's `plugins` directory and restart the server.
3. Edit `plugins/Incognito/config.yml` and grant players `incognito.use`.
4. Restart the server after configuration changes. Missing configuration keys are added at startup.

PlaceholderAPI and MiniPlaceholders are optional. Database libraries are loaded through the server's library mechanism. The plugin also uses bStats metrics, controlled by the server's bStats settings.

## Using incognito

`/incognito` saves the requested state. Enabling and disabling protection takes effect after the player disconnects and rejoins, including when coordinate masking is disabled. The player receives a pending-change message; the plugin does not automatically disconnect them to apply it.

`/incognito status` shows the active alias, disabled state or pending change. Toggling again before disconnecting cancels a pending change. Aliases are generated during login.

## Commands and permissions

Command alias: `/incog`.

| Command | Action |
| --- | --- |
| `/incognito` | Toggles the requested incognito state. |
| `/incognito on` / `/incognito off` | Requests enabling or disabling incognito. |
| `/incognito status` | Shows the current protection state. |
| `/incognito messages` | Toggles visibility of the player's own join and quit messages while incognito is active. |
| `/incognito messages on` / `/incognito messages off` | Sets those messages to shown or hidden. |
| `/incognito messages status` | Shows the effective setting and whether changes are locked. |
| `/incognito player <player> [on\|off]` | Changes an online player's state; toggles it when `on`/`off` is omitted. |

| Permission | Default | Purpose |
| --- | --- | --- |
| `incognito.use` | OP | Access to the command and personal incognito settings. |
| `incognito.admin` | OP | Manage other players and receive incognito state and alias notifications. The administrative command also requires `incognito.use`. |
| `incognito.reveal` | OP | View real names through the `incognito_realname` placeholder. Ordinary name masking remains active. |

Personal settings require a player sender. The console can use the `player` subcommand. With `names.hide_realname: true`, target incognito players by their aliases.

## Join and quit messages

```yaml
join-quit:
  mode: player
  default-show: true
  allow-toggle: true
```

| Setting | Behavior |
| --- | --- |
| `mode: player` | Uses each player's saved preference, or `default-show` until they choose one. |
| `mode: show` | Forces messages to be shown and blocks player changes. |
| `mode: hide` | Forces messages to be hidden and blocks player changes. |
| `default-show` | Initial setting in `player` mode: `true` shows messages, `false` hides them. |
| `allow-toggle: false` | Locks changes in `player` mode while preserving saved preferences and the default for players without a preference. |

A preference controls both join and quit announcements about that player, as seen by everyone in chat. It is saved in the database and can be set before enabling incognito. It applies to subsequent join and quit events while protection is active. Messages for players without incognito remain under the server's control.

When showing messages, the plugin preserves the event message and masks the player's name if `names.enabled` and `names.mask-system-messages` are enabled. In `show` mode, if the message was previously cleared, it supplies `messages.join-message` or `messages.quit-message`. In `player` mode, suppression by another plugin is preserved. These rules control standard join and quit events; separate broadcasts sent directly by other plugins are outside their scope. Another listener modifying the event later can change the result.

## Administrative notifications

Online players with `incognito.admin` receive notifications when a pending change is saved and when it takes effect at login. They do not need `incognito.reveal`. Real names in these notifications are shown only to authorized recipients.

Example messages after a change takes effect:

```text
Incognito >> Player Notch enabled incognito, new nickname: incognito123
Incognito >> Player incognito123 disabled incognito, new nickname: Notch
```

Before reconnection, the notification describes the pending change. Rejoining with incognito still active sends a new-alias notification. Canceling a pending change has a separate notification; requesting an already saved state does not send another notification.

Templates are in the `messages` section under `notify-enabled`, `notify-disabled`, `notify-alias-changed`, `notify-pending-enabled`, `notify-pending-disabled` and `notify-cancelled`. Set an individual template to `''` to disable that notification. `<player>` is the real name and `<name>` is the alias. All templates in `messages` support MiniMessage.

## Protection settings

See [config.yml](src/main/resources/config.yml) for the full configuration, defaults and comments.

| Key | Purpose |
| --- | --- |
| `names.enabled` | Enables name masking and replacement of real UUIDs in supported text with session UUIDs. Dashed and compact UUIDs are supported. |
| `names.mask-player-messages` | Default `false`: preserves player-written chat content. Set `true` to mask incognito names and UUIDs inside player messages. Names without an incognito session are unchanged. Structured sender names remain masked independently of this option. |
| `names.mask-system-messages` | Default `true`: masks incognito names and UUIDs in server chat messages, including join, quit and death announcements, and on the death screen. Set `false` to preserve those messages, which may expose real identities. Does not control player chat or other UI text such as titles and action bars. |
| `names.format` | Alias pattern, default `Anon_{random}`. Requires `{random}`; other characters must be ASCII letters, digits or `_`. Aliases are limited to 16 characters. |
| `names.tabcomplete` | Uses aliases in command suggestions and chat name completion. Disabling it leaves real names in suggestions. |
| `names.hide_realname` | Replaces the real name with the alias in the server's online-player name map. Offline lookups retain the real name; custom lookup mechanisms in other plugins may bypass this map. |
| `skin.enabled` | Masks skins for other viewers. |
| `skin.value`, `skin.signature` | Optional shared skin as signed Minecraft profile texture properties. Set both together; these are not a username or image URL. |
| `heads` | Masks player heads in packets. |
| `books` | Uses the alias as the author when signing a book while name masking is active. |
| `coordinates.enabled` | Shifts the visible world along X/Z. Y is unchanged. |
| `coordinates.tabcomplete` | Translates coordinates in command suggestions. |
| `coordinates.patterns` | Regular expressions for coordinates in text, using named `x` and `z` capture groups. |
| `placeholders.enabled` | Filters results from PlaceholderAPI expansions. |
| `debug` | Logs detailed packet transformations for troubleshooting. |

Masking applies to supported data sent to clients. It does not remove real names from databases, logs or data retained by other plugins. Incognito is not vanish: the player remains visible in the world.

Both message options require `names.enabled`. With `names.mask-system-messages` enabled, join, quit and death message fields are masked at `LOWEST` and `HIGHEST` event priorities. Death-screen overrides and the separate death-screen packet are covered as well. The `AsyncChatEvent` renderer receives a masked sender display name regardless of the message options. These event transformations do not shift coordinates; the outgoing packet filter applies the recipient's coordinate offset.

The server's `Player` object retains its real UUID and account name so permissions, inventories and other player data keep their original identity. Installed plugins still have access to that object and can reconstruct real names or UUIDs. Custom plugin channels and renderers that ignore the supplied display name require integration; event masking does not isolate plugins from each other. With `names.mask-player-messages: false`, unstructured player chat content is preserved, including content generated by a custom renderer. Messages sent by plugins as unstructured system chat follow `names.mask-system-messages`. Ordinary item metadata and previously written books are not covered by this text filtering.

## Web maps

Web maps read positions and names from the server API, so packet masking does not reach them. With `maps: true` (default) incognito players are hidden on dynmap, BlueMap, squaremap and Pl3xMap while incognito is active and shown again when it ends. Hiding is transient on dynmap, squaremap and Pl3xMap. BlueMap stores visibility in its own state, so a player whose incognito ended during a crash can stay hidden there until incognito is toggled again. Markers placed by other plugins (homes, claims, death points) and map chat bridges are not changed.

## Placeholders

The following placeholders are available when the corresponding integration is installed:

| PlaceholderAPI | MiniPlaceholders | Result |
| --- | --- | --- |
| `%incognito_enabled%` | `<incognito_enabled>` | `true` or `false` for the active session. |
| `%incognito_name%` | `<incognito_name>` | Active alias or ordinary player name. |
| `%incognito_realname%` | `<incognito_realname>` | Real name of an online incognito player, revealed to recipients with `incognito.reveal`; otherwise empty. |
| `%incognito_x%` | `<incognito_x>` | Visible block X coordinate. |
| `%incognito_y%` | `<incognito_y>` | Block Y coordinate. |
| `%incognito_z%` | `<incognito_z>` | Visible block Z coordinate. |
| `%incognito_world%` | `<incognito_world>` | World name. |

MiniPlaceholders tags require a player audience. Incognito's own placeholders are registered independently of `placeholders.enabled`; that option controls wrapping results from other PlaceholderAPI expansions.

## Storage

The default backend is SQLite at `plugins/Incognito/data.db`. Records contain the UUID, real name, requested incognito state, message preference and last alias needed for the disable notification. The stored alias is used for notifications; a new alias is generated at login. Coordinate offsets are kept in memory only and a new one is chosen for each session.

For MySQL, create a database and configure:

```yaml
storage:
  type: mysql
  host: localhost
  port: 3306
  database: incognito
  username: incognito
  password: ''
  table-prefix: incognito_
  pool-size: 4
```

For PostgreSQL, use `type: postgresql` (or `postgres`) and the appropriate port, usually `5432`. The database account must be able to create and alter tables. Switching backends does not automatically transfer records between databases. Writes are asynchronous, and pending changes are flushed during a normal plugin shutdown.

## Networks and sector servers

With `network.enabled: true` every server shares incognito sessions through Redis. A player keeps the same alias, masked profile and coordinate offset when switching servers, so sector servers that split one map show consistent coordinates across transfers. Aliases are reserved network-wide, and incognito state changes are pushed to the other servers.

```yaml
network:
  enabled: true
  host: redis.internal
  port: 6379
  password: ''
  key-prefix: 'incognito:'
  session-timeout: 60
```

Sessions are not persisted: once a player has been off the whole network for longer than `session-timeout` seconds, the next login gets a new alias and offset. Use a shared MySQL or PostgreSQL `storage` on every server so all of them read the same incognito state. If Redis is unreachable, players get local sessions and a warning is logged.

## Building

The project requires JDK 25 for compilation. Gradle Wrapper downloads the version specified in the repository; the default JVM target is 21.

Windows:

```powershell
.\gradlew.bat build
```

Linux / macOS:

```sh
./gradlew build
```

`build` runs tests and creates `build/libs/Incognito-v<version>.jar`. Run `test` for the test suite alone. Override the API version and JVM target with `-PpaperApiVersion=<version>` and `-PtargetJava=<level>`. The `runServer` task starts a development server; its default Minecraft version is `1.21.8`, configurable with `-PminecraftVersion=<version>`.

## License

Copyright © 2026 Naimadx123. This project uses the [Non-Commercial Source Available License v1.0](LICENSE). The license permits non-commercial use and modification; commercial use requires prior written permission from the copyright holder. See `LICENSE` for the full terms.
