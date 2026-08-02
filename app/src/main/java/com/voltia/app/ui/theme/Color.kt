package com.voltia.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Tokens de precio importados del proyecto "Sistema visual de Voltia" en
 * claude.ai/design (tokens/colors.css, --price-low/mid/high), convertidos de
 * OKLCH a sRGB. Cada categoría expone:
 *  - base: color de acento (precio sin extreme, icono de extreme, precio
 *    grande de la cabecera).
 *  - container: fondo de la fila / cabecera.
 *  - onContainer: texto de hora, y color del precio en best/worst.
 *
 * En oscuro los tonos usan menor croma que en claro (no solo un aclarado),
 * igual que hace el propio sistema de diseño.
 */

// Precio bajo — modo claro
val VoltiaLowLight = Color(0xFF00631B)
val VoltiaLowContainerLight = Color(0xFFD0F3D4)
val VoltiaLowOnContainerLight = Color(0xFF00340A)

// Precio bajo — modo oscuro
val VoltiaLowDark = Color(0xFF8BC993)
val VoltiaLowContainerDark = Color(0xFF113117)
val VoltiaLowOnContainerDark = Color(0xFFC4EAC8)

// Precio medio — modo claro
val VoltiaMidLight = Color(0xFF933800)
val VoltiaMidContainerLight = Color(0xFFFFDFC0)
val VoltiaMidOnContainerLight = Color(0xFF511A00)

// Precio medio — modo oscuro
val VoltiaMidDark = Color(0xFFD8B260)
val VoltiaMidContainerDark = Color(0xFF3C2B02)
val VoltiaMidOnContainerDark = Color(0xFFF3DBA9)

// Precio alto — modo claro
val VoltiaHighLight = Color(0xFFAC001E)
val VoltiaHighContainerLight = Color(0xFFFFD9D5)
val VoltiaHighOnContainerLight = Color(0xFF620911)

// Precio alto — modo oscuro
val VoltiaHighDark = Color(0xFFF4928A)
val VoltiaHighContainerDark = Color(0xFF4B1D1C)
val VoltiaHighOnContainerDark = Color(0xFFFFCDC7)
