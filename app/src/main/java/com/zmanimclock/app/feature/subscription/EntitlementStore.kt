package com.zmanimclock.app.feature.subscription

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The last authoritative answer Play gave us, on DEVICE-PROTECTED storage.
 *
 * WHY NOT DATASTORE, LIKE EVERY OTHER PREFERENCE IN THIS APP.
 * The alarm scheduler re-arms alarms from [BootReceiver] on
 * `LOCKED_BOOT_COMPLETED` — Direct Boot, before the user has unlocked the
 * phone for the first time after a restart. Credential-protected storage is
 * not readable there, and Google Play services are not available there either,
 * so the scheduler can neither ask Play nor read a DataStore answer. It needs
 * a value it can read at that moment, and this is it — the same trick the app
 * already uses for the scheduling shadow next to it.
 *
 * Get this wrong and the failure is silent and terrible: every alarm quietly
 * fails to re-arm after a reboot, and nobody finds out until a morning nobody
 * woke up for.
 *
 * NEVER AUTHORITATIVE. This is a cache of Play's last answer, not a licence.
 * With no backend the app gets no notification when a subscription ends, so a
 * stale `true` left to stand would entitle a lapsed user forever. Everything
 * that reads it does so through [Entitlement.allowsAccess], which only lets the
 * cache decide when Play genuinely could not be reached, and only for
 * [Entitlement.OFFLINE_GRACE_DAYS].
 *
 * Excluded from backup and device transfer, so an entitlement cannot ride a
 * cloud restore onto a different Google account — see xml/backup_rules.xml.
 */
@Singleton
class EntitlementStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.createDeviceProtectedStorageContext()
        .getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** The last authoritative answer, or [Entitlement.UNKNOWN_NO_HISTORY]. */
    fun cached(): Entitlement {
        val checkedAt = prefs.getLong(KEY_CHECKED_AT, 0L)
        if (checkedAt == 0L) return Entitlement.UNKNOWN_NO_HISTORY
        return Entitlement(
            state = EntitlementState.UNKNOWN,
            lastKnownEntitled = prefs.getBoolean(KEY_ENTITLED, false),
            lastCheckMillis = checkedAt,
        )
    }

    /**
     * Record an answer that actually came from Play.
     *
     * Only ever called with a real result. An unreachable Play must leave the
     * previous answer and its timestamp untouched, or the offline grace window
     * would silently restart on every failed query and never expire.
     */
    fun record(entitled: Boolean, atMillis: Long = System.currentTimeMillis()) {
        prefs.edit()
            .putBoolean(KEY_ENTITLED, entitled)
            .putLong(KEY_CHECKED_AT, atMillis)
            .apply()
    }

    /**
     * Blocking read for callers with no coroutine and no Play access — the
     * boot-time scheduler and the alarm trigger receiver.
     */
    fun allowsAccessFromCache(): Boolean = cached().allowsAccess

    private companion object {
        const val FILE = "entitlement_shadow"
        const val KEY_ENTITLED = "entitled"
        const val KEY_CHECKED_AT = "checkedAt"
    }
}
