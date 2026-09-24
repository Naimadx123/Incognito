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
            .then(Commands.literal("messages")
                .executes { messages(service, it, null) }
                .then(Commands.literal("on").executes { messages(service, it, true) })
                .then(Commands.literal("off").executes { messages(service, it, false) })
                .then(Commands.literal("status").executes {
                    val sender = it.source.sender
                    sender.sendMessage((sender as? Player)?.let { service.joinQuitStatus(it.uniqueId) }
                        ?: service.settings.messages.get("players-only"))
                    Command.SINGLE_SUCCESS
                }))
            .then(Commands.literal("status").executes {
                val sender = it.source.sender
                sender.sendMessage((sender as? Player)?.let { service.status(it.uniqueId) } ?: service.settings.messages.get("status-disabled"))
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
        service.change(player, enabled)
        return Command.SINGLE_SUCCESS
    }

    private fun messages(service: IncognitoService, context: CommandContext<CommandSourceStack>, shown: Boolean?): Int {
        val player = context.source.sender as? Player ?: run {
            context.source.sender.sendMessage(service.settings.messages.get("players-only"))
            return Command.SINGLE_SUCCESS
        }
        service.changeJoinQuit(player, shown)
        return Command.SINGLE_SUCCESS
    }

    private fun other(service: IncognitoService, context: CommandContext<CommandSourceStack>, enabled: Boolean?): Int {
        val target = context.getArgument("target", PlayerSelectorArgumentResolver::class.java).resolve(context.source).first()
        val messages = service.settings.messages
        service.change(target, enabled) { active ->
            val key = if (service.pending(target.uniqueId) != null) {
                if (active) "admin-pending-enabled" else "admin-pending-disabled"
            } else if (active) "admin-enabled" else "admin-disabled"
            val message = messages.get(key, player = target.name)
            val sender = context.source.sender
            if (sender is Player) service.region(sender) { sender.sendMessage(message) } else sender.sendMessage(message)
        }
        return Command.SINGLE_SUCCESS
    }
}
