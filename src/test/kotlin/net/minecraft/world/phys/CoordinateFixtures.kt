package net.minecraft.world.phys

import net.minecraft.core.BlockPos

data class Vec3(@JvmField val x: Double, @JvmField val y: Double, @JvmField val z: Double)

class BlockHitResult(val location: Vec3, val blockPos: BlockPos, val inside: Boolean)
