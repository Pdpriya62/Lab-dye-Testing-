package com.team11.labdip.data.model

import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.roundToInt

// Same sRGB / D65 constants as rgb2lab() in lab_dip_esp32.ino, so the target swatch matches the firmware's maths.
private const val XN = 0.95047
private const val ZN = 1.08883

/** CIE Lab to sRGB 0–255, clamped. Used to draw the target half of the swatch. */
fun labToRgb(l: Double, a: Double, b: Double): Triple<Int, Int, Int> {
    val fy = (l + 16) / 116
    val fx = fy + a / 500
    val fz = fy - b / 200
    fun inv(t: Double) = if (t * t * t > 0.008856) t * t * t else (t - 16.0 / 116) / 7.787
    val x = inv(fx) * XN
    val y = inv(fy)
    val z = inv(fz) * ZN
    val r = 3.2404542 * x - 1.5371385 * y - 0.4985314 * z
    val g = -0.9692660 * x + 1.8760108 * y + 0.0415560 * z
    val bl = 0.0556434 * x - 0.2040259 * y + 1.0572252 * z
    fun gamma(c: Double) = if (c <= 0.0031308) 12.92 * c else 1.055 * c.pow(1 / 2.4) - 0.055
    fun to255(c: Double) = (gamma(c) * 255).roundToInt().coerceIn(0, 255)
    return Triple(to255(r), to255(g), to255(bl))
}

/** Port of the firmware's rgb2lab(); kept for tests and the round-trip check. */
fun rgbToLab(r: Int, g: Int, b: Int): Triple<Double, Double, Double> {
    fun lin(c: Int): Double {
        val v = c / 255.0
        return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }
    fun f(t: Double) = if (t > 0.008856) cbrt(t) else 7.787 * t + 16.0 / 116
    val rl = lin(r); val gl = lin(g); val bl = lin(b)
    val x = (0.4124564 * rl + 0.3575761 * gl + 0.1804375 * bl) / XN
    val y = 0.2126729 * rl + 0.7151522 * gl + 0.0721750 * bl
    val z = (0.0193339 * rl + 0.1191920 * gl + 0.9503041 * bl) / ZN
    val fx = f(x); val fy = f(y); val fz = f(z)
    return Triple(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))
}
