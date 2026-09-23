package com.zmanimclock.app.feature.omer

import android.content.Context
import com.zmanimclock.app.feature.alarms.data.AlarmDao
import com.zmanimclock.app.feature.alarms.data.AlarmEntity
import com.zmanimclock.app.scheduling.RescheduleWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one place that turns the Sefirat HaOmer alert on and off.
 *
 * "On" means exactly one ACTIVE [AlarmEntity.omerPreset] row exists; the
 * alarms table is the single source of truth, so both the Settings switch and
 * the first-night prompt drive this and nothing has to be kept in sync with a
 * separate boolean. The row also appears in the alarms list like any alarm —
 * toggling or deleting it there flows back through [enabled] automatically.
 */
@Singleton
class OmerAlertManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmDao: AlarmDao,
) {

    /** True while an active omer-count alarm exists. */
    val enabled: Flow<Boolean> =
        alarmDao.getOmerAlarms().map { alarms -> alarms.any { it.isActive } }

    /**
     * Enable → reactivate the existing omer alarm, or create one if none.
     * Disable → delete every omer alarm (it is a managed, one-tap thing; a
     * lingering disabled row in the list would just be clutter). Reschedules
     * afterwards so the change takes effect immediately.
     */
    suspend fun setEnabled(on: Boolean) {
        val existing = alarmDao.getOmerAlarmsList()
        if (on) {
            if (existing.isEmpty()) {
                alarmDao.insertAlarm(AlarmEntity.omerPreset())
            } else {
                existing.filterNot { it.isActive }.forEach { alarmDao.setActive(it.id, true) }
            }
        } else {
            existing.forEach { alarmDao.deleteById(it.id) }
        }
        RescheduleWorker.enqueueUnique(context)
    }
}
