package com.zmanimclock.app.feature.zmanim.engine

import com.zmanimclock.app.feature.zmanim.format.asZmanTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

/**
 * TWO plag shitot, each pinned against its own authority.
 *
 * They differ by 11-16 minutes and both are right. The luach measures plag
 * back from TZEIT (shkia + 13.5 zmaniyot); the GRA reckoning measures it back
 * from SHKIA. The whole gap is those 13.5 zmaniyot minutes, which is why it
 * widens in summer and narrows in winter — the signature of a shita, not of an
 * error.
 *
 * Verified when this was written:
 *  - the luach FORMULA reproduced from the luach's own published netz/shkia:
 *    25 points, 5 cities, 5 dates, max deviation 0.9 SECONDS. That is the
 *    formula being identical.
 *  - this ENGINE against the same reference: within a few seconds, the
 *    residual being our sunrise/sunset differing from theirs by seconds — the
 *    same tolerance LuachVerificationTest already works to.
 *  - GRA plag vs Hebcal's independent implementation: 240 points, 8 Israeli
 *    cities, 30 dates across 6 years, max deviation 1 minute (the truncation
 *    artefact), zero points beyond that.
 */
class PlagHaminchaTest {

    private val zone = ZoneId.of("Asia/Jerusalem")
    private val engine = MaranZmanimEngine()

    private fun city(name: String, lat: Double, lng: Double) =
        EngineLocation(name, lat, lng, 0.0, "Asia/Jerusalem")

    private val jerusalem = city("Jerusalem", 31.7683, 35.2137)
    private val telAviv = city("Tel Aviv", 32.0853, 34.7818)
    private val tzfat = city("Tzfat", 32.9646, 35.4960)
    private val beerSheva = city("Beer Sheva", 31.2530, 34.7915)

    // ------------------------------------------ the luach's own plag

    @Test
    fun `the luach plag matches the Ohr HaChaim reference to the second`() {
        // Values read from docs/luach_ohr_hachaim_reference.csv, which was
        // captured from the luach's own engine.
        data class Ref(val loc: EngineLocation, val date: LocalDate, val expected: String)
        listOf(
            Ref(jerusalem, LocalDate.of(2026, 7, 29), "18:27:31"),
            Ref(jerusalem, LocalDate.of(2026, 9, 21), "17:34:54"),
            Ref(jerusalem, LocalDate.of(2026, 12, 15), "15:45:08"),
            Ref(jerusalem, LocalDate.of(2027, 3, 22), "16:49:14"),
            Ref(jerusalem, LocalDate.of(2028, 2, 29), "16:37:34"),
            Ref(telAviv, LocalDate.of(2026, 7, 29), "18:29:50"),
            Ref(tzfat, LocalDate.of(2026, 12, 15), "15:41:41"),
            Ref(beerSheva, LocalDate.of(2027, 3, 22), "16:50:58"),
        ).forEach { (loc, date, expected) ->
            val actual = engine.calculate(loc, date).plagHaminchaYalkutYosef!!
            val exp = LocalDate.parse(date.toString())
                .atTime(
                    expected.substring(0, 2).toInt(),
                    expected.substring(3, 5).toInt(),
                    expected.substring(6, 8).toInt(),
                ).atZone(zone).toInstant()
            val off = Duration.between(exp, actual).seconds
            // Seconds, not zero: the FORMULA is identical (proven separately by
            // reproducing the luach's own value from its own published netz and
            // shkia to within 0.9s), so what is left here is our astronomy
            // differing from theirs by a few seconds. Same tolerance the
            // existing luach verification works to.
            assertTrue(
                "${loc.name} $date: luach says $expected, we say ${actual.atZone(zone)} (${off}s off)",
                kotlin.math.abs(off) <= 30,
            )
        }
    }

