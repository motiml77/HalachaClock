package com.zmanimclock.app.feature.zmanim.engine

import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Verification dump aimed at EXTERNAL luach websites (MyZmanim, royzmanim /
 * Zemaneh Yosef, Chabad, Kaluach, yeshiva.org.il …) rather than at a
 * recomputation.
 *
 * Kept separate from [PrintComparisonTablesTest] because the matrix is
 * chosen to match what those sites will actually serve: well-known cities
 * they all carry, and dates spread across the seasons plus the two leap
 * cases. All at sea level (elevation 0), which is the basis every one of
 * those sites publishes for Israel.
 *
 * Not an assertion test — it prints CSV consumed by the comparison scripts.
 */
class ExternalComparisonDumpTest {

    private val cities = listOf(
        EngineLocation("ירושלים", 31.778, 35.235, 0.0, "Asia/Jerusalem"),
        EngineLocation("תל אביב", 32.0853, 34.7818, 0.0, "Asia/Jerusalem"),
        EngineLocation("בני ברק", 32.0807, 34.8338, 0.0, "Asia/Jerusalem"),
        EngineLocation("חיפה", 32.794, 34.9896, 0.0, "Asia/Jerusalem"),
        EngineLocation("באר שבע", 31.2518, 34.7913, 0.0, "Asia/Jerusalem"),
        EngineLocation("צפת", 32.9646, 35.4960, 0.0, "Asia/Jerusalem"),
    )

    private val dates = listOf(
        LocalDate.of(2026, 7, 29),   // high summer
        LocalDate.of(2026, 9, 21),   // near the autumn equinox
        LocalDate.of(2026, 12, 15),  // deep winter, standard time
        LocalDate.of(2027, 3, 22),   // Taanit Esther, Hebrew LEAP year 5787
        LocalDate.of(2028, 2, 29),   // Gregorian leap day
    )

    @Test
    fun printCsv() {
        val engine = MaranZmanimEngine()
        val zone = ZoneId.of("Asia/Jerusalem")
        val fmt = DateTimeFormatter.ofPattern("HH:mm:ss")
        fun f(i: Instant?): String = i?.atZone(zone)?.format(fmt) ?: ""

        println("EXT_CSV_START")
        println(
            "city,date,alot,misheyakir60,netz_mishor,shma_mga_luach,shma_mga16,shma_gra," +
                "tfila_mga_luach,tfila_mga16,tfila_gra,chatzot,mincha_gedola,mincha_ketana," +
                "plag_yy,shkia,tzeit,tzeit_lechumra,tzeit_shabbat,rt,shaah_sec"
        )
        for (city in cities) {
            for (date in dates) {
                val d = engine.calculate(city, date)
                println(
                    listOf(
                        city.name, date.toString(),
                        f(d.alotHashachar), f(d.misheyakir60), f(d.hanetzMishor),
                        f(d.sofZmanShmaMga), f(d.sofZmanShmaMga16), f(d.sofZmanShmaGra),
                        f(d.sofZmanTfilaMga), f(d.sofZmanTfilaMga16), f(d.sofZmanTfilaGra),
                        f(d.chatzot), f(d.minchaGedola), f(d.minchaKetana),
                        f(d.plagHaminchaYalkutYosef), f(d.shkia),
                        f(d.tzeitHakochavim), f(d.tzeitLechumra), f(d.tzeitShabbat),
                        f(d.tzeitRabbeinuTam),
                        (d.shaahZmanisGra?.let { it / 1000 } ?: "").toString(),
                    ).joinToString(",")
                )
            }
        }
        println("EXT_CSV_END")
    }
}
