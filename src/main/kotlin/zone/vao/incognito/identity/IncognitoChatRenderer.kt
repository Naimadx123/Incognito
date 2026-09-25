package zone.vao.incognito.identity

import io.papermc.paper.chat.ChatRenderer
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player

internal class IncognitoChatRenderer(private val delegate: ChatRenderer, private val service: IncognitoService) : ChatRenderer {
    override fun render(source: Player, sourceDisplayName: Component, message: Component, viewer: Audience): Component {
        val rendered = delegate.render(source, service.publicMessage(sourceDisplayName), message, viewer)
        return if (service.settings.maskPlayerMessages) service.publicMessage(rendered) else rendered
    }
}
