package com.zmanimclock.desktop.reminder

import com.zmanimclock.app.feature.zmanim.model.ZmanKind
import com.zmanimclock.desktop.data.DesktopPrefs
import com.zmanimclock.desktop.data.DesktopZmanimService
import java.time.DayOfWeek
import com.zmanimclock.desktop.data.ZmanAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * The two parts of the reminder logic that are pure, and therefore the two
 * parts worth pinning: WHICH zman (if any) is due right now, and whether a
 * "fired" flag survives a restart.
 *
 * The scheduler's own loop is not tested here — it is a `delay` and a call to
 * these functions, and a test of it would test coroutines, not zmanim.
 */
class ReminderSchedulerTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val window = Duration.ofMinutes(5)
    private val now: Instant = Instant.parse("2026-08-07T16:30:00Z")

    private fun at(secondsFromNow: Long): Instant = now.plusSeconds(secondsFromNow)

    /**
     * sweepDue works on ALERTS now, not on bare zmanim — two alerts can hang
     * off the same zman with different offsets, so the fired-log had to key on
     * the alert. These cases are all about the DUE/MISSED arithmetic, which
     * did not change, so each kind is wrapped in a throwaway alert named after
     * itself and the assertions read the kind back off it.
     */
    private fun alertOn(kind: ZmanKind) = ZmanAlert(kind.name, kind.shortName, kind)

    // ---- reminderStateOf --------------------------------------------------

    @Test
    fun `a zman still in the future is not yet due`() {
        assertEquals(ReminderState.NOT_YET, reminderStateOf(at(1), now, window))
        assertEquals(ReminderState.NOT_YET, reminderStateOf(at(3600), now, window))
    }

    @Test
    fun `a zman happening exactly now is due`() {
        assertEquals(ReminderState.DUE, reminderStateOf(now, now, window))
    }

    @Test
    fun `a zman inside the delivery window is due`() {
        assertEquals(ReminderState.DUE, reminderStateOf(at(-1), now, window))
        assertEquals(ReminderState.DUE, reminderStateOf(at(-4 * 60 - 59), now, window))
    }

    /**
     * The boundary is not arbitrary: Windows' own wording for
     * ScheduledToastNotification is "remains off for LONGER THAN 5 minutes",
     * so exactly five minutes late is still delivered. A test that only
     * checked 4:00 and 10:00 would pass under either reading.
     */
    @Test
    fun `exactly the window late is still due, one second more is missed`() {
        assertEquals(ReminderState.DUE, reminderStateOf(at(-300), now, window))
        assertEquals(ReminderState.MISSED, reminderStateOf(at(-301), now, window))
    }

    @Test
    fun `a zman from hours ago is missed`() {
        assertEquals(ReminderState.MISSED, reminderStateOf(at(-6 * 3600), now, window))
    }

    // ---- sweepDue ---------------------------------------------------------

    @Test
    fun `nothing due yet handles nothing and shows nothing`() {
        val sweep = sweepDue(
            listOf(
                alertOn(ZmanKind.SHKIA) to at(60),
                alertOn(ZmanKind.TZEIT_LECHUMRA) to at(900),
            ),
            now,
            window,
        )
        assertNull(sweep.show)
        assertTrue(sweep.handled.isEmpty())
    }

    @Test
    fun `a single zman inside the window is shown`() {
        val sweep = sweepDue(listOf(alertOn(ZmanKind.SHKIA) to at(-120)), now, window)
        assertEquals(ZmanKind.SHKIA, sweep.show?.first?.kind)
        assertEquals(1, sweep.handled.size)
    }

    /**
     * The resume-from-sleep case. Everything that has gone by is written off
     * so it can never be evaluated again, but only ONE popup is produced — a
     * queue that unspools on wake is precisely the alarm-clock behaviour this
     * build refuses to have.
     */
    @Test
    fun `several due at once show exactly one — the most recent`() {
        val sweep = sweepDue(
            listOf(
                alertOn(ZmanKind.MINCHA_KETANA) to at(-280),
                alertOn(ZmanKind.PLAG_HAMINCHA) to at(-200),
                alertOn(ZmanKind.SHKIA) to at(-30),
                alertOn(ZmanKind.TZEIT_LECHUMRA) to at(600),
            ),
            now,
            window,
        )
        assertEquals(ZmanKind.SHKIA, sweep.show?.first?.kind)
        assertEquals(
            listOf(ZmanKind.MINCHA_KETANA, ZmanKind.PLAG_HAMINCHA, ZmanKind.SHKIA),
            sweep.handled.map { it.first.kind },
        )
    }

    /**
     * A machine that slept through the morning: the stale zmanim must still be
     * flagged, or every tick for the rest of the day would re-evaluate them.
     */
    @Test
    fun `stale zmanim are written off silently, not shown`() {
        val sweep = sweepDue(
            listOf(
                alertOn(ZmanKind.ALOT_HASHACHAR) to at(-11 * 3600),
                alertOn(ZmanKind.HANETZ) to at(-10 * 3600),
                alertOn(ZmanKind.SOF_ZMAN_SHMA_GRA) to at(-8 * 3600),
            ),
            now,
            window,
        )
        assertNull(sweep.show)
        assertEquals(3, sweep.handled.size)
    }

    @Test
    fun `a mix of missed and due shows only the due one`() {
        val sweep = sweepDue(
            listOf(
                alertOn(ZmanKind.CHATZOT) to at(-4 * 3600),
                alertOn(ZmanKind.MINCHA_GEDOLA) to at(-60),
            ),
            now,
            window,
        )
        assertEquals(ZmanKind.MINCHA_GEDOLA, sweep.show?.first?.kind)
        assertEquals(2, sweep.handled.size)
    }

    @Test
    fun `an empty schedule is not an error`() {
        val sweep = sweepDue(emptyList(), now, window)
        assertNull(sweep.show)
        assertTrue(sweep.handled.isEmpty())
    }

    // ---- weekdays end to end ----------------------------------------------

    /**
     * The scheduler resolves alerts against the REAL engine, so these exercise
     * the whole chain: alert -> zman -> offset -> weekday filter -> due.
     * A day mask that did not reach the scheduler would show up here and
     * nowhere else, since the model tests only prove the mask itself.
     */
    private fun scheduleOn(date: LocalDate, vararg alerts: ZmanAlert): List<String> {
        val svc = DesktopZmanimService(DesktopPrefs(alerts = alerts.toList()))
        val fired = FiredLog(File.createTempFile("fired", ".txt").apply { delete() })
        val scheduler = ReminderScheduler(
            service = svc,
            firedLog = fired,
            clock = { date.atTime(23, 59).atZone(svc.zone).toInstant() },
        )
        // Everything on `date` is in the past at 23:59, so a sweep reports the
        // full day's worth as handled — which is exactly the day's table.
        scheduler.sweepOnce()
        return fired.entriesForTest().map { it.substringAfter('|') }
    }

    @Test
    fun `an alert restricted to other days does not fire`() {
        // 2026-08-26 is a Wednesday.
        val wednesday = LocalDate.of(2026, 8, 26)
        val onlyFriday = ZmanAlert("a1", "ערב שבת", ZmanKind.SHKIA, -18, ZmanAlert.bitFor(DayOfWeek.FRIDAY))
        assertTrue(scheduleOn(wednesday, onlyFriday).isEmpty())
    }

    @Test
    fun `an alert on today's weekday does fire`() {
        val wednesday = LocalDate.of(2026, 8, 26)
        val onlyWednesday =
            ZmanAlert("a1", "מנחה", ZmanKind.MINCHA_KETANA, 0, ZmanAlert.bitFor(DayOfWeek.WEDNESDAY))
        assertEquals(listOf("a1"), scheduleOn(wednesday, onlyWednesday))
    }

    @Test
    fun `a disabled alert never reaches the schedule`() {
        val wednesday = LocalDate.of(2026, 8, 26)
        val off = ZmanAlert("a1", "כבוי", ZmanKind.SHKIA, 0, ZmanAlert.EVERY_DAY, enabled = false)
        assertTrue(scheduleOn(wednesday, off).isEmpty())
    }

    /**
     * Two alerts on the SAME zman must both fire. The fired-log used to key on
     * the zman, which meant the first one shown suppressed the second for the
     * rest of the day; it keys on the alert id now.
     */
    @Test
    fun `two alerts on one zman are tracked separately`() {
        val wednesday = LocalDate.of(2026, 8, 26)
        val a = ZmanAlert("a1", "להתכונן", ZmanKind.SHKIA, -30)
        val b = ZmanAlert("a2", "הדלקה", ZmanKind.SHKIA, -18)
        assertEquals(setOf("a1", "a2"), scheduleOn(wednesday, a, b).toSet())
    }

    // ---- FiredLog ---------------------------------------------------------

    private val day: LocalDate = LocalDate.of(2026, 8, 7)

    private fun logFile(): File = File(temp.newFolder(), "fired.txt")

    @Test
    fun `an unmarked zman has not fired`() {
        val log = FiredLog(logFile())
        assertFalse(log.hasFired(day, "SHKIA"))
    }

    @Test
    fun `marking is remembered`() {
        val log = FiredLog(logFile())
        log.markFired(day, "SHKIA")
        assertTrue(log.hasFired(day, "SHKIA"))
        assertFalse(log.hasFired(day, "HANETZ"))
        assertFalse(log.hasFired(day.plusDays(1), "SHKIA"))
    }

    /**
     * The reason the flag is on disk at all: a restart, a crash, or Windows'
     * Fast Startup must not be able to re-fire a reminder that already showed.
     */
    @Test
    fun `a flag survives a restart`() {
        val file = logFile()
        FiredLog(file).markFired(day, "SHKIA")

        val reopened = FiredLog(file)
        assertTrue(reopened.hasFired(day, "SHKIA"))
        assertFalse(reopened.hasFired(day, "TZEIT_LECHUMRA"))
    }

    @Test
    fun `marking twice is idempotent`() {
        val file = logFile()
        val log = FiredLog(file)
        log.markFired(day, "SHKIA")
        log.markFired(day, "SHKIA")
        assertEquals(1, log.size())
        assertEquals(1, FiredLog(file).size())
    }

    /**
     * The same zman name on two different days is two different flags — this
     * is what makes a reminder repeat tomorrow instead of being silenced
     * forever after its first showing.
     */
    @Test
    fun `the same zman on a different day is a different flag`() {
        val log = FiredLog(logFile())
        log.markFired(day, "SHKIA")
        assertFalse(log.hasFired(day.plusDays(1), "SHKIA"))
        log.markFired(day.plusDays(1), "SHKIA")
        assertTrue(log.hasFired(day, "SHKIA"))
        assertTrue(log.hasFired(day.plusDays(1), "SHKIA"))
    }

    /**
     * The cutoff is measured from the date being WRITTEN, not from
     * `LocalDate.now()` — that is what keeps [FiredLog] free of a hidden clock
     * dependency. Marks always happen for roughly today in practice, so the
     * effect is the same: a flag older than the retention window is gone by
     * the next time anything is recorded.
     */
    @Test
    fun `old flags are pruned so the file cannot grow forever`() {
        val file = logFile()
        val log = FiredLog(file, retentionDays = 3)
        log.markFired(day.minusDays(10), "SHKIA")
        log.markFired(day.minusDays(1), "HANETZ")
        log.markFired(day, "CHATZOT")

        assertFalse(log.hasFired(day.minusDays(10), "SHKIA"))
        assertTrue(log.hasFired(day.minusDays(1), "HANETZ"))
        assertTrue(log.hasFired(day, "CHATZOT"))
        assertEquals(2, FiredLog(file, retentionDays = 3).size())
    }

    /**
     * A log that cannot be read is worth one duplicate reminder; a log that
     * throws would be no reminders at all, and no clue why.
     */
    @Test
    fun `a corrupt log degrades to empty instead of throwing`() {
        val file = logFile()
        file.parentFile.mkdirs()
        file.writeText(" not a flag\n\n|||\n")

        val log = FiredLog(file)
        assertFalse(log.hasFired(day, "SHKIA"))
        log.markFired(day, "SHKIA")
        assertTrue(FiredLog(file).hasFired(day, "SHKIA"))
    }

    @Test
    fun `a missing directory is created on first write`() {
        val file = File(File(temp.newFolder(), "HalachClock"), "fired.txt")
        assertFalse(file.exists())
        FiredLog(file).markFired(day, "SHKIA")
        assertTrue(file.isFile)
        assertTrue(FiredLog(file).hasFired(day, "SHKIA"))
    }
}
