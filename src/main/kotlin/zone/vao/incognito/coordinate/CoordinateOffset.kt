package zone.vao.incognito.coordinate

data class CoordinateOffset(val x: Int, val z: Int) {

    init {
        require(x % 16 == 0 && z % 16 == 0) { "Offset must be aligned to chunks" }
        require(x in -1_048_576..1_048_576 && z in -1_048_576..1_048_576) { "Offset is too large" }
    }

    val chunkX: Int get() = x / 16
    val chunkZ: Int get() = z / 16

    fun inverse(): CoordinateOffset = CoordinateOffset(-x, -z)

    fun x(value: Double, relative: Boolean = false): Double = if (relative) value else value + x

    fun z(value: Double, relative: Boolean = false): Double = if (relative) value else value + z

    companion object {
        val ZERO = CoordinateOffset(0, 0)
    }
}