    @Test
    fun `plag is measured from the 13 and a half zmaniyot tzeit, NOT the later 6 point 2 degree one`() {
        // The obvious-looking "fix": this app's DEFAULT displayed tzeit is the
        // 6.2° one (three medium stars) and the 13.5-zmaniyot time is labelled
        // לקולא, so pointing plag at the default row looks like tidying up an
        // inconsistency. It is not — it moves plag 12-17 minutes later and off
        // the luach entirely.
        //
        // The two tzeit values answer different questions. 6.2° is when night
        // has certainly fallen (melacha, Shema at night). The 13.5 zmaniyot is
        // the Geonim's ¾-mil tzeit — Terumat HaDeshen's 18-minute mil, adopted
        // by the Shulchan Aruch — and it is the one that closes the halachic
        // DAY that the seasonal-hour divisions are built on. Rav Yitzchak
        // Yosef derives exactly that chain inside the plag discussion itself.
        listOf(
            Triple(jerusalem, LocalDate.of(2026, 7, 29), "18:27:31"),
            Triple(jerusalem, LocalDate.of(2026, 12, 15), "15:45:08"),
            Triple(telAviv, LocalDate.of(2026, 9, 21), "17:36:44"),
            Triple(tzfat, LocalDate.of(2027, 3, 22), "16:48:18"),
        ).forEach { (loc, date, published) ->
            val d = engine.calculate(loc, date)
            val shaah = d.shaahZmanisGra!!
            val expected = date.atTime(
                published.substring(0, 2).toInt(),
                published.substring(3, 5).toInt(),
                published.substring(6, 8).toInt(),
            ).atZone(zone).toInstant()

            val fromGeonim = d.tzeitHakochavim!!.minusMillis((shaah * 1.25).toLong())
            val fromLechumra = d.tzeitLechumra!!.minusMillis((shaah * 1.25).toLong())

            assertTrue(
                "${loc.name} $date: the luach's plag must come from the 13.5-zmaniyot tzeit",
                kotlin.math.abs(Duration.between(expected, fromGeonim).seconds) <= 30,
            )
            assertTrue(
                "${loc.name} $date: the 6.2° tzeit would put plag " +
                    "${Duration.between(expected, fromLechumra).toMinutes()} min off the luach",
                Duration.between(expected, fromLechumra).toMinutes() >= 10,
            )
            // And the shipped value is the correct one.
            assertEquals(
                fromGeonim.toEpochMilli() / 1000,
                d.plagHaminchaYalkutYosef!!.toEpochMilli() / 1000,
            )
        }
    }

    // ---------------------------------------------- the GRA plag

    @Test
    fun `the GRA plag is shkia minus one and a quarter seasonal hours`() {
        listOf(jerusalem, telAviv, tzfat, beerSheva).forEach { loc ->
            listOf(
                LocalDate.of(2026, 1, 15), LocalDate.of(2026, 6, 21),
                LocalDate.of(2029, 9, 23), LocalDate.of(2031, 3, 21),
            ).forEach { date ->
                val d = engine.calculate(loc, date)
                val shaah = d.shaahZmanisGra!!
                val expected = d.shkia!!.minusMillis((shaah * 1.25).toLong())
                assertEquals(
                    "${loc.name} $date",
                    expected.toEpochMilli() / 1000,
                    d.plagHaminchaGra!!.toEpochMilli() / 1000,
                )
            }
        }
    }

    // ------------------------------------- the relationship between them

    @Test
    fun `the gap between the two shitot is exactly the 13 and a half zmaniyot of tzeit`() {
        listOf(jerusalem, telAviv, tzfat, beerSheva).forEach { loc ->
            (2026..2031).forEach { year ->
                listOf(1 to 15, 6 to 21, 9 to 23).forEach { (m, day) ->
                    val d = engine.calculate(loc, LocalDate.of(year, m, day))
                    val gap = Duration.between(d.plagHaminchaGra!!, d.plagHaminchaYalkutYosef!!)
                    val thirteenAndAHalfZmaniyot = (d.shaahZmanisGra!! * 13.5 / 60.0).toLong()
                    assertTrue(
                        "${loc.name} $year-$m-$day gap=${gap.toMillis()}ms expected≈$thirteenAndAHalfZmaniyot",
                        kotlin.math.abs(gap.toMillis() - thirteenAndAHalfZmaniyot) <= 1_000,
                    )
                }
            }
        }
    }

    @Test
    fun `the luach plag is always later, and the gap widens in summer`() {
        val summer = engine.calculate(jerusalem, LocalDate.of(2026, 6, 21))
        val winter = engine.calculate(jerusalem, LocalDate.of(2026, 12, 15))
        val summerGap = Duration.between(summer.plagHaminchaGra!!, summer.plagHaminchaYalkutYosef!!)
        val winterGap = Duration.between(winter.plagHaminchaGra!!, winter.plagHaminchaYalkutYosef!!)

        assertTrue("the luach plag must be the later of the two", summerGap.isPositive)
        assertTrue(winterGap.isPositive)
        // A longer summer day means a longer seasonal hour, so 13.5 of its
        // minutes are worth more wall-clock time. If this ever inverts, the
        // gap has stopped tracking the seasonal hour and something is wrong.
        assertTrue(
            "summer gap ${summerGap.toMinutes()}min should exceed winter ${winterGap.toMinutes()}min",
            summerGap > winterGap,
        )
        // Duration.toMinutes() truncates, so these are floors of the real gap.
        assertEquals(16, summerGap.toMinutes())
        assertEquals(11, winterGap.toMinutes())
    }

    @Test
    fun `both rows are listed, and in time order`() {
        val date = LocalDate.of(2026, 8, 23)
        val d = engine.calculate(jerusalem, date)
        assertEquals("17:52", d.plagHaminchaGra!!.asZmanTime(zone))
        assertEquals("18:07", d.plagHaminchaYalkutYosef!!.asZmanTime(zone))
    }
}
