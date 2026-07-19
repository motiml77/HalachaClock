package com.zmanimclock.app.feature.zmanim.engine

import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Verification dump: engine output for 5 cities across Israel × 4 dates
 * (today / +1 month / next-year winter / next-year summer) as CSV, consumed
 * by the cross-check script that compares against an independent NOAA
 * implementation and an external luach API. Not an assertion test.
 */
class PrintComparisonTablesTest {

    private val cities = listOf(
        EngineLocation("ירושלים", 31.778, 35.235, 0.0, "Asia/Jerusalem"),
        EngineLocation("תל אביב", 32.0853, 34.7818, 0.0, "Asia/Jerusalem"),
        EngineLocation("חיפה", 32.794, 34.9896, 0.0, "Asia/Jerusalem"),
        EngineLocation("באר שבע", 31.2518, 34.7913, 0.0, "Asia/Jerusalem"),
        EngineLocation("קרני שומרון", 32.1667, 35.0833, 0.0, "Asia/Jerusalem"),
    )

    private val dates = listOf(
        LocalDate.of(2026, 7, 19),
        LocalDate.of(2026, 8, 19),
        LocalDate.of(2027, 1, 19),
        LocalDate.of(2027, 7, 19),
    )

    @Test
    fun printCsv() {
        val engine = MaranZmanimEngine()
        val zone = ZoneId.of("Asia/Jerusalem")
        val fmt = DateTimeFormatter.ofPattern("HH:mm:ss")
        fun f(i: Instant?): String = i?.atZone(zone)?.format(fmt) ?: ""

        println("CSV_START")
        println("city,date,alot,misheyakir60,netz_mishor,shma_mga,shma_gra,tfila_mga,tfila_gra,chatzot,mincha_gedola,mincha_ketana,plag_yy,shkia,tzeit,tzeit_shabbat,rt,shaah_sec")
        for (city in cities) {
            for (date in dates) {
                val d = engine.calculate(city, date)
                println(
                    listOf(
                        city.name, date.toString(),
                        f(d.alotHashachar), f(d.misheyakir60), f(d.hanetzMishor),
                        f(d.sofZmanShmaMga), f(d.sofZmanShmaGra),
                        f(d.sofZmanTfilaMga), f(d.sofZmanTfilaGra),
                        f(d.chatzot), f(d.minchaGedola), f(d.minchaKetana),
                        f(d.plagHaminchaYalkutYosef), f(d.shkia),
                        f(d.tzeitHakochavim), f(d.tzeitShabbat), f(d.tzeitRabbeinuTam),
                        (d.shaahZmanisGra?.let { it / 1000 } ?: "").toString(),
                    ).joinToString(",")
                )
            }
        }
        println("CSV_END")
    }
}
