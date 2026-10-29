package com.team11.labdip.ui.theme

import androidx.compose.ui.graphics.Color

// Dark glass palette from the reference mock-up. Dark-only by design.
val Night = Color(0xFF161714)
val Dusk = Color(0xFF22241F)
val Moss = Color(0xFF3E4636) // glow behind the hero card
val Glass = Color(0x14FFFFFF) // card fill
val GlassStrong = Color(0x26FFFFFF) // selected chip, pressed
val GlassEdge = Color(0x1AFFFFFF) // 1 dp card border
val Ink = Color(0xFFF4F3EE)
val InkSoft = Color(0xFFB0B0A8) // secondary text, ≥ 4.5:1 on Dusk
val InkFaint = Color(0xFF6F706A) // unlit gauge ticks, axis lines

// Accents for numbers, one per reading kind
val Ember = Color(0xFFFF6B2C) // bath
val Amber = Color(0xFFF7B634) // ambient
val Lime = Color(0xFFC5E26A) // light
val Aqua = Color(0xFF3FD4EA) // humidity

// Status: always paired with a word and an icon
val Pass = Color(0xFF7CCB95)
val Marginal = Color(0xFFE3C15A)
val Fail = Color(0xFFF08A8A)

// Neutral N5 grey (R=G=B) around the fabric swatch so no tint biases the shade
val SwatchSurround = Color(0xFF777777)
