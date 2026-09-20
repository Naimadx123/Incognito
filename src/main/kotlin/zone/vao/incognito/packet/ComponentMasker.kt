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

class ComponentMasker(private val text: TextMasker, private val fallback: (String) -> Unit = {}) {

    private val codec = GsonComponentSerializer.gson()
    private val plain = PlainTextComponentSerializer.plainText()

    fun mask(component: Component, identities: List<Identity>, offset: CoordinateOffset): Component {
        if (identities.isEmpty() && offset == CoordinateOffset.ZERO) return component
        val masked = visit(component, identities, offset)
        if (!text.exposes(plain.serialize(masked), identities) && !text.exposes(codec.serialize(masked), identities)) {
            return if (masked == component) component else masked
        }
        fallback(codec.serialize(component))
        return Component.text(text.mask(plain.serialize(component), identities, offset))
    }

    private fun visit(component: Component, identities: List<Identity>, offset: CoordinateOffset): Component {
        var result = when (component) {
            is TextComponent -> component.content(text.mask(component.content(), identities, offset))
            is TranslatableComponent -> component.arguments(component.arguments().map { visit(it.asComponent(), identities, offset) })
            else -> component
        }
        result = result.children(result.children().map { visit(it, identities, offset) })
        when (val hover = result.hoverEvent()?.value()) {
            is Component -> result = result.hoverEvent(HoverEvent.showText(visit(hover, identities, offset)))
            is HoverEvent.ShowEntity -> hover.name()?.let { result = result.hoverEvent(HoverEvent.showEntity(hover.type(), hover.id(), visit(it, identities, offset))) }
        }
        result.insertion()?.let { result = result.insertion(text.mask(it, identities, offset)) }
        val click = result.clickEvent()
        val payload = click?.payload()
        if (click != null && payload is ClickEvent.Payload.Text) {
            val masked = text.mask(payload.value(), identities, offset)
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
