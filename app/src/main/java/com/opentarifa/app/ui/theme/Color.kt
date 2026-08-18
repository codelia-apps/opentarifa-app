package com.opentarifa.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Tokens de precio importados del proyecto "Sistema visual de OpenTarifa" en
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
val OpenTarifaLowLight = Color(0xFF00631B)
val OpenTarifaLowContainerLight = Color(0xFFD0F3D4)
val OpenTarifaLowOnContainerLight = Color(0xFF00340A)

// Precio bajo — modo oscuro
val OpenTarifaLowDark = Color(0xFF8BC993)
val OpenTarifaLowContainerDark = Color(0xFF113117)
val OpenTarifaLowOnContainerDark = Color(0xFFC4EAC8)

// Precio medio — modo claro
val OpenTarifaMidLight = Color(0xFF933800)
val OpenTarifaMidContainerLight = Color(0xFFFFDFC0)
val OpenTarifaMidOnContainerLight = Color(0xFF511A00)

// Precio medio — modo oscuro
val OpenTarifaMidDark = Color(0xFFD8B260)
val OpenTarifaMidContainerDark = Color(0xFF3C2B02)
val OpenTarifaMidOnContainerDark = Color(0xFFF3DBA9)

// Precio alto — modo claro
val OpenTarifaHighLight = Color(0xFFAC001E)
val OpenTarifaHighContainerLight = Color(0xFFFFD9D5)
val OpenTarifaHighOnContainerLight = Color(0xFF620911)

// Precio alto — modo oscuro
val OpenTarifaHighDark = Color(0xFFF4928A)
val OpenTarifaHighContainerDark = Color(0xFF4B1D1C)
val OpenTarifaHighOnContainerDark = Color(0xFFFFCDC7)
