package com.voltia.app.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

interface ReeApiService {
    @GET("es/datos/mercados/precios-mercados-tiempo-real")
    suspend fun getPreciosMercado(
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String,
        @Query("time_trunc") timeTrunc: String = "hour"
    ): PvpcResponseDto
}
