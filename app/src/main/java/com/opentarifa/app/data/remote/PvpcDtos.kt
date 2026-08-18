package com.opentarifa.app.data.remote

/**
 * Modelos que reflejan la forma exacta del JSON devuelto por
 * https://apidatos.ree.es/es/datos/mercados/precios-mercados-tiempo-real
 * Ver /docs/pvpc-api.md para el formato completo de la respuesta.
 */
data class PvpcResponseDto(
    val included: List<PvpcIncludedDto> = emptyList()
)

data class PvpcIncludedDto(
    val type: String,
    val id: String,
    val attributes: PvpcAttributesDto
)

data class PvpcAttributesDto(
    val title: String,
    val values: List<PvpcValueDto> = emptyList()
)

data class PvpcValueDto(
    val value: Double,
    val datetime: String
)
