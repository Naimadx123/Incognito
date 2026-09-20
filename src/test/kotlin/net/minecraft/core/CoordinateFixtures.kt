package net.minecraft.core

open class BlockPos(private val x: Int, private val y: Int, private val z: Int) {
    fun getX(): Int = x
    fun getY(): Int = y
    fun getZ(): Int = z
}

class SectionPos(x: Int, y: Int, z: Int) : BlockPos(x, y, z) {
    companion object {
        @JvmStatic fun of(x: Int, y: Int, z: Int): SectionPos = SectionPos(x, y, z)
    }
}
