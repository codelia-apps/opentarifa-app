package com.voltia.app.data.model

data class HourlyPrice(
    val hour: String,
    val hourStart: Int,
    val priceEurPerKwh: Double
)
