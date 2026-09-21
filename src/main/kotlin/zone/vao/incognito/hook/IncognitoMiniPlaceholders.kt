package zone.vao.incognito.hook

import io.github.miniplaceholders.api.Expansion
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.tag.Tag
import org.bukkit.entity.Player
import zone.vao.incognito.Incognito
import zone.vao.incognito.identity.IncognitoService

class IncognitoMiniPlaceholders(plugin: Incognito, service: IncognitoService) {

    private val placeholders = Placeholders(service)
    private val expansion: Expansion = Expansion.builder("incognito")
        .author("vao")
        .version(plugin.pluginMeta.version)
        .also { builder ->
            placeholders.keys.forEach { key ->
                builder.audiencePlaceholder(Player::class.java, key) { player, _, _ ->
                    Tag.selfClosingInserting(Component.text(placeholders.value(player, key).orEmpty()))
                }
            }
        }
        .build()

    fun register() = expansion.register()

    fun unregister() = expansion.unregister()
}
