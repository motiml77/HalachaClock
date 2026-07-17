package com.zmanimclock.app.feature.zmanim.data

import com.zmanimclock.app.feature.chaitables.data.ChaiTablesRepository
import com.zmanimclock.app.feature.zmanim.engine.DayZmanim
import com.zmanimclock.app.feature.zmanim.engine.EngineLocation
import com.zmanimclock.app.feature.zmanim.engine.MaranZmanimEngine
import com.zmanimclock.app.location.model.AppGeoLocation
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single entry point for daily zmanim.
 *
 * Wires the ChaiTables visible sunrise into [MaranZmanimEngine]: the visible
 * netz — when available for the location/date — becomes the base of the whole
 * day per the Ohr HaChaim / Chazon Yosef luach. When unavailable (no terrain
 * data, fetch failure), the engine transparently falls back to the mishor
 * (sea-level) sunrise and the result is flagged via
 * [DayZmanim.basedOnVisibleSunrise].
 */
@Singleton
class ZmanimRepository @Inject constructor(
    private val engine: MaranZmanimEngine,
    private val chaiTables: ChaiTablesRepository,
) {

    suspend fun getDayZmanim(
        location: AppGeoLocation,
        cityId: String?,
        date: LocalDate,
        candleLightingOffsetMinutes: Long = MaranZmanimEngine.DEFAULT_CANDLE_OFFSET_MINUTES,
        /** true = no network fetch (receivers / pre-unlock); mishor fallback. */
        cacheOnly: Boolean = false,
    ): DayZmanim {
        val visibleSunrise =
            chaiTables.getVisibleSunrise(location, cityId, date, allowNetwork = !cacheOnly)
        return engine.calculate(
            location = location.toEngineLocation(),
            date = date,
            visibleSunrise = visibleSunrise,
            candleLightingOffsetMinutes = candleLightingOffsetMinutes,
        )
    }

    private fun AppGeoLocation.toEngineLocation() = EngineLocation(
        name = cityNameEnglish,
        latitude = latitude,
        longitude = longitude,
        elevationMeters = elevation,
        timeZoneId = timeZone.id,
    )
}
