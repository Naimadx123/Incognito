package zone.vao.incognito.coordinate

import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.network.protocol.game.*
import net.minecraft.world.entity.PositionMoveRotation
import net.minecraft.world.entity.Relative
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CoordinateMapperTest {

    private val mapper = CoordinateMapper()
    private val offset = CoordinateOffset(32_000, -64_000)

    @Test
    fun `requires whole chunk offsets`() {
        assertFailsWith<IllegalArgumentException> { CoordinateOffset(1, 16) }
        assertFailsWith<IllegalArgumentException> { CoordinateOffset(Int.MIN_VALUE, 0) }
    }

    @Test
    fun `absolute teleport round trip preserves height velocity rotation and id`() {
        val original = ClientboundPlayerPositionPacket(7,
            PositionMoveRotation(Vec3(-12.75, 64.5, 1024.125), Vec3(0.1, -0.2, 0.3), 90f, 20f), emptySet())
        val shifted = mapper.translate(original, offset) as ClientboundPlayerPositionPacket
        assertEquals(Vec3(31987.25, 64.5, -62975.875), shifted.change.position)
        assertSame(original.change.deltaMovement, shifted.change.deltaMovement)
        assertEquals(original, mapper.translate(shifted, offset.inverse()))
    }

    @Test
    fun `relative axes are not offset even when velocity flags are present`() {
        val original = ClientboundPlayerPositionPacket(9,
            PositionMoveRotation(Vec3(5.0, 80.0, 7.0), Vec3(1.0, 0.0, 2.0), 0f, 0f), setOf(Relative.X, Relative.DELTA_Z))
        val shifted = mapper.translate(original, offset) as ClientboundPlayerPositionPacket
        assertEquals(Vec3(5.0, 80.0, -63993.0), shifted.change.position)
        assertEquals(original, mapper.translate(shifted, offset.inverse()))
    }

    @Test
    fun `chunk center uses chunks rather than blocks`() {
        assertEquals(ClientboundSetChunkCacheCenterPacket(1987, -3981),
            mapper.translate(ClientboundSetChunkCacheCenterPacket(-13, 19), offset))
    }

    @Test
    fun `sounds use eighths of a block`() {
        assertEquals(ClientboundSoundPacket(256008, 512, -512016, 1f),
            mapper.translate(ClientboundSoundPacket(8, 512, -16, 1f), offset))
    }

    @Test
    fun `explosion position changes without changing knockback`() {
        val impulse = Vec3(1.0, 2.0, 3.0)
        val shifted = mapper.translate(ClientboundExplodePacket(Vec3(0.0, 64.0, 0.0), Optional.of(impulse)), offset) as ClientboundExplodePacket
        assertEquals(Vec3(32000.0, 64.0, -64000.0), shifted.center)
        assertSame(impulse, shifted.playerKnockback.get())
    }

    @Test
    fun `block update changes block position but preserves block state`() {
        val shifted = mapper.translate(ClientboundBlockUpdatePacket(BlockPos(-1, 42, 16), 912), offset) as ClientboundBlockUpdatePacket
        assertEquals(31999, shifted.pos.getX())
        assertEquals(42, shifted.pos.getY())
        assertEquals(-63984, shifted.pos.getZ())
        assertEquals(912, shifted.blockState)
    }

    @Test
    fun `section update leaves local block indices alone`() {
        val local = shortArrayOf(0, 42, 4095)
        val shifted = mapper.translate(ClientboundSectionBlocksUpdatePacket(SectionPos(-1, -4, 2), local), offset) as ClientboundSectionBlocksUpdatePacket
        assertEquals(1999, shifted.sectionPos.getX())
        assertEquals(-4, shifted.sectionPos.getY())
        assertEquals(-3998, shifted.sectionPos.getZ())
        assertSame(local, shifted.positions)
    }

    @Test
    fun `incoming interaction restores block and exact hit point`() {
        val client = ServerboundUseItemOnPacket(BlockHitResult(Vec3(32001.25, 70.5, -63998.75), BlockPos(32001, 70, -63999), true), 23)
        val restored = mapper.translate(client, offset.inverse()) as ServerboundUseItemOnPacket
        assertEquals(Vec3(1.25, 70.5, 1.25), restored.blockHit.location)
        assertEquals(1, restored.blockHit.blockPos.getX())
        assertEquals(1, restored.blockHit.blockPos.getZ())
        assertEquals(23, restored.sequence)
    }

    @Test
    fun `incoming position is translated but rotation only packet is not selected`() {
        val position = ServerboundMovePlayerPacket.Pos(32001.5, 64.0, -63998.5, true)
        assertTrue(mapper.supports(position.javaClass))
        val restored = mapper.translate(position, offset.inverse()) as ServerboundMovePlayerPacket.Pos
        assertEquals(1.5, restored.x)
        assertEquals(1.5, restored.z)
        assertFalse(mapper.supports(ServerboundMovePlayerPacket.Rot::class.java))
    }

    @Test
    fun `structure offsets and sizes remain relative`() {
        val relative = BlockPos(1, 2, 3)
        val size = BlockPos(16, 16, 16)
        val shifted = mapper.translate(ServerboundSetStructureBlockPacket(BlockPos(32000, 64, -64000), relative, size), offset.inverse()) as ServerboundSetStructureBlockPacket
        assertEquals(0, shifted.pos.getX())
        assertEquals(0, shifted.pos.getZ())
        assertSame(relative, shifted.offset)
        assertSame(size, shifted.size)
    }
}
