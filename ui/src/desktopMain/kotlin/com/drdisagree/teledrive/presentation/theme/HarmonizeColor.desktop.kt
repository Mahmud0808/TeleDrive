package com.drdisagree.teledrive.presentation.theme

import java.awt.Color as AwtColor

internal actual fun harmonizeColor(argb: Int, primaryArgb: Int): Int {
    val source = FloatArray(3)
    val target = FloatArray(3)
    AwtColor.RGBtoHSB(argb shr 16 and 0xFF, argb shr 8 and 0xFF, argb and 0xFF, source)
    AwtColor.RGBtoHSB(
        primaryArgb shr 16 and 0xFF,
        primaryArgb shr 8 and 0xFF,
        primaryArgb and 0xFF,
        target
    )
    val sourceDegrees = source[0] * 360f
    val targetDegrees = target[0] * 360f
    var delta = targetDegrees - sourceDegrees
    if (delta > 180f) delta -= 360f
    if (delta < -180f) delta += 360f
    val shift = delta.coerceIn(-MAX_SHIFT_DEGREES, MAX_SHIFT_DEGREES)
    val hue = ((sourceDegrees + shift + 360f) % 360f) / 360f
    val rgb = AwtColor.HSBtoRGB(hue, source[1], source[2])
    return (argb.toLong() and 0xFF000000L).toInt() or (rgb and 0xFFFFFF)
}

private const val MAX_SHIFT_DEGREES = 15f
