package zone.vao.incognito.packet

import com.destroystokyo.paper.profile.PlayerProfile
import com.destroystokyo.paper.profile.ProfileProperty
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import zone.vao.incognito.config.IncognitoConfig
import zone.vao.incognito.identity.Identity
import zone.vao.incognito.identity.RevealedNames
import zone.vao.incognito.coordinate.CoordinateOffset
import zone.vao.incognito.coordinate.CoordinateMapper
import java.lang.reflect.Modifier
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.CompassMeta
import java.util.logging.Logger
import java.util.EnumSet
import java.util.Optional
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal class NativePackets(private val settings: IncognitoConfig, private val logger: Logger, private val headMasker: (ItemStack) -> Boolean, private val suggestionIdentities: () -> List<Identity>) : AutoCloseable {

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
    private val itemStackType = Class.forName("net.minecraft.world.item.ItemStack")
    private val getItem = itemStackType.getMethod("getItem")
    private val playerHead = Class.forName("net.minecraft.world.item.Items").getField("PLAYER_HEAD").get(null)
    private val compass = Class.forName("net.minecraft.world.item.Items").getField("COMPASS").get(null)
    private val craftItemStack = Class.forName("org.bukkit.craftbukkit.inventory.CraftItemStack")
    private val toBukkit = craftItemStack.methods.first { it.name == "asBukkitCopy" && it.parameterCount == 1 }
    private val toNms = craftItemStack.getMethod("asNMSCopy", ItemStack::class.java)
    private val compoundType = Class.forName("net.minecraft.nbt.CompoundTag")
    private val listTagType = Class.forName("net.minecraft.nbt.ListTag")
    private val tagType = Class.forName("net.minecraft.nbt.Tag")
    private val pairType = Class.forName("com.mojang.datafixers.util.Pair")
    private val codecs = ConcurrentHashMap<Class<*>, Any>()
    private val logged = ConcurrentHashMap.newKeySet<String>()
    private val coordinateMapper = CoordinateMapper()
    private val textPackets = setOf(
        "ClientboundSystemChatPacket", "ClientboundDisguisedChatPacket", "ClientboundSetActionBarTextPacket",
        "ClientboundSetTitleTextPacket", "ClientboundSetSubtitleTextPacket", "ClientboundTabListPacket",
        "ClientboundSetObjectivePacket", "ClientboundSetPlayerTeamPacket", "ClientboundSetScorePacket",
        "ClientboundBossEventPacket", "ClientboundSetEntityDataPacket",
        "ClientboundContainerSetSlotPacket", "ClientboundContainerSetContentPacket", "ClientboundSetEquipmentPacket",
        "ClientboundSetCursorItemPacket", "ClientboundSetPlayerInventoryPacket", "ClientboundBlockEntityDataPacket",
        "ClientboundLevelChunkWithLightPacket", "ClientboundResetScorePacket",
    )
    private val textContainers = setOf("net.minecraft.network.chat.ChatType\$Bound", "net.minecraft.network.syncher.SynchedEntityData\$DataValue")

    fun validate() {
        (textPackets + "ClientboundPlayerInfoUpdatePacket").forEach {
            codec(Class.forName("net.minecraft.network.protocol.game.$it"))
        }
        val probe = profileConstructor.newInstance(UUID(0, 0), "ExamplePlayer")
        check(gameProfile.invoke(probe) != null)
    }

    fun mask(packet: Any, identities: List<Identity>, offset: CoordinateOffset = CoordinateOffset.ZERO, requests: MutableMap<Int, SuggestionRequest> = HashMap(), id: UUID? = null, reveal: Boolean = false): Any? {
        val type = packet.javaClass
        val names = if (settings.names) identities else emptyList()
        if (type.name == "net.minecraft.network.protocol.game.ClientboundPlayerChatPacket" && (names.isNotEmpty() || RevealedNames.active())) return mask(disguise(packet), identities, offset, requests, id, reveal)
        if (type.simpleName == "ClientboundDisguisedChatPacket") {
            return NativeReflection.record(packet) { name, value ->
                transform(value, if (name == "message") emptyList() else names, offset, reveal)
            }
        }
        if (type.simpleName == "ClientboundSystemChatPacket" && field(packet, "overlay") == false) {
            return NativeReflection.record(packet) { name, value ->
                if (name == "content" && value != null) {
                    val adventure = if (value is Component) value else toAdventure.invoke(null, value) as Component
                    val masked = components.system(adventure, names, offset, reveal)
                    if (value is Component) masked else toVanilla.invoke(null, masked)
                } else value
            }
        }
        if (type.name == "net.minecraft.network.protocol.game.ClientboundBundlePacket") {
            val packets = type.getMethod("subPackets").invoke(packet) as Iterable<*>
            val masked = packets.mapNotNull { it?.let { mask(it, identities, offset, requests, id, reveal) } }
            return if (masked.isEmpty()) null else type.getConstructor(Iterable::class.java).newInstance(masked)
        }
        if (type.name == "net.minecraft.network.protocol.game.ClientboundCustomChatCompletionsPacket" && names.isNotEmpty() && settings.namesTabComplete) {
            val suggestedNames = suggestionIdentities()
            return NativeReflection.record(packet) { name, value ->
                if (name == "entries") (value as List<*>).map { text.mask(it as String, suggestedNames, CoordinateOffset.ZERO) }.distinct() else value
            }
        }
        if (type.name == "net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket") {
            val request = requests.remove(field(packet, "id") as Int)
            val command = request?.command.orEmpty()
            val suggestedNames = if (settings.names && settings.namesTabComplete) suggestionIdentities() else emptyList()
            val suggestedOffset = if (settings.coordinatesTabComplete) offset else CoordinateOffset.ZERO
            val serverStart = field(packet, "start") as Int
            val serverEnd = serverStart + (field(packet, "length") as Int)
            val start = request?.start(serverStart) ?: serverStart
            val end = request?.end(serverEnd) ?: serverEnd
            val preceding = text.preceding(command.take(start))
            val suggestions = (field(packet, "suggestions") as List<*>).mapNotNull { entry ->
                val suggestion = text.suggestion(field(entry!!, "text") as String, suggestedNames, suggestedOffset, preceding)
                if (request?.accepts(suggestion, start, end, suggestedNames) == false) return@mapNotNull null
                NativeReflection.record(entry) { part, content ->
                    when (part) {
                        "text" -> RevealedNames.resolve(suggestion, reveal)
                        "tooltip" -> transform(content, suggestedNames, suggestedOffset, reveal)
                        else -> content
                    }
                }
            }.distinctBy { field(it, "text") }
            if (settings.debug) logger.info("[debug] suggestions $id request=${field(packet, "id")} command='$command' received=${(field(packet, "suggestions") as List<*>).size} sent=${suggestions.size} range=$serverStart..$serverEnd -> $start..$end")
            return NativeReflection.record(packet) { name, value ->
                when (name) {
                    "start" -> start
                    "length" -> end - start
                    "suggestions" -> suggestions
                    else -> value
                }
            }
        }
        var result = packet
        if (offset != CoordinateOffset.ZERO && coordinateMapper.supports(type)) {
            result = coordinateMapper.translate(copy(packet), offset)
            debug("$id:out:" + type.name) { "outbound $id ${type.simpleName}: ${describe(packet).take(300)} -> ${describe(result).take(300)}" }
        }
        if (type.name == "net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket") return maskProfiles(result, identities, names, id, reveal)
        val heads = if (settings.heads && (settings.names || settings.skin)) identities else emptyList()
        if (names.isEmpty() && heads.isEmpty() && offset == CoordinateOffset.ZERO && !RevealedNames.active() || type.packageName != "net.minecraft.network.protocol.game" || type.simpleName !in textPackets) return result
        val masked = transform(if (result !== packet || type.isRecord) result else copy(packet), names, offset, reveal, heads)!!
        if (names.isEmpty()) return masked
        return when (type.simpleName) {
            "ClientboundSetScorePacket", "ClientboundResetScorePacket" -> NativeReflection.record(masked) { name, value -> if (name == "owner") text.mask(value as String, names, CoordinateOffset.ZERO) else value }
            "ClientboundSetPlayerTeamPacket" -> masked.also { team ->
                val players = NativeReflection.fields(type).first { it.name == "players" }
                players.set(team, (players.get(team) as Collection<*>).map { text.mask(it as String, names, CoordinateOffset.ZERO) })
            }
            else -> masked
        }
    }

    fun inbound(packet: Any, identities: List<Identity>, offset: CoordinateOffset, requests: MutableMap<Int, SuggestionRequest>, id: UUID? = null): Any {
        val type = packet.javaClass
        val names = if (settings.names) identities else emptyList()
        if (type.name == "net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket") {
            val request = SuggestionRequest.create(field(packet, "command") as String, if (settings.namesTabComplete) names else emptyList(), false)
            requests[field(packet, "id") as Int] = request
            if (settings.debug) logger.info("[debug] suggestion request $id request=${field(packet, "id")} command='${request.command}' forwarded='${request.forwarded}'")
            if (request.command == request.forwarded) return packet
            if (type.isRecord) return NativeReflection.record(packet) { name, value -> if (name == "command") request.forwarded else value }
            return copy(packet).also { copied -> NativeReflection.fields(type).first { it.name == "command" }.set(copied, request.forwarded) }
        }
        if (type.name == "net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket" && offset != CoordinateOffset.ZERO) {
            return NativeReflection.record(packet) { name, value -> if (name == "itemStack") maskStack(value!!, false, offset.inverse()) else value }
        }
        if (type.name == "net.minecraft.network.protocol.game.ServerboundChatCommandPacket" && offset != CoordinateOffset.ZERO) {
            return NativeReflection.record(packet) { name, value -> if (name == "command") text.restore(value as String, emptyList(), offset) else value }
        }
        if (offset == CoordinateOffset.ZERO || !coordinateMapper.supports(type)) return packet
        val translated = coordinateMapper.translate(copy(packet), offset.inverse())
        debug("$id:in:" + type.name) { "inbound $id ${type.simpleName}: ${describe(packet)} -> ${describe(translated)}" }
        return translated
    }

    private fun debug(key: String, message: () -> String) {
        if (settings.debug && logged.add(key)) logger.info("[debug] ${message()}")
    }

    private fun maskProfiles(packet: Any, identities: List<Identity>, names: List<Identity>, viewer: UUID?, reveal: Boolean): Any {
        val fields = NativeReflection.fields(packet.javaClass)
        val actions = fields.single { EnumSet::class.java.isAssignableFrom(it.type) }.get(packet)
        val entries = fields.single { List::class.java.isAssignableFrom(it.type) }.get(packet) as List<*>
        val byId = identities.associateBy { it.id }
        val masked = entries.map { entry ->
            requireNotNull(entry)
            val id = entry.javaClass.getMethod("profileId").invoke(entry) as UUID
            val identity = byId[id]
            NativeReflection.record(entry) { name, value ->
                when (name) {
                    "profile" -> if (identity == null || !settings.names && !settings.skin) value else createProfile(identity, value, settings.skin && identity.id != viewer)
                    "displayName" -> transform(value, names, CoordinateOffset.ZERO, reveal)
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

    private fun transform(value: Any?, identities: List<Identity>, offset: CoordinateOffset, reveal: Boolean, heads: List<Identity> = emptyList()): Any? {
        if (value == null) return null
        if (value is Component) return components.mask(value, identities, offset, reveal)
        if (componentType.isInstance(value)) {
            val adventure = toAdventure.invoke(null, value) as Component
            return toVanilla.invoke(null, components.mask(adventure, identities, offset, reveal))
        }
        if (value is Optional<*>) return value.map { transform(it, identities, offset, reveal, heads) }
        if (value is List<*>) return value.map { transform(it, identities, offset, reveal, heads) }
        val type = value.javaClass
        if (heads.isNotEmpty() || offset != CoordinateOffset.ZERO) {
            if (itemStackType.isInstance(value)) return maskStack(value, heads.isNotEmpty(), offset)
            if (compoundType.isInstance(value)) return maskTag(value, heads)
            if (pairType.isInstance(value)) {
                val first = pairType.getMethod("getFirst").invoke(value)
                val second = transform(pairType.getMethod("getSecond").invoke(value), identities, offset, reveal, heads)
                return if (second === pairType.getMethod("getSecond").invoke(value)) value else pairType.getMethod("of", Any::class.java, Any::class.java).invoke(null, first, second)
            }
        }
        if (type.isEnum || type.packageName != "net.minecraft.network.protocol.game" && type.name !in textContainers) return value
        if (type.isRecord) return NativeReflection.record(value) { _, part -> transform(part, identities, offset, reveal, heads) }
        NativeReflection.fields(type).forEach { field ->
            val original = field.get(value)
            val masked = transform(original, identities, offset, reveal, heads)
            if (masked !== original) field.set(value, masked)
        }
        return value
    }

    private fun maskStack(stack: Any, heads: Boolean, offset: CoordinateOffset): Any {
        val item = getItem.invoke(stack)
        if (!(heads && item === playerHead || offset != CoordinateOffset.ZERO && item === compass)) return stack
        val bukkit = toBukkit.invoke(null, stack) as ItemStack
        val changed = if (item === playerHead) headMasker(bukkit) else lodestone(bukkit, offset)
        return if (changed) toNms.invoke(null, bukkit) else stack
    }

    private fun lodestone(item: ItemStack, offset: CoordinateOffset): Boolean {
        val meta = item.itemMeta as? CompassMeta ?: return false
        val target = meta.lodestone ?: return false
        meta.lodestone = target.clone().add(offset.x.toDouble(), 0.0, offset.z.toDouble())
        item.itemMeta = meta
        return true
    }

    private fun maskTag(tag: Any, heads: List<Identity>): Any {
        val profile = (compoundType.getMethod("getCompound", String::class.java).invoke(tag, "profile") as Optional<*>).orElse(null) ?: return tag
        val name = (compoundType.getMethod("getString", String::class.java).invoke(profile, "name") as Optional<*>).orElse(null) as String? ?: return tag
        val identity = heads.firstOrNull { it.realName.equals(name, true) } ?: return tag
        val copy = compoundType.getMethod("copy").invoke(tag)
        val masked = (compoundType.getMethod("getCompound", String::class.java).invoke(copy, "profile") as Optional<*>).get()
        compoundType.getMethod("putString", String::class.java, String::class.java).invoke(masked, "name", identity.alias)
        if (!settings.skin) return copy
        compoundType.getMethod("remove", String::class.java).invoke(masked, "properties")
        if (settings.texture.isNotEmpty()) {
            val property = compoundType.getConstructor().newInstance()
            val putString = compoundType.getMethod("putString", String::class.java, String::class.java)
            putString.invoke(property, "name", "textures")
            putString.invoke(property, "value", settings.texture)
            putString.invoke(property, "signature", settings.signature)
            val properties = listTagType.getConstructor().newInstance()
            listTagType.getMethod("add", Any::class.java).invoke(properties, property)
            compoundType.getMethod("put", String::class.java, tagType).invoke(masked, "properties", properties)
        }
        return copy
    }

    private fun disguise(packet: Any): Any {
        val unsigned = field(packet, "unsignedContent").let { if (it is Optional<*>) it.orElse(null) else it }
        val content = unsigned ?: literal.invoke(null, field(field(packet, "body")!!, "content"))
        return disguisedChat.newInstance(content, field(packet, "chatType"))
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
