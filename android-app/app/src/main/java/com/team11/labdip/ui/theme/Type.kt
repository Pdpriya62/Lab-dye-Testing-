package com.team11.labdip.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.team11.labdip.R

// Variable-font axes need API 26+, which is minSdk
@OptIn(ExperimentalTextApi::class)
private fun archivo(weight: Int, width: Float) = Font(
    R.font.archivo,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.width(width)),
)

val ArchivoFamily = FontFamily(archivo(400, 100f), archivo(500, 100f), archivo(600, 100f), archivo(700, 100f))
// Condensed widths suit dense instrument readouts
val ArchivoCondensed = FontFamily(archivo(600, 80f), archivo(700, 75f))
val SerifItalic = FontFamily(Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic))

private const val TABULAR = "tnum"

object LabType {
    val title = TextStyle(fontFamily = SerifItalic, fontStyle = FontStyle.Italic, fontSize = 38.sp, lineHeight = 42.sp)
    val cardTitle = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 24.sp)
    val hero = TextStyle(fontFamily = ArchivoCondensed, fontWeight = FontWeight.Bold, fontSize = 64.sp, lineHeight = 64.sp, fontFeatureSettings = TABULAR)
    val big = TextStyle(fontFamily = ArchivoCondensed, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 38.sp, fontFeatureSettings = TABULAR)
    val value = TextStyle(fontFamily = ArchivoCondensed, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 24.sp, fontFeatureSettings = TABULAR)
    val status = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp)
    val body = TextStyle(fontFamily = ArchivoFamily, fontSize = 15.sp, lineHeight = 22.sp)
    val label = TextStyle(fontFamily = ArchivoFamily, fontSize = 13.sp, lineHeight = 18.sp, fontFeatureSettings = TABULAR)
    val small = TextStyle(fontFamily = ArchivoFamily, fontSize = 11.sp, lineHeight = 14.sp, fontFeatureSettings = TABULAR)
}

val AppTypography = Typography().run {
    copy(
        bodyLarge = bodyLarge.copy(fontFamily = ArchivoFamily),
        bodyMedium = bodyMedium.copy(fontFamily = ArchivoFamily),
        bodySmall = bodySmall.copy(fontFamily = ArchivoFamily),
        labelLarge = labelLarge.copy(fontFamily = ArchivoFamily),
        labelMedium = labelMedium.copy(fontFamily = ArchivoFamily),
        labelSmall = labelSmall.copy(fontFamily = ArchivoFamily),
        titleMedium = titleMedium.copy(fontFamily = ArchivoFamily),
        titleLarge = titleLarge.copy(fontFamily = ArchivoFamily),
    )
}
