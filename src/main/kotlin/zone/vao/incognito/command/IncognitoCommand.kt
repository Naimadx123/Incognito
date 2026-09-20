package zone.vao.incognito.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver
import org.bukkit.entity.Player
import zone.vao.incognito.identity.IncognitoService

object IncognitoCommand {

    fun build(service: IncognitoService): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("incognito")
            .requires { it.sender.hasPermission("incognito.use") }
            .executes { self(service, it, null) }
            .then(Commands.literal("on").executes { self(service, it, true) })
            .then(Commands.literal("off").executes { self(service, it, false) })
            .then(Commands.literal("status").executes {
                val sender = it.source.sender
                val identity = (sender as? Player)?.let { player -> service.identity(player.uniqueId) }
                sender.sendMessage(identity?.let { service.settings.messages.get("status-enabled", it.alias) } ?: service.settings.messages.get("status-disabled"))
                Command.SINGLE_SUCCESS
            })
            .then(Commands.literal("player")
                .requires { it.sender.hasPermission("incognito.admin") }
                .then(Commands.argument("target", ArgumentTypes.player())
                    .executes { other(service, it, null) }
                    .then(Commands.literal("on").executes { other(service, it, true) })
                    .then(Commands.literal("off").executes { other(service, it, false) })))
            .build()

    private fun self(service: IncognitoService, context: CommandContext<CommandSourceStack>, enabled: Boolean?): Int {
        val player = context.source.sender as? Player ?: run {
            context.source.sender.sendMessage(service.settings.messages.get("players-only"))
            return Command.SINGLE_SUCCESS
        }
        change(service, player, enabled, true)
        return Command.SINGLE_SUCCESS
    }

    private fun other(service: IncognitoService, context: CommandContext<CommandSourceStack>, enabled: Boolean?): Int {
        val target = context.getArgument("target", PlayerSelectorArgumentResolver::class.java).resolve(context.source).first()
        val messages = service.settings.messages
        val alias = change(service, target, enabled, false)
        context.source.sender.sendMessage(alias?.let { messages.get("admin-enabled", it, target.name) } ?: messages.get("admin-disabled", "", target.name))
        return Command.SINGLE_SUCCESS
    }

    private fun change(service: IncognitoService, player: Player, enabled: Boolean?, kick: Boolean): String? {
        val messages = service.settings.messages
        if (enabled ?: (service.identity(player.uniqueId) == null)) {
            val identity = service.enable(player, kick = kick)
            player.sendMessage(messages.get("enabled", identity.alias))
            return identity.alias
        }
        service.disable(player, kick)
        player.sendMessage(messages.get("disabled"))
        return null
    }
}
