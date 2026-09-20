package net.minecraft.network.protocol.game

import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.world.entity.PositionMoveRotation
import net.minecraft.world.entity.Relative
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import java.util.Optional

@JvmRecord
data class ClientboundPlayerPositionPacket(val id: Int, val change: PositionMoveRotation, val relatives: Set<Relative>)

@JvmRecord
data class ClientboundSetChunkCacheCenterPacket(val x: Int, val z: Int)

@JvmRecord
data class ClientboundSoundPacket(val x: Int, val y: Int, val z: Int, val pitch: Float)

@JvmRecord
data class ClientboundExplodePacket(val center: Vec3, val playerKnockback: Optional<Vec3>)

@JvmRecord
data class ClientboundSectionBlocksUpdatePacket(val sectionPos: SectionPos, val positions: ShortArray)

@JvmRecord
data class ClientboundBlockUpdatePacket(val pos: BlockPos, val blockState: Int)

@JvmRecord
data class ServerboundUseItemOnPacket(val blockHit: BlockHitResult, val sequence: Int)

@JvmRecord
data class ServerboundSetStructureBlockPacket(val pos: BlockPos, val offset: BlockPos, val size: BlockPos)

class ServerboundMovePlayerPacket {
    class Pos(val x: Double, val y: Double, val z: Double, val onGround: Boolean)
    class Rot(val xRot: Float, val yRot: Float)
}
