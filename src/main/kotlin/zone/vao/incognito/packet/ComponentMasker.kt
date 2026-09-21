package zone.vao.incognito.packet

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.TranslatableComponent
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import zone.vao.incognito.coordinate.CoordinateOffset
import zone.vao.incognito.identity.Identity
import zone.vao.incognito.identity.RevealedNames
import zone.vao.incognito.command.CommandNames
import java.util.UUID

class ComponentMasker(private val text: TextMasker, private val fallback: (String) -> Unit = {}) {

    private val codec = GsonComponentSerializer.gson()
    private val plain = PlainTextComponentSerializer.plainText()
    private val chatTypes = setOf("chat.type.text", "chat.type.announcement", "chat.type.emote", "commands.message.display.incoming", "commands.message.display.outgoing")

    fun system(component: Component, identities: List<Identity>, offset: CoordinateOffset, reveal: Boolean = false): Component =
        if (component is TranslatableComponent && component.key() in chatTypes) chat(component, identities, offset, reveal)
        else mask(component, identities, offset, reveal)

    fun chat(component: Component, identities: List<Identity>, offset: CoordinateOffset, reveal: Boolean = false): Component {
        if (component is TranslatableComponent) {
            if (component.key() in chatTypes) {
                return component.arguments(component.arguments().mapIndexed { index, argument ->
                    mask(argument.asComponent(), if (index == 1) emptyList() else identities, offset, reveal)
                }).children(component.children().map { chat(it, identities, offset, reveal) })
            }
            if (component.key().startsWith("multiplayer.player.")) return mask(component, identities, offset, reveal)
        }
        return mask(component, emptyList(), offset, reveal)
    }

    fun mask(component: Component, identities: List<Identity>, offset: CoordinateOffset, reveal: Boolean = false): Component {
        if (identities.isEmpty() && offset == CoordinateOffset.ZERO && !RevealedNames.active() && !CommandNames.contains(codec.serialize(component)) && !CommandNames.contains(plain.serialize(component))) return component
        val names = identities
        var masked = visit(component, { id -> names.firstOrNull { it.id == id }?.maskedId ?: id }) { text.mask(it, names, offset) }
        if (text.exposes(plain.serialize(masked), names) || text.exposes(codec.serialize(masked), names)) {
            fallback(codec.serialize(component))
            masked = Component.text(text.mask(plain.serialize(component), names, offset))
        }
        masked = visit(masked) { CommandNames.resolve(RevealedNames.resolve(it, reveal)) }
        if (RevealedNames.contains(plain.serialize(masked)) || CommandNames.contains(plain.serialize(masked))) masked = Component.text(CommandNames.resolve(RevealedNames.resolve(plain.serialize(masked), reveal)))
        return if (masked == component) component else masked
    }

    private fun visit(component: Component, profileId: (UUID) -> UUID = { it }, replace: (String) -> String): Component {
        var result = when (component) {
            is TextComponent -> component.content(replace(component.content()))
            is TranslatableComponent -> component.arguments(component.arguments().map { visit(it.asComponent(), profileId, replace) })
            else -> component
        }
        result = result.children(result.children().map { visit(it, profileId, replace) })
        when (val hover = result.hoverEvent()?.value()) {
            is Component -> result = result.hoverEvent(HoverEvent.showText(visit(hover, profileId, replace)))
            is HoverEvent.ShowEntity -> result = result.hoverEvent(HoverEvent.showEntity(hover.type(), profileId(hover.id()), hover.name()?.let { visit(it, profileId, replace) }))
        }
        result.insertion()?.let { result = result.insertion(replace(it)) }
        val click = result.clickEvent()
        val payload = click?.payload()
        if (click != null && payload is ClickEvent.Payload.Text) {
            val masked = replace(payload.value())
            when (ClickEvent.Action.NAMES.key(click.action())) {
                "run_command" -> ClickEvent.runCommand(masked)
                "suggest_command" -> ClickEvent.suggestCommand(masked)
                "copy_to_clipboard" -> ClickEvent.copyToClipboard(masked)
                "open_url" -> ClickEvent.openUrl(masked)
                else -> null
            }?.let { result = result.clickEvent(it) }
        }
        return result
    }
}
