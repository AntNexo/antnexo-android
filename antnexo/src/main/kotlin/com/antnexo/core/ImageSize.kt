package com.antnexo.core

import kotlin.math.roundToInt

data class Size(val width: Int, val height: Int)

/** The size that fits within max × max, keeping the aspect ratio; never scaled up. */
fun fittedSize(width: Int, height: Int, max: Int = 1600): Size {
    val longest = maxOf(width, height)
    if (longest <= max) return Size(width, height)
    val scale = max.toDouble() / longest
    return Size((width * scale).roundToInt(), (height * scale).roundToInt())
}
