package zone.vao.incognito.coordinate

import zone.vao.incognito.packet.NativeReflection
import java.util.Optional

internal class CoordinateMapper {

    private val chunks = setOf("ClientboundLevelChunkWithLightPacket", "ClientboundLightUpdatePacket", "ClientboundSetChunkCacheCenterPacket")
    private val scalar = setOf(
        "ClientboundAddEntityPacket", "ClientboundAddExperienceOrbPacket", "ClientboundLevelParticlesPacket",
        "ClientboundPlayerLookAtPacket", "ClientboundInitializeBorderPacket", "ClientboundSetBorderCenterPacket",
        "ServerboundMovePlayerPacket\$Pos", "ServerboundMovePlayerPacket\$PosRot", "ServerboundAcceptTeleportationPacket",
    )
    private val structured = setOf(
        "ClientboundBlockDestructionPacket", "ClientboundBlockEntityDataPacket", "ClientboundBlockEventPacket",
        "ClientboundBlockUpdatePacket", "ClientboundChunksBiomesPacket", "ClientboundEntityPositionSyncPacket", "ClientboundDamageEventPacket",
        "ClientboundExplodePacket", "ClientboundForgetLevelChunkPacket", "ClientboundLevelEventPacket",
        "ClientboundLoginPacket", "ClientboundMoveMinecartPacket", "ClientboundMoveVehiclePacket",
        "ClientboundOpenSignEditorPacket", "ClientboundPlayerPositionPacket", "ClientboundRespawnPacket",
        "ClientboundSectionBlocksUpdatePacket", "ClientboundSetDefaultSpawnPositionPacket",
        "ClientboundSetEntityDataPacket", "ClientboundTeleportEntityPacket", "ClientboundTrackedWaypointPacket",
        "ServerboundMoveVehiclePacket", "ServerboundPlayerActionPacket", "ServerboundUseItemOnPacket",
        "ServerboundSignUpdatePacket", "ServerboundBlockEntityTagQueryPacket", "ServerboundSetCommandBlockPacket",
        "ServerboundSetJigsawBlockPacket", "ServerboundSetStructureBlockPacket", "ServerboundJigsawGeneratePacket",
        "ServerboundPickItemFromBlockPacket", "ServerboundTestInstanceBlockActionPacket", "ServerboundSetTestBlockPacket",
    )
    private val nested = setOf(
        "net.minecraft.world.entity.PositionMoveRotation", "net.minecraft.core.GlobalPos",
        "net.minecraft.world.entity.vehicle.NewMinecartBehavior\$MinecartStep", "net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior\$MinecartStep",
        "net.minecraft.world.entity.PositionPath\$Linear", "net.minecraft.world.entity.PositionPath\$Stepped", "net.minecraft.world.entity.PositionStep",
        "net.minecraft.core.PositionAndRotation\$Immutable", "net.minecraft.core.PositionAndRotation\$Mutable", "net.minecraft.world.level.storage.LevelData\$RespawnData",
        "net.minecraft.network.syncher.SynchedEntityData\$DataValue", "net.minecraft.world.phys.BlockHitResult",
    )

    fun supports(type: Class<*>): Boolean = type.packageName == "net.minecraft.network.protocol.game" &&
        (type.simpleName in chunks || type.simpleName in scalar || type.simpleName in structured ||
            type.name.substringAfterLast('.') in scalar || type.simpleName == "ClientboundSoundPacket")

    fun translate(copy: Any, offset: CoordinateOffset): Any = visit(copy, offset, emptySet(), "")!!

    private fun visit(value: Any?, offset: CoordinateOffset, relatives: Set<String>, fieldName: String): Any? {
        if (value == null) return null
        if (fieldName in setOf("offset", "size", "deltaMovement", "movement", "playerKnockback")) return value
        val type = value.javaClass
        when (type.name) {
            "net.minecraft.core.BlockPos", "net.minecraft.core.Vec3i" -> return blockPosition(value, offset, false)
            "net.minecraft.core.SectionPos" -> return blockPosition(value, offset, true)
            "net.minecraft.world.level.ChunkPos" -> return type.getConstructor(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                .newInstance(axis(value, "x").toInt() + offset.chunkX, axis(value, "z").toInt() + offset.chunkZ)
            "net.minecraft.world.phys.Vec3" -> {
                if (fieldName !in setOf("position", "endPosition", "center", "location", "sourcePosition")) return value
                return type.getConstructor(Double::class.javaPrimitiveType, Double::class.javaPrimitiveType, Double::class.javaPrimitiveType)
                    .newInstance(offset.x(axis(value, "x").toDouble(), "X" in relatives),
                        axis(value, "y").toDouble(), offset.z(axis(value, "z").toDouble(), "Z" in relatives))
            }
        }
        if (value is Optional<*>) return value.map { visit(it, offset, relatives, fieldName) }
        if (value is List<*>) return value.map { visit(it, offset, relatives, fieldName) }
        if (type.isEnum || type.packageName != "net.minecraft.network.protocol.game" && type.name !in nested &&
            !type.name.startsWith("net.minecraft.world.waypoints.TrackedWaypoint\$")) return value
        val fields = NativeReflection.fields(type)
        val flags = fields.firstOrNull { it.name == "relatives" }?.get(value) as? Set<*>
        val currentRelatives = flags?.mapNotNull { (it as? Enum<*>)?.name }?.toSet() ?: relatives
        val packetName = type.name.substringAfterLast('.')
        fun replace(name: String, part: Any?): Any? {
            if (name == "x" || name == "z" || name == "newCenterX" || name == "newCenterZ") {
                val axis = if (name == "x" || name == "newCenterX") offset.x else offset.z
                if (type.simpleName in chunks && part is Int) return Math.addExact(part, axis / 16)
                if (type.simpleName == "ClientboundSoundPacket" && part is Int) return Math.addExact(part, axis * 8)
                if ((type.simpleName in scalar || packetName in scalar) && part is Double) return part + axis
            }
            return visit(part, offset, currentRelatives, name)
        }
        if (type.isRecord) return NativeReflection.record(value, ::replace)
        fields.forEach { field ->
            val previous = field.get(value)
            val replacement = replace(field.name, previous)
            if (replacement !== previous) field.set(value, replacement)
        }
        return value
    }

    private fun axis(value: Any, name: String): Number = NativeReflection.fields(value.javaClass).first { it.name == name }.get(value) as Number

    private fun blockPosition(value: Any, offset: CoordinateOffset, section: Boolean): Any {
        val type = value.javaClass
        val x = (type.getMethod("getX").invoke(value) as Int) + if (section) offset.chunkX else offset.x
        val y = type.getMethod("getY").invoke(value) as Int
        val z = (type.getMethod("getZ").invoke(value) as Int) + if (section) offset.chunkZ else offset.z
        val ints = arrayOf(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
        return if (section) type.getMethod("of", *ints).invoke(null, x, y, z)
        else type.getConstructor(*ints).newInstance(x, y, z)
    }
}
