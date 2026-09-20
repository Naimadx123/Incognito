package net.minecraft.world.entity

import net.minecraft.world.phys.Vec3

@JvmRecord
data class PositionMoveRotation(val position: Vec3, val deltaMovement: Vec3, val yRot: Float, val xRot: Float)

enum class Relative { X, Y, Z, DELTA_X, DELTA_Z }
