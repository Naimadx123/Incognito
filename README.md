# Incognito

A plugin for Paper and Folia 1.21.8 and later (also checked against 26.x). `/incognito` hides a player's identity with a random `Anon_…` alias, a hidden or shared skin, and shifted coordinates. It operates at the packet level, covering F3, the player list, nametags, chat, scoreboards, boss bars, holograms, tab completion, and text from other plugins without requiring integration on their side.

## Features

- **Alias and skin** — player heads (inventories, drops, equipment, placed skulls) are masked in packets with the alias and the masked skin (`heads`), the items themselves stay untouched; the alias follows `names.format` (`Anon_{random}` by default, `{random}` is hex, 16 characters max); replacements in the profile, TAB, nametag, and all text sent to clients; players still see their own skin. Message colors and formatting are preserved. With `names.hide_realname` (default on) the real-name entry is removed from the server online-player name map and the alias is registered instead. Plugin-specific lookups may bypass this map; offline records retain the real name.
- **Coordinates** — a random, fixed X/Z offset for the player's entire world view (chunks, entities, sounds, world border, F3, minimaps). Coordinates in text and command suggestions receive the same offset, while coordinates entered in commands are translated back.
- **PlaceholderAPI** — placeholder results pass through the same filter (`%player_x%`, `%player_name%`, etc.), with additional built-in placeholders: `%incognito_enabled%`, `%incognito_name%`, `%incognito_realname%`, `%incognito_x/y/z/world%`. The same set is available as MiniPlaceholders audience tags (`<incognito_realname>` etc.) when MiniPlaceholders is installed.
- **Administration** — `/incognito player <player> [on|off]` requires `incognito.admin`. `incognito.reveal` reveals real names only through `%incognito_realname%`. This permission defaults to OP. All other name masking, skin masking and coordinate offsets remain active.

## Installation

With `names.enabled` and `names.tabcomplete` enabled, server-provided command suggestions mask account names and names exposed through Bukkit's display name and player list name. Names captured before incognito and current names are mapped to the same alias, with Adventure formatting removed for matching. Current names are refreshed every 20 ticks on the player's [entity scheduler](https://jd.papermc.io/paper/1.21.8/io/papermc/paper/threadedregions/scheduler/EntityScheduler.html); packet handlers read cached names without calling another plugin's API. Alias prefixes are expanded before asking the command provider for candidates, then results are masked and filtered back to the typed prefix. The provider still controls permissions and visibility; Incognito does not add hidden players to its results.

Aliases resolve through Bukkit's online player lookup, including when `names.hide_realname` is disabled. This supports commands that use that lookup, without integrations for individual plugins. Names kept exclusively in another plugin's private cache cannot be mapped unless that plugin exposes them through Bukkit. Ambiguous display names shared by multiple incognito players are not assigned to an arbitrary alias. Command trees retain their original suggestion providers.

With `names.enabled` and `names.hide_realname` enabled, full real names of online incognito players in player-command arguments are replaced with random non-player identifiers before command execution. Aliases and names of offline players remain unchanged, even if their stored incognito state is enabled. Matching is case-insensitive; the command label is preserved. OP and `incognito.reveal` do not bypass replacement. Incognito neither cancels the command nor sends a lookup error: the receiving plugin handles the supplied identifier. A plugin accepting arbitrary offline identities may accept it instead of reporting a missing player.

Replacement uses `PlayerCommandPreprocessEvent` so Paper can preserve validation of the original signed command and execute the modified command through its supported path. This avoids modifying signed text directly in a network packet. The generic replacement also affects name mentions in message bodies, reasons and selectors; it does not know third-party argument semantics. Partial names, UUID lookups, console commands and internal plugin dispatches are not covered. Plugin error messages may display the replacement identifier. This does not change private player indexes or guarantee that a plugin with its own lookup accepts aliases.

On startup the plugin adds any options missing from `plugins/Incognito/config.yml` (with their default values and comments), so updating the jar never requires regenerating the config.

Copy `build/libs/Incognito-v1.0.jar` into `plugins` and restart the server. Configuration is stored in `plugins/Incognito/config.yml`: features (`names`, `skin`, `coordinates`, `placeholders`, tab completion) can be disabled individually, player messages are configured in the `messages` section using MiniMessage, and `debug: true` logs packet transformations. Enabling or disabling coordinate offsets requires reconnecting.

## Storage

SQLite is the default backend, stored in `plugins/Incognito/data.db`. The storage layout follows Voxen: a [HikariCP](https://github.com/brettwooldridge/HikariCP) connection pool, a SQL storage implementation and an in-memory player cache. Active state, real name and alias are loaded before packet handlers are registered. Packet and placeholder lookups use memory, without database queries. Changes are coalesced per UUID, written on a background worker every second and flushed during normal shutdown. Failed writes remain pending for retry; an abrupt process termination can lose changes not yet flushed.

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

For PostgreSQL, use `storage.type: postgres` (or `postgresql`) and `storage.port: 5432`, with the same host, database, credentials and pool settings shown above. Create the database first. Existing configurations keep their explicit port, so change `3306` when switching from MySQL. PostgreSQL uses the same startup cache and background saves as the other backends, with `ON CONFLICT` updates. Connection and socket timeouts follow the [PostgreSQL JDBC driver settings](https://jdbc.postgresql.org/documentation/use/).

The plugin creates its table automatically. HikariCP and the SQLite, MySQL and PostgreSQL JDBC drivers are declared in the [libraries section of plugin.yml](https://docs.papermc.io/paper/dev/plugin-yml/). Paper downloads them and their transitive dependencies into its local library cache instead of loading bundled copies from the plugin JAR. The first startup needs access to the configured Maven Central mirror; subsequent starts reuse cached libraries. Kotlin remains bundled. Keep library versions in `build.gradle.kts` and `plugin.yml` aligned when updating dependencies. Restart after changing the backend; changing `storage.type` does not transfer data between databases. A remote database connection failure at startup prevents initialization instead of silently using an empty SQLite database. The cache is local to each server and does not provide live synchronization across a network.

## Commands and permissions

| Command | Permission | Action |
| --- | --- | --- |
| `/incognito`, `/incognito on\|off` | `incognito.use` (OP) | Toggles incognito mode |
| `/incognito status` | `incognito.use` | Shows status and alias |
| `/incognito player <player> [on\|off]` | `incognito.admin` (OP) | Changes another player's incognito mode |

## Building

Versions ending in `-SNAPSHOT` include the first eight characters of the Git commit hash, with `-dirty` when the working tree has uncommitted changes, for example `1.1-a1b2c3d4-dirty-SNAPSHOT`. The version is applied to the JAR filename, `plugin.yml` and the manifest. The manifest also records the full commit hash and dirty state. Release versions remain unchanged. Builds without Git metadata retain the version from `gradle.properties`.

```powershell
.\gradlew.bat build
.\gradlew.bat build '-PpaperApiVersion=26.3.build.+' -PtargetJava=25
```

Kotlin 2.4, Gradle 9.7, JDK 21+ (Java 25 for the 26.x API).
