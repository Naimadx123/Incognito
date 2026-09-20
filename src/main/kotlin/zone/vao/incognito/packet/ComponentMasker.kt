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

class ComponentMasker(private val text: TextMasker, private val fallback: (String) -> Unit = {}) {

    private val codec = GsonComponentSerializer.gson()
    private val plain = PlainTextComponentSerializer.plainText()

    fun mask(component: Component, identities: List<Identity>, offset: CoordinateOffset, reveal: Boolean = false): Component {
        if (identities.isEmpty() && offset == CoordinateOffset.ZERO && !RevealedNames.active()) return component
        val names = identities
        var masked = visit(component) { text.mask(it, names, offset) }
        if (text.exposes(plain.serialize(masked), names) || text.exposes(codec.serialize(masked), names)) {
            fallback(codec.serialize(component))
            masked = Component.text(text.mask(plain.serialize(component), names, offset))
        }
        masked = visit(masked) { RevealedNames.resolve(it, reveal) }
        if (RevealedNames.contains(plain.serialize(masked))) masked = Component.text(RevealedNames.resolve(plain.serialize(masked), reveal))
        return if (masked == component) component else masked
    }

    private fun visit(component: Component, replace: (String) -> String): Component {
        var result = when (component) {
            is TextComponent -> component.content(replace(component.content()))
            is TranslatableComponent -> component.arguments(component.arguments().map { visit(it.asComponent(), replace) })
            else -> component
        }
        result = result.children(result.children().map { visit(it, replace) })
        when (val hover = result.hoverEvent()?.value()) {
            is Component -> result = result.hoverEvent(HoverEvent.showText(visit(hover, replace)))
            is HoverEvent.ShowEntity -> hover.name()?.let { result = result.hoverEvent(HoverEvent.showEntity(hover.type(), hover.id(), visit(it, replace))) }
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
