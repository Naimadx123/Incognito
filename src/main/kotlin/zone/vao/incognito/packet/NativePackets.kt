package zone.vao.incognito.packet

import com.destroystokyo.paper.profile.PlayerProfile
import com.destroystokyo.paper.profile.ProfileProperty
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import zone.vao.incognito.config.IncognitoConfig
import zone.vao.incognito.identity.Identity
import zone.vao.incognito.coordinate.CoordinateOffset
import zone.vao.incognito.coordinate.CoordinateMapper
import java.lang.reflect.Modifier
import java.util.logging.Logger
import java.util.EnumSet
import java.util.Optional
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal class NativePackets(private val settings: IncognitoConfig, private val logger: Logger) : AutoCloseable {

    private val text = TextMasker(settings.coordinatePatterns)
    private val components = ComponentMasker(text) { json -> debug("fallback:$json") { "component flattened: $json" } }
    private val componentType = Class.forName("net.minecraft.network.chat.Component")
    private val bridge = Class.forName("io.papermc.paper.adventure.PaperAdventure")
    private val toAdventure = bridge.getMethod("asAdventure", componentType)
    private val toVanilla = bridge.getMethod("asVanilla", Component::class.java)
    private val literal = componentType.getMethod("literal", String::class.java)
    private val disguisedChat = Class.forName("net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket")
        .getConstructor(componentType, Class.forName("net.minecraft.network.chat.ChatType\$Bound"))
    private val profileType = Class.forName("com.destroystokyo.paper.profile.CraftPlayerProfile")
    private val profileConstructor = profileType.getConstructor(UUID::class.java, String::class.java)
    private val gameProfile = profileType.getMethod("getGameProfile")
    private val bukkitProfile = profileType.getMethod("asBukkitCopy", Class.forName("com.mojang.authlib.GameProfile"))
    private val streamCodec = Class.forName("net.minecraft.network.codec.StreamCodec")
    private val encode = streamCodec.getMethod("encode", Any::class.java, Any::class.java)
    private val decode = streamCodec.getMethod("decode", Any::class.java)
    private val registryType = Class.forName("net.minecraft.core.RegistryAccess")
    private val buffer = Class.forName("net.minecraft.network.RegistryFriendlyByteBuf").getConstructor(ByteBuf::class.java, registryType)
    private val server = Bukkit.getServer().javaClass.getMethod("getServer").invoke(Bukkit.getServer())
    private val registry = server.javaClass.getMethod("registryAccess").invoke(server)
    private val codecs = ConcurrentHashMap<Class<*>, Any>()
    private val logged = ConcurrentHashMap.newKeySet<String>()
    private val askServerIds = ConcurrentHashMap<Class<*>, Any>()
    private val coordinateMapper = CoordinateMapper()
    private val textPackets = setOf(
        "ClientboundSystemChatPacket", "ClientboundDisguisedChatPacket", "ClientboundSetActionBarTextPacket",
        "ClientboundSetTitleTextPacket", "ClientboundSetSubtitleTextPacket", "ClientboundTabListPacket",
        "ClientboundSetObjectivePacket", "ClientboundSetPlayerTeamPacket", "ClientboundSetScorePacket",
        "ClientboundBossEventPacket", "ClientboundSetEntityDataPacket",
    )
    private val textContainers = setOf("net.minecraft.network.chat.ChatType\$Bound", "net.minecraft.network.syncher.SynchedEntityData\$DataValue")

    fun validate() {
        (textPackets + "ClientboundPlayerInfoUpdatePacket").forEach {
            codec(Class.forName("net.minecraft.network.protocol.game.$it"))
        }
        val probe = profileConstructor.newInstance(UUID(0, 0), "ExamplePlayer")
        check(gameProfile.invoke(probe) != null)
    }

    fun mask(packet: Any, identities: List<Identity>, offset: CoordinateOffset = CoordinateOffset.ZERO, requests: MutableMap<Int, String> = HashMap(), id: UUID? = null, reveal: Boolean = false): Any? {
        val type = packet.javaClass
        val names = if (settings.names && !reveal) identities else emptyList()
        if (type.name == "net.minecraft.network.protocol.game.ClientboundPlayerChatPacket" && names.isNotEmpty()) return mask(disguise(packet), identities, offset, requests)
        if (type.name == "net.minecraft.network.protocol.game.ClientboundBundlePacket") {
            val packets = type.getMethod("subPackets").invoke(packet) as Iterable<*>
            val masked = packets.mapNotNull { it?.let { mask(it, identities, offset, requests, id, reveal) } }
            return if (masked.isEmpty()) null else type.getConstructor(Iterable::class.java).newInstance(masked)
        }
        if (type.name == "net.minecraft.network.protocol.game.ClientboundCommandsPacket" &&
            (names.isNotEmpty() && !settings.namesTabComplete || offset != CoordinateOffset.ZERO && !settings.coordinatesTabComplete)) return askServer(copy(packet))
        if (type.name == "net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket") {
            val command = requests.remove(field(packet, "id") as Int) ?: ""
            val suggestedNames = if (settings.namesTabComplete) names else emptyList()
            val suggestedOffset = if (settings.coordinatesTabComplete) offset else CoordinateOffset.ZERO
            if (suggestedNames.isEmpty() && suggestedOffset == CoordinateOffset.ZERO) return packet
            val preceding = text.preceding(command.take(field(packet, "start") as Int))
            return NativeReflection.record(packet) { name, value ->
                if (name != "suggestions") value else (value as List<*>).map { entry ->
                    NativeReflection.record(entry!!) { part, content -> if (part == "text") text.suggestion(content as String, suggestedNames, suggestedOffset, preceding) else content }
                }
            }
        }
        var result = packet
        if (offset != CoordinateOffset.ZERO && coordinateMapper.supports(type)) {
            result = coordinateMapper.translate(copy(packet), offset)
            debug("$id:out:" + type.name) { "outbound $id ${type.simpleName}: ${describe(packet).take(300)} -> ${describe(result).take(300)}" }
        }
        if (identities.isNotEmpty() && (settings.names || settings.skin) && type.name == "net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket") return maskProfiles(result, identities, id)
        if (names.isEmpty() && offset == CoordinateOffset.ZERO || type.packageName != "net.minecraft.network.protocol.game" || type.simpleName !in textPackets) return result
        return transform(if (result !== packet || type.isRecord) result else copy(packet), names, offset)
    }

    fun inbound(packet: Any, identities: List<Identity>, offset: CoordinateOffset, requests: MutableMap<Int, String>, id: UUID? = null): Any {
        val type = packet.javaClass
        val names = if (settings.names) identities else emptyList()
        if (type.name == "net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket") {
            requests[field(packet, "id") as Int] = field(packet, "command") as String
            return packet
        }
        if (type.name == "net.minecraft.network.protocol.game.ServerboundChatCommandPacket" && (names.isNotEmpty() || offset != CoordinateOffset.ZERO)) {
            return NativeReflection.record(packet) { name, value -> if (name == "command") text.restore(value as String, names, offset) else value }
        }
        if (offset == CoordinateOffset.ZERO || !coordinateMapper.supports(type)) return packet
        val translated = coordinateMapper.translate(copy(packet), offset.inverse())
        debug("$id:in:" + type.name) { "inbound $id ${type.simpleName}: ${describe(packet)} -> ${describe(translated)}" }
        return translated
    }

    private fun debug(key: String, message: () -> String) {
        if (settings.debug && logged.add(key)) logger.info("[debug] ${message()}")
    }

    private fun maskProfiles(packet: Any, identities: List<Identity>, viewer: UUID?): Any {
        val fields = NativeReflection.fields(packet.javaClass)
        val actions = fields.single { EnumSet::class.java.isAssignableFrom(it.type) }.get(packet)
        val entries = fields.single { List::class.java.isAssignableFrom(it.type) }.get(packet) as List<*>
        val byId = identities.associateBy { it.id }
        val masked = entries.map { entry ->
            requireNotNull(entry)
            val id = entry.javaClass.getMethod("profileId").invoke(entry) as UUID
            val identity = byId[id] ?: return@map entry
            NativeReflection.record(entry) { name, value ->
                when (name) {
                    "profile" -> createProfile(identity, value, settings.skin && identity.id != viewer)
                    "displayName" -> if (settings.names) toVanilla.invoke(null, Component.text(identity.alias)) else value
                    else -> value
                }
            }
        }
        return packet.javaClass.getConstructor(EnumSet::class.java, List::class.java).newInstance(actions, masked)
    }

    private fun createProfile(identity: Identity, original: Any?, hideSkin: Boolean): Any {
        val profile = profileConstructor.newInstance(identity.id, identity.alias) as PlayerProfile
        if (!hideSkin && original != null) (bukkitProfile.invoke(null, original) as PlayerProfile).properties.forEach(profile::setProperty)
        else if (hideSkin && settings.texture.isNotEmpty()) profile.setProperty(ProfileProperty("textures", settings.texture, settings.signature))
        return gameProfile.invoke(profile)
    }

    private fun transform(value: Any?, identities: List<Identity>, offset: CoordinateOffset): Any? {
        if (value == null) return null
        if (value is Component) return components.mask(value, identities, offset)
        if (componentType.isInstance(value)) {
            val adventure = toAdventure.invoke(null, value) as Component
            return toVanilla.invoke(null, components.mask(adventure, identities, offset))
        }
        if (value is Optional<*>) return value.map { transform(it, identities, offset) }
        if (value is List<*>) return value.map { transform(it, identities, offset) }
        val type = value.javaClass
        if (type.isEnum || type.packageName != "net.minecraft.network.protocol.game" && type.name !in textContainers) return value
        if (type.isRecord) return NativeReflection.record(value) { _, part -> transform(part, identities, offset) }
        NativeReflection.fields(type).forEach { field ->
            val original = field.get(value)
            val masked = transform(original, identities, offset)
            if (masked !== original) field.set(value, masked)
        }
        return value
    }

    private fun disguise(packet: Any): Any {
        val unsigned = field(packet, "unsignedContent").let { if (it is Optional<*>) it.orElse(null) else it }
        val content = unsigned ?: literal.invoke(null, field(field(packet, "body")!!, "content"))
        return disguisedChat.newInstance(content, field(packet, "chatType"))
    }

    private fun askServer(packet: Any): Any {
        val entries = NativeReflection.fields(packet.javaClass).first { it.name == "entries" }
        entries.set(packet, (entries.get(packet) as List<*>).map { entry ->
            val stub = field(entry!!, "stub")
            if (stub == null || stub.javaClass.simpleName != "ArgumentNodeStub") return@map entry
            NativeReflection.record(entry) { name, value ->
                when (name) {
                    "stub" -> NativeReflection.record(stub) { part, content ->
                        if (part != "suggestionId") content else askServerIds.computeIfAbsent(stub.javaClass.recordComponents.first { it.name == part }.type) {
                            it.getMethod("parse", String::class.java).invoke(null, "minecraft:ask_server")
                        }
                    }
                    "flags" -> (value as Int) or 16
                    else -> value
                }
            }
        })
        return packet
    }

    private fun describe(packet: Any): String = if (packet.javaClass.isRecord) packet.toString() else
        NativeReflection.fields(packet.javaClass).joinToString(", ", "${packet.javaClass.simpleName}[", "]") { "${it.name}=${it.get(packet)}" }

    private fun field(value: Any, name: String): Any? = NativeReflection.fields(value.javaClass).first { it.name == name }.get(value)

    private fun codec(type: Class<*>): Any = codecs.computeIfAbsent(type) {
        val field = it.declaredFields.single { field -> Modifier.isStatic(field.modifiers) && field.name == "STREAM_CODEC" }
        field.isAccessible = true
        field.get(null)
    }

    private fun copy(packet: Any): Any {
        val bytes = Unpooled.buffer()
        try {
            val friendly = buffer.newInstance(bytes, registry)
            val codec = codec(packet.javaClass)
            encode.invoke(codec, friendly, packet)
            return decode.invoke(codec, friendly)
        } finally {
            bytes.release()
        }
    }

    override fun close() {
        codecs.clear()
    }
}
