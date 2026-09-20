package zone.vao.incognito.packet

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import java.util.concurrent.CompletableFuture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommandCompletionTest {

    @Test
    fun `overriding sibling arguments with a single pending server request prevents completion`() {
        var pending: CompletableFuture<Suggestions>? = null
        var requests = 0
        val dispatcher = dispatcher(true) {
            pending?.cancel(false)
            CompletableFuture<Suggestions>().also {
                pending = it
                requests++
            }
        }
        val result = dispatcher.getCompletionSuggestions(dispatcher.parse("tp ", Unit))
        assertEquals(3, requests)
        pending!!.complete(SuggestionsBuilder("tp ", 3).suggest("Anon_0123456789").build())
        assertFalse(result.isDone)
        result.cancel(false)
    }

    @Test
    fun `native sibling suggestions complete without competing server requests`() {
        val dispatcher = dispatcher(false) { error("Unexpected server request") }
        val result = dispatcher.getCompletionSuggestions(dispatcher.parse("tp ", Unit))
        assertTrue(result.isDone)
        assertEquals(listOf("Anon_0123456789"), result.join().list.map { it.text })
    }

    private fun dispatcher(override: Boolean, request: () -> CompletableFuture<Suggestions>): CommandDispatcher<Unit> {
        val command = LiteralArgumentBuilder.literal<Unit>("tp")
        for (name in listOf("location", "destination", "targets")) {
            val argument = RequiredArgumentBuilder.argument<Unit, String>(name, object : ArgumentType<String> {
                override fun parse(reader: StringReader): String = reader.readUnquotedString()

                override fun <S> listSuggestions(context: CommandContext<S>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> =
                    builder.suggest("Anon_0123456789").buildFuture()
            })
            if (override) argument.suggests { _, _ -> request() }
            command.then(argument)
        }
        return CommandDispatcher<Unit>().also { it.register(command) }
    }
}
