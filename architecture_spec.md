# Zmanim Clock - System Architecture Specification

## Table of Contents
1. [High-Level Architecture](#high-level-architecture)
2. [Part 1: Smart App Initialization](#part-1-smart-app-initialization)
3. [Part 2: Modular Architecture](#part-2-modular-architecture)
4. [Part 3: Remote Admin Control](#part-3-remote-admin-control)
5. [Part 4: Update Strategy](#part-4-update-strategy)
6. [Part 5: Code Implementation](#part-5-code-implementation)

---

## High-Level Architecture

```
+-------------------------------------------------------------------+
|                        PRESENTATION LAYER                          |
|  +------------+  +------------+  +-----------+  +---------------+ |
|  | ZmanimScreen|  | AlertScreen|  | Calendar  |  | Settings      | |
|  | (Compose)   |  | (Compose)  |  | (Compose) |  | (Compose)     | |
|  +------+------+  +-----+------+  +-----+-----+  +-------+-------+ |
|         |               |              |                  |         |
|  +------+------+  +-----+------+  +----+------+  +-------+-------+ |
|  |ZmanimVM     |  |AlertsVM    |  |CalendarVM |  |SettingsVM     | |
|  +------+------+  +-----+------+  +-----+-----+  +-------+-------+ |
+---------|----------------|--------------|-----------------|---------+
          |                |              |                 |
+---------|----------------|--------------|-----------------|---------+
|         v                v              v                 v         |
|                        DOMAIN LAYER                                 |
|  +------------------+  +-------------------+  +------------------+ |
|  | ZmanimRepository |  | AlertRepository   |  | SettingsRepo     | |
|  | (interface)       |  | (interface)       |  | (interface)      | |
|  +--------+---------+  +---------+---------+  +--------+---------+ |
|           |                      |                      |          |
|  +--------+---------+  +--------+---------+  +---------+--------+ |
|  | CalcZmanimUseCase|  | ScheduleAlertUC  |  | SyncConfigUC     | |
|  | GetNextZmanUC    |  | GetAlertsUC      |  | ToggleFeatureUC  | |
|  +------------------+  +------------------+  +------------------+ |
+---------|----------------|--------------|-----------------|---------+
          |                |              |                 |
+---------|----------------|--------------|-----------------|---------+
|         v                v              v                 v         |
|                         DATA LAYER                                  |
|  +------------------+  +-------------------+  +------------------+ |
|  | ZmanimCalcEngine |  | Room Database     |  | DataStore Prefs  | |
|  | (KosherJava)     |  | (Alerts, Cache)   |  | (User Settings)  | |
|  +------------------+  +-------------------+  +------------------+ |
|  +------------------+  +-------------------+  +------------------+ |
|  | LocationProvider |  | Firebase Remote   |  | Firebase FCM     | |
|  | (FusedLocation)  |  | Config            |  | (Push Messages)  | |
|  +------------------+  +-------------------+  +------------------+ |
+--------------------------------------------------------------------+

+--------------------------------------------------------------------+
|                     BACKGROUND SERVICES                             |
|  +------------------+  +-------------------+  +------------------+ |
|  | MidnightWorker   |  | AlertScheduler    |  | ConfigSyncWorker | |
|  | (pre-calc daily) |  | (AlarmManager)    |  | (periodic sync)  | |
|  +------------------+  +-------------------+  +------------------+ |
+--------------------------------------------------------------------+
```

---

## Part 1: Smart App Initialization

### Cold Start Timeline Target: < 400ms to first frame

```
PROCESS START (0ms)
  |
  +-- ContentProvider-based init (AndroidX Startup)       [0-50ms]
  |     +-- Initialize Hilt DI graph
  |     +-- Read cached zmanim from DataStore (last known)
  |     +-- Read feature flags from local cache
  |
  +-- Application.onCreate()                              [50-100ms]
  |     +-- Create notification channels
  |     +-- Check if zmanim cache is stale (date changed?)
  |     +-- If stale: launch coroutine to recalculate (non-blocking)
  |     +-- Schedule midnight WorkManager (if not already)
  |
  +-- Splash / First Frame                                [100-200ms]
  |     +-- Show cached zmanim immediately (even if stale)
  |     +-- Show loading shimmer only on stale items
  |
  +-- Lazy init (after first frame)                       [200ms+]
        +-- Firebase Remote Config fetch
        +-- Location refresh (if GPS mode)
        +-- Recalculated zmanim swap in via StateFlow
        +-- Sync admin messages
```

### What Loads Immediately vs Lazily

| Component | When | Why |
|-----------|------|-----|
| Hilt DI graph | Process start | Required for everything |
| Cached zmanim (DataStore) | Process start | Show UI instantly |
| Feature flags (local cache) | Process start | Determine visible features |
| Notification channels | Application.onCreate | Required for foreground service |
| KosherJava calculation | Lazy (coroutine) | CPU-intensive, ~50ms |
| Firebase Remote Config | Lazy (after first frame) | Network dependent |
| Location refresh | Lazy (after first frame) | Slow, permission-dependent |
| Hebrew calendar data | Lazy (first access) | Only needed for calendar tab |
| FCM token registration | Lazy (after first frame) | Non-critical |
| Crashlytics | Lazy (deferred init) | Non-critical |

### Splash Screen Strategy

Use the Android 12+ SplashScreen API with a branded icon. No custom splash Activity.

```kotlin
// In themes.xml - the system handles the splash screen
<style name="Theme.ZmanimClock" parent="Theme.Material3.DayNight">
    <item name="android:windowSplashScreenAnimatedIcon">@drawable/splash_icon</item>
    <item name="android:windowSplashScreenBackground">@color/splash_bg</item>
</style>
```

The key insight: display cached zmanim from DataStore the moment the main Activity renders. The user sees real data immediately, not a loading screen. If the cache is from today, no recalculation is needed at all.

### Background WorkManager Tasks

```
+-------------------------------------------------------------+
|                   WorkManager Schedule                        |
+-------------------------------------------------------------+
| MidnightRecalcWorker    | OneTime, midnight   | EXPEDITED   |
|   - Recalculate all zmanim for new day                      |
|   - Update widget                                            |
|   - Reschedule all alerts                                    |
+-------------------------------------------------------------+
| ConfigSyncWorker        | Periodic, 6 hours   | REGULAR     |
|   - Fetch Firebase Remote Config                             |
|   - Apply feature flag changes                               |
|   - Check for admin messages                                 |
+-------------------------------------------------------------+
| LocationRefreshWorker   | Periodic, 12 hours  | REGULAR     |
|   - Refresh GPS location (if GPS mode enabled)               |
|   - Recalculate if location changed significantly            |
+-------------------------------------------------------------+
| WidgetUpdateWorker      | Per-minute via Alarm | EXPEDITED   |
|   - Update widget countdown/next zman display                |
+-------------------------------------------------------------+
```

### Pre-Calculation at Boot and Midnight

```kotlin
// Boot receiver triggers immediate recalculation
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            MidnightRecalcWorker.enqueueImmediate(context)
        }
    }
}

// MidnightRecalcWorker also schedules itself for the NEXT midnight
class MidnightRecalcWorker(ctx: Context, params: WorkerParameters)
    : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        // 1. Get location (cached or fresh)
        // 2. Calculate all enabled zmanim
        // 3. Cache to DataStore
        // 4. Schedule alerts
        // 5. Update widget
        // 6. Schedule next midnight run
        return Result.success()
    }
}
```

### Startup Profiling Strategy

Use AndroidX Startup Tracing and Macrobenchmark:

```kotlin
// In debug builds, add startup trace points
class ZmanimApp : Application() {
    override fun onCreate() {
        Trace.beginSection("ZmanimApp.onCreate")
        super.onCreate()
        // ...
        Trace.endSection()
    }
}
```

Baseline Profile should include:
- `ZmanimApp.onCreate`
- `MainActivity.onCreate`
- Main Compose screen rendering
- DataStore read paths
- KosherJava `ComplexZmanimCalendar` instantiation

---

## Part 2: Modular Architecture for Easy Updates

### Module Dependency Diagram

```
:app (Application shell)
  |
  +-- :core:common         (shared models, utils, Result types)
  |
  +-- :core:data            (Room DB, DataStore, network clients)
  |     +-- :core:common
  |
  +-- :core:domain          (use cases, repository interfaces)
  |     +-- :core:common
  |
  +-- :core:zmanim-engine   (KosherJava wrapper, calculation logic)
  |     +-- :core:common
  |
  +-- :core:remote          (Firebase Remote Config, FCM, Crashlytics)
  |     +-- :core:common
  |
  +-- :feature:zmanim       (zmanim list screen)
  |     +-- :core:domain
  |     +-- :core:common
  |
  +-- :feature:alerts       (alert management)
  |     +-- :core:domain
  |     +-- :core:common
  |
  +-- :feature:calendar     (Hebrew calendar)
  |     +-- :core:domain
  |     +-- :core:common
  |
  +-- :feature:settings     (user preferences)
  |     +-- :core:domain
  |     +-- :core:common
  |
  +-- :feature:widget       (Glance widget)
  |     +-- :core:domain
  |     +-- :core:zmanim-engine
  |
  +-- :feature:onboarding   (first-run setup)
       +-- :core:common
```

For the current single-module stage, use package-level separation as already structured in the codebase. The above module split is the target for when the app grows. The important thing now is to use **interface-based dependencies** so modules can be split out later without refactoring.

### Interface-Based Dependencies

```kotlin
// :core:domain - Repository interfaces
interface ZmanimRepository {
    fun getTodayZmanim(): Flow<DayZmanim>
    fun getZmanimForDate(date: LocalDate): Flow<DayZmanim>
    suspend fun recalculate(location: AppGeoLocation)
    suspend fun invalidateCache()
}

interface ZmanCalculator {
    fun calculate(zmanId: ZmanId, location: AppGeoLocation, date: LocalDate): Date?
}

interface FeatureFlagProvider {
    fun isEnabled(flag: FeatureFlag): Boolean
    fun getLong(key: String, default: Long): Long
    fun getString(key: String, default: String): String
    fun observeChanges(): Flow<Set<String>>  // emits changed keys
}

interface AdminMessageRepository {
    fun getMessages(): Flow<List<AdminMessage>>
    suspend fun markRead(messageId: String)
    suspend fun dismiss(messageId: String)
}
```

The DI module then binds implementations:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun bindZmanimRepo(impl: ZmanimRepositoryImpl): ZmanimRepository
    @Binds abstract fun bindFeatureFlags(impl: FirebaseFeatureFlagProvider): FeatureFlagProvider
    @Binds abstract fun bindAdminMessages(impl: FcmAdminMessageRepository): AdminMessageRepository
}
```

### Version-Safe Room Database Migrations

Strategy: **Always use additive migrations. Never delete columns.**

```kotlin
@Database(
    entities = [
        AlertEntity::class,
        ZmanimCacheEntity::class,
        AdminMessageEntity::class,
    ],
    version = 3,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3, spec = Migration2To3::class),
    ]
)
abstract class ZmanimDatabase : RoomDatabase() {
    abstract fun alertDao(): AlertDao
    abstract fun cacheDao(): ZmanimCacheDao
    abstract fun adminMessageDao(): AdminMessageDao
}

@RenameColumn(tableName = "alerts", fromColumnName = "zman_name", toColumnName = "zman_id")
class Migration2To3 : AutoMigrationSpec
```

For truly breaking changes, use a `FallbackMigration` that exports data, drops, recreates, and re-imports:

```kotlin
val MIGRATION_FALLBACK = object : Migration(OLD, NEW) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. CREATE TABLE alerts_backup AS SELECT * FROM alerts
        // 2. DROP TABLE alerts
        // 3. CREATE TABLE alerts (new schema)
        // 4. INSERT INTO alerts SELECT ... FROM alerts_backup
        // 5. DROP TABLE alerts_backup
    }
}
```

### Adding New Zmanim Without Breaking Existing Code

The current `ZmanId` enum is the registry. Adding a new zman:

1. Add enum entry to `ZmanId` (backward compatible, Room stores strings)
2. Add calculation in `ZmanCalculatorImpl`
3. Add string resource for Hebrew name
4. Done -- the UI auto-discovers from the enum

For a fully decoupled plugin approach:

```kotlin
// Plugin interface for halachic opinion providers
interface ZmanPlugin {
    val id: String
    val displayName: String
    val category: ZmanCategory
    val opinion: ZmanOpinion
    fun calculate(calendar: ComplexZmanimCalendar): Date?
    fun description(): String
}

// Registry collects all plugins
@Singleton
class ZmanPluginRegistry @Inject constructor(
    private val plugins: Set<@JvmSuppressWildcards ZmanPlugin>
) {
    fun getAll(): List<ZmanPlugin> = plugins.toList()
    fun getByCategory(cat: ZmanCategory) = plugins.filter { it.category == cat }
    fun getById(id: String) = plugins.first { it.id == id }
}

// Hilt multi-binding for plugins
@Module
@InstallIn(SingletonComponent::class)
abstract class ZmanPluginModule {
    @Binds @IntoSet
    abstract fun bindAlos72(impl: Alos72Plugin): ZmanPlugin

    @Binds @IntoSet
    abstract fun bindHanetz(impl: HanetzPlugin): ZmanPlugin

    // Adding a new zman = add one @Binds @IntoSet line + implement ZmanPlugin
}
```

### Adding New Alert Types Without Changing the DB Schema

Use a flexible `extras` JSON column:

```kotlin
@Entity(tableName = "alerts")
data class AlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val zmanId: String,                    // which zman
    val alertType: String,                 // "NOTIFICATION", "ALARM", "VIBRATE", "SILENT"
    val offsetMinutes: Int,                // minutes before/after zman
    val isEnabled: Boolean,
    val extras: String = "{}",             // JSON for future extension
    val createdAt: Long = System.currentTimeMillis(),
)
```

The `extras` JSON column allows adding properties (custom sound URI, repeat count, snooze behavior) without schema changes. Parse with Moshi:

```kotlin
data class AlertExtras(
    val customSoundUri: String? = null,
    val repeatCount: Int = 1,
    val snoozeMinutes: Int = 5,
    val daysOfWeek: List<Int>? = null,      // null = every day
    val onlyOnShabbat: Boolean = false,
    val vibrationPattern: List<Long>? = null,
)
```

### Plugin Architecture for Halachic Opinions

```
+-------------------------------------------------------+
|             Halachic Opinion System                    |
+-------------------------------------------------------+
|                                                        |
|  HalachicProfile (sealed class)                        |
|    +-- SephardiStandard    (R' Ovadia defaults)        |
|    +-- AshkenaziStandard   (Mishna Berura defaults)    |
|    +-- Custom              (user picks per-zman)       |
|                                                        |
|  Each profile maps ZmanId -> enabled/disabled          |
|  plus default alert preferences                        |
|                                                        |
+-------------------------------------------------------+
```

```kotlin
sealed class HalachicProfile(val id: String, val displayName: String) {
    abstract val defaultZmanim: Set<ZmanId>
    abstract val defaultCandleLightingMinutes: Int

    data object SephardiStandard : HalachicProfile("sephardi", "ספרדי - לוח אור החיים") {
        override val defaultZmanim = setOf(
            ZmanId.ALOS_72, ZmanId.MISHEYAKIR_60, ZmanId.HANETZ_SEA,
            ZmanId.SOF_ZMAN_SHMA_MGA, ZmanId.SOF_ZMAN_SHMA_GRA,
            ZmanId.SOF_ZMAN_TFILA_GRA, ZmanId.MINCHA_GEDOLA,
            ZmanId.PLAG_YALKUT_YOSEF, ZmanId.SHKIA_SEA,
            ZmanId.TZAIS_13_5_ZMANIYOT, ZmanId.TZAIS_SHABBAT_AH,
            ZmanId.CANDLE_LIGHTING, ZmanId.SHAAH_ZMANIT_GRA,
        )
        override val defaultCandleLightingMinutes = 30  // Jerusalem: 40
    }

    data object AshkenaziStandard : HalachicProfile("ashkenazi", "אשכנזי - משנה ברורה") {
        override val defaultZmanim = setOf(
            ZmanId.ALOS_72, ZmanId.HANETZ_SEA,
            ZmanId.SOF_ZMAN_SHMA_GRA, ZmanId.SOF_ZMAN_SHMA_MGA,
            ZmanId.SOF_ZMAN_TFILA_GRA, ZmanId.MINCHA_GEDOLA,
            ZmanId.SHKIA_SEA, ZmanId.TZAIS_8_5,
            ZmanId.CANDLE_LIGHTING, ZmanId.SHAAH_ZMANIT_GRA,
        )
        override val defaultCandleLightingMinutes = 18
    }

    data class Custom(val overrides: Map<ZmanId, Boolean>)
        : HalachicProfile("custom", "מותאם אישית") {
        override val defaultZmanim = overrides.filter { it.value }.keys
        override val defaultCandleLightingMinutes = 18
    }
}
```

---

## Part 3: Remote Admin Control (Firebase)

### Architecture Overview

```
+------------------+          +-----------------------+
|   Admin Panel    |          |   Firebase Console    |
|  (Web / Custom)  |          |   (Remote Config)     |
+--------+---------+          +-----------+-----------+
         |                                |
         v                                v
+--------+--------------------------------+-----------+
|                FIREBASE SERVICES                     |
|  +-------------+  +---------------+  +------------+ |
|  |   FCM       |  | Remote Config |  | Crashlytics| |
|  | (push msgs) |  | (feature flags|  | (crash rpt)| |
|  |             |  |  + overrides) |  |            | |
|  +------+------+  +-------+-------+  +-----+------+ |
+---------|-----------------|-----------------|---------+
          |                 |                 |
          v                 v                 v
+---------+-----------------+-----------------+---------+
|              ANDROID APP                              |
|  +----------------+  +------------------+             |
|  |AdminMsgHandler |  |RemoteConfigMgr   |             |
|  | (processes FCM)|  | (syncs flags)    |             |
|  +-------+--------+  +--------+---------+             |
|          |                     |                      |
|          v                     v                      |
|  +-------+---------------------+---------+            |
|  |        FeatureFlagProvider            |            |
|  |  (merges local defaults + remote)     |            |
|  +---------------------------------------+            |
+-------------------------------------------------------+
```

### A. Firebase Remote Config

#### Config Key Structure Convention

```
Format: {category}_{feature}_{detail}

Categories:
  zman_    = zman-specific flags
  ui_      = UI/UX flags
  calc_    = calculation parameters
  alert_   = alert system flags
  admin_   = admin messages
  exp_     = experiments / A-B tests
```

#### Complete Config JSON Structure

```json
{
  "zman_alos_72_enabled": true,
  "zman_alos_90_enabled": false,
  "zman_misheyakir_66_enabled": false,
  "zman_misheyakir_60_enabled": true,
  "zman_hanetz_sea_enabled": true,
  "zman_hanetz_elevated_enabled": false,
  "zman_hanetz_visible_enabled": false,
  "zman_shma_gra_enabled": true,
  "zman_shma_mga_enabled": true,
  "zman_tfila_gra_enabled": true,
  "zman_tfila_mga_enabled": true,
  "zman_mincha_gedola_enabled": true,
  "zman_plag_yy_enabled": true,
  "zman_shkia_sea_enabled": true,
  "zman_shkia_elevated_enabled": false,
  "zman_tzais_8_5_enabled": true,
  "zman_tzais_13_5_enabled": true,
  "zman_tzais_shabbat_ah_enabled": true,
  "zman_candle_lighting_enabled": true,
  "zman_shaah_gra_enabled": true,

  "calc_candle_lighting_minutes_default": 18,
  "calc_candle_lighting_minutes_jerusalem": 40,
  "calc_use_elevation": false,
  "calc_tzais_degrees_override": -1.0,

  "ui_show_seconds": false,
  "ui_show_countdown_to_next": true,
  "ui_dark_mode_default": "system",
  "ui_show_parasha": true,
  "ui_show_daf_yomi": true,
  "ui_font_size_scale": 1.0,
  "ui_max_zmanim_per_screen": 20,

  "alert_max_per_day": 30,
  "alert_vibrate_default": true,
  "alert_sound_enabled": true,

  "admin_banner_message": "",
  "admin_banner_type": "none",
  "admin_banner_url": "",
  "admin_force_update_version": 0,
  "admin_recommended_version": 0,
  "admin_maintenance_mode": false,
  "admin_maintenance_message": "",

  "exp_new_clock_ui": false,
  "exp_analog_clock": false,

  "app_min_supported_version": 1,
  "app_latest_version": 1,
  "app_update_url": "https://play.google.com/store/apps/details?id=com.zmanimclock.app"
}
```

#### Default Values (work offline)

```kotlin
object RemoteConfigDefaults {
    val defaults: Map<String, Any> = buildMap {
        // All zmanim default to their ZmanId.defaultEnabled
        ZmanId.entries.forEach { zman ->
            put("zman_${zman.name.lowercase()}_enabled", zman.defaultEnabled)
        }

        // Calculation defaults
        put("calc_candle_lighting_minutes_default", 18L)
        put("calc_candle_lighting_minutes_jerusalem", 40L)
        put("calc_use_elevation", false)
        put("calc_tzais_degrees_override", -1.0)

        // UI defaults
        put("ui_show_seconds", false)
        put("ui_show_countdown_to_next", true)
        put("ui_dark_mode_default", "system")
        put("ui_show_parasha", true)
        put("ui_show_daf_yomi", true)
        put("ui_font_size_scale", 1.0)

        // Alert defaults
        put("alert_max_per_day", 30L)
        put("alert_vibrate_default", true)

        // Admin defaults
        put("admin_banner_message", "")
        put("admin_banner_type", "none")
        put("admin_force_update_version", 0L)
        put("admin_maintenance_mode", false)

        // App version
        put("app_min_supported_version", 1L)
    }
}
```

### B. Firebase Cloud Messaging (FCM)

#### Message Types and Topic Structure

```
FCM Topics:
  /topics/all                    - all users
  /topics/city_{cityId}          - by city (e.g., city_jerusalem, city_tel_aviv)
  /topics/nusach_{nusach}        - by nusach (sephardi, ashkenazi)
  /topics/version_{versionCode}  - by app version
  /topics/beta                   - beta testers
  /topics/admin                  - admin notifications
```

#### Message Type Definitions

```kotlin
enum class AdminMessageType {
    BUG_FIX,           // "תיקון: זמן X חושב בצורה שגויה"
    UPDATE_AVAILABLE,  // "עדכון חדש זמין"
    RABBINICAL,        // "הודעה מהרב: ..."
    DST_CHANGE,        // "שינוי שעון - בדקו את הזמנים"
    MAINTENANCE,       // "תחזוקה מתוכננת"
    GENERAL,           // general announcement
}
```

#### FCM Payload Examples

**Bug Fix Notification:**
```json
{
  "message": {
    "topic": "all",
    "data": {
      "type": "BUG_FIX",
      "title": "תיקון חישוב",
      "body": "תיקון: זמן פלג המנחה חושב בצורה שגויה בגרסה 1.2. עדכנו לגרסה 1.3",
      "action": "UPDATE",
      "affected_zman": "PLAG_YALKUT_YOSEF",
      "fixed_in_version": "3",
      "priority": "high",
      "persist": "true"
    }
  }
}
```

**Rabbinical Announcement:**
```json
{
  "message": {
    "topic": "nusach_sephardi",
    "data": {
      "type": "RABBINICAL",
      "title": "הודעה חשובה",
      "body": "הרב פסק שיש לברך ברכת האילנות עד סוף ניסן",
      "action": "SHOW",
      "expiry": "2026-05-01T00:00:00Z",
      "priority": "normal",
      "persist": "true"
    }
  }
}
```

**DST Change Alert:**
```json
{
  "message": {
    "topic": "city_jerusalem",
    "data": {
      "type": "DST_CHANGE",
      "title": "שינוי שעון",
      "body": "הלילה עוברים לשעון חורף. הזמנים יתעדכנו אוטומטית. בדקו שההתראות תקינות.",
      "action": "RECALCULATE",
      "priority": "high",
      "persist": "false"
    }
  }
}
```

**Force Update:**
```json
{
  "message": {
    "topic": "version_1",
    "data": {
      "type": "UPDATE_AVAILABLE",
      "title": "עדכון חובה",
      "body": "גרסה 1.0 כבר אינה נתמכת. אנא עדכנו לגרסה האחרונה.",
      "action": "FORCE_UPDATE",
      "update_url": "https://play.google.com/store/apps/details?id=com.zmanimclock.app",
      "priority": "high",
      "persist": "true"
    }
  }
}
```

### C. Firebase Crashlytics

#### Setup and Custom Keys

```kotlin
@Singleton
class CrashlyticsManager @Inject constructor() {

    fun setUserContext(cityId: String, nusach: String) {
        Firebase.crashlytics.apply {
            setCustomKey("city_id", cityId)
            setCustomKey("nusach", nusach)
        }
    }

    fun logZmanCalculation(zmanId: String, location: String, result: String) {
        Firebase.crashlytics.apply {
            setCustomKey("last_zman_calculated", zmanId)
            setCustomKey("last_location", location)
            setCustomKey("last_calc_result", result)
            log("Calculated $zmanId at $location -> $result")
        }
    }

    fun logConfigSync(success: Boolean, changedKeys: Int) {
        Firebase.crashlytics.apply {
            setCustomKey("last_config_sync", System.currentTimeMillis().toString())
            setCustomKey("config_sync_success", success)
            setCustomKey("config_changed_keys", changedKeys)
        }
    }

    fun recordNonFatal(exception: Exception, context: Map<String, String> = emptyMap()) {
        context.forEach { (k, v) -> Firebase.crashlytics.setCustomKey(k, v) }
        Firebase.crashlytics.recordException(exception)
    }
}
```

#### Custom Keys for Debugging

| Key | Purpose |
|-----|---------|
| `city_id` | Which city the user is in |
| `nusach` | Sephardi / Ashkenazi |
| `last_zman_calculated` | Which zman triggered the crash |
| `last_location` | Lat/lon at time of crash |
| `last_calc_result` | Result or "null" |
| `last_config_sync` | Timestamp of last Remote Config sync |
| `app_version` | Version code |
| `enabled_zmanim_count` | How many zmanim are enabled |
| `active_alerts_count` | How many alerts are set |

### D. Admin Dashboard

#### Admin Actions Available

| Action | How | Tool |
|--------|-----|------|
| Enable/disable any zman globally | Remote Config | Firebase Console |
| Push announcement to all users | FCM topic message | Firebase Console / Admin SDK |
| Push announcement to specific city | FCM topic `city_X` | Firebase Console |
| Force app update | Remote Config `admin_force_update_version` | Firebase Console |
| Show maintenance banner | Remote Config `admin_maintenance_mode` | Firebase Console |
| Override calculation parameter | Remote Config `calc_*` | Firebase Console |
| View crash reports | Crashlytics dashboard | Firebase Console |
| A/B test new UI | Remote Config + Analytics | Firebase Console |
| View active users by city | Analytics | Firebase Console |

#### Recommended Approach: Firebase Console + Cloud Functions

For MVP, use the Firebase Console directly. It provides Remote Config editing, FCM topic messaging, and Crashlytics viewing.

For a custom admin panel later, use Firebase Admin SDK with Cloud Functions:

```
Admin Panel (simple web app)
    |
    v
Cloud Functions (REST API)
    |
    +-- POST /admin/message    -> sends FCM
    +-- POST /admin/config     -> updates Remote Config
    +-- GET  /admin/crashes    -> reads Crashlytics
    +-- POST /admin/override   -> sets zman calculation override
```

---

## Part 4: Update Strategy

### Pushing Zmanim Fixes Without Full App Update

```
Strategy: Server-side calculation overrides via Remote Config

Remote Config key: calc_override_{zmanId}
Value: JSON with override parameters

Example:
  "calc_override_PLAG_YALKUT_YOSEF": {
    "method": "degrees",
    "value": 10.75,
    "from_version": 1,
    "to_version": 2,
    "message": "תיקון: פלג חושב מהשקיעה במקום מצה\"כ"
  }
```

The app checks for overrides before using default calculation:

```kotlin
class ZmanCalculatorImpl @Inject constructor(
    private val remoteConfig: RemoteConfigManager,
) : ZmanCalculator {

    override fun calculate(zmanId: ZmanId, location: AppGeoLocation, date: LocalDate): Date? {
        // Check for remote override first
        val override = remoteConfig.getCalcOverride(zmanId)
        if (override != null && isApplicable(override)) {
            return calculateWithOverride(override, location, date)
        }

        // Default calculation
        return calculateDefault(zmanId, location, date)
    }
}
```

### A/B Testing

Use Firebase Remote Config conditions:

```
Condition: "10% of users" -> exp_new_clock_ui = true
Condition: "Beta testers"  -> exp_analog_clock = true
```

The app reads these as regular feature flags.

### Staged Rollouts

```
Phase 1: Internal testing (0.1%)
  - Deploy to internal track
  - Monitor Crashlytics for 24h

Phase 2: Beta (5%)
  - Deploy to beta track
  - Monitor for 48h
  - Check zmanim accuracy reports

Phase 3: Staged production
  - 10% -> 25% -> 50% -> 100%
  - Each stage: monitor 24h minimum
  - Halt criteria: crash rate > 0.5% or zmanim accuracy report
```

### Handling Breaking Changes

```
+-------------------------------------------+
|          Breaking Change Protocol           |
+-------------------------------------------+
| 1. Set admin_force_update_version in RC    |
| 2. Send FCM to affected version topics     |
| 3. App shows blocking update dialog        |
| 4. Grace period: 7 days with dismissible   |
|    banner, then blocking                    |
| 5. Old versions get "unsupported" screen   |
+-------------------------------------------+
```

```kotlin
class VersionChecker @Inject constructor(
    private val remoteConfig: RemoteConfigManager,
) {
    sealed class VersionStatus {
        data object Current : VersionStatus()
        data class UpdateAvailable(val url: String) : VersionStatus()
        data class ForceUpdate(val url: String, val message: String) : VersionStatus()
        data class Unsupported(val message: String) : VersionStatus()
    }

    fun checkVersion(currentVersionCode: Int): VersionStatus {
        val minSupported = remoteConfig.getLong("app_min_supported_version", 1)
        val forceVersion = remoteConfig.getLong("admin_force_update_version", 0)
        val latestVersion = remoteConfig.getLong("app_latest_version", currentVersionCode.toLong())
        val updateUrl = remoteConfig.getString("app_update_url", "")

        return when {
            currentVersionCode < minSupported ->
                VersionStatus.Unsupported("גרסה זו אינה נתמכת עוד. אנא עדכנו.")
            currentVersionCode < forceVersion ->
                VersionStatus.ForceUpdate(updateUrl, "עדכון חובה זמין")
            currentVersionCode < latestVersion ->
                VersionStatus.UpdateAvailable(updateUrl)
            else -> VersionStatus.Current
        }
    }
}
```

---

## Part 5: Code Implementation

### 1. RemoteConfigManager.kt

```kotlin
package com.zmanimclock.app.remote

import android.util.Log
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import com.zmanimclock.app.feature.zmanim.data.model.ZmanId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class RemoteConfigManager @Inject constructor() {

    private val remoteConfig: FirebaseRemoteConfig = Firebase.remoteConfig

    private val _lastFetchStatus = MutableStateFlow(FetchStatus.IDLE)
    val lastFetchStatus: StateFlow<FetchStatus> = _lastFetchStatus.asStateFlow()

    private val _changedKeys = MutableStateFlow<Set<String>>(emptySet())
    val changedKeys: StateFlow<Set<String>> = _changedKeys.asStateFlow()

    enum class FetchStatus { IDLE, FETCHING, SUCCESS, FAILED }

    /**
     * Initialize with defaults and fetch settings.
     * Call once during app startup.
     *
     * minimumFetchIntervalInSeconds:
     *   - Debug: 0 (no throttle)
     *   - Release: 3600 (1 hour)
     */
    fun initialize(isDebug: Boolean = false) {
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = if (isDebug) 0L else 3600L
        }
        remoteConfig.setConfigSettingsAsync(configSettings)
        remoteConfig.setDefaultsAsync(RemoteConfigDefaults.defaults)
    }

    /**
     * Fetch and activate remote config.
     * Returns true if new values were activated (something changed).
     */
    suspend fun fetchAndActivate(): Boolean = suspendCancellableCoroutine { cont ->
        _lastFetchStatus.value = FetchStatus.FETCHING

        // Snapshot current values to detect changes
        val beforeSnapshot = snapshotCurrentValues()

        remoteConfig.fetchAndActivate()
            .addOnSuccessListener { activated ->
                _lastFetchStatus.value = FetchStatus.SUCCESS

                if (activated) {
                    val afterSnapshot = snapshotCurrentValues()
                    val changed = findChangedKeys(beforeSnapshot, afterSnapshot)
                    _changedKeys.value = changed
                    Log.i(TAG, "Remote config activated. Changed keys: $changed")
                }

                cont.resume(activated)
            }
            .addOnFailureListener { exception ->
                _lastFetchStatus.value = FetchStatus.FAILED
                Log.w(TAG, "Remote config fetch failed", exception)
                cont.resume(false)
            }
    }

    // ---- Typed Getters ----

    fun getBoolean(key: String, default: Boolean = false): Boolean {
        return try {
            remoteConfig.getBoolean(key)
        } catch (e: Exception) {
            default
        }
    }

    fun getLong(key: String, default: Long = 0): Long {
        return try {
            remoteConfig.getLong(key)
        } catch (e: Exception) {
            default
        }
    }

    fun getDouble(key: String, default: Double = 0.0): Double {
        return try {
            remoteConfig.getDouble(key)
        } catch (e: Exception) {
            default
        }
    }

    fun getString(key: String, default: String = ""): String {
        return try {
            val value = remoteConfig.getString(key)
            value.ifEmpty { default }
        } catch (e: Exception) {
            default
        }
    }

    // ---- Zman-Specific Helpers ----

    /**
     * Check if a specific zman is enabled via remote config.
     * Falls back to the zman's defaultEnabled if no remote value.
     */
    fun isZmanEnabled(zmanId: ZmanId): Boolean {
        val key = "zman_${zmanId.name.lowercase()}_enabled"
        return getBoolean(key, zmanId.defaultEnabled)
    }

    /**
     * Get calculation override for a zman, if any.
     * Returns null if no override is set.
     */
    fun getCalcOverride(zmanId: ZmanId): CalcOverride? {
        val key = "calc_override_${zmanId.name}"
        val json = getString(key)
        if (json.isEmpty()) return null

        return try {
            Json.decodeFromString<CalcOverride>(json)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse calc override for $zmanId", e)
            null
        }
    }

    /**
     * Check if app is in maintenance mode.
     */
    fun isMaintenanceMode(): Boolean = getBoolean("admin_maintenance_mode", false)

    fun getMaintenanceMessage(): String = getString(
        "admin_maintenance_message",
        "האפליקציה בתחזוקה. נחזור בקרוב."
    )

    /**
     * Get admin banner if set.
     */
    fun getAdminBanner(): AdminBanner? {
        val message = getString("admin_banner_message")
        val type = getString("admin_banner_type", "none")
        if (message.isEmpty() || type == "none") return null

        return AdminBanner(
            message = message,
            type = AdminBannerType.valueOf(type.uppercase()),
            actionUrl = getString("admin_banner_url"),
        )
    }

    // ---- Internals ----

    private fun snapshotCurrentValues(): Map<String, String> {
        return remoteConfig.all.mapValues { it.value.asString() }
    }

    private fun findChangedKeys(
        before: Map<String, String>,
        after: Map<String, String>
    ): Set<String> {
        val changed = mutableSetOf<String>()
        for ((key, value) in after) {
            if (before[key] != value) {
                changed.add(key)
            }
        }
        return changed
    }

    companion object {
        private const val TAG = "RemoteConfigManager"
    }
}

// ---- Supporting Data Classes ----

@kotlinx.serialization.Serializable
data class CalcOverride(
    val method: String,         // "degrees", "minutes", "fixed_time"
    val value: Double,
    val fromVersion: Int = 0,   // apply to versions >= this
    val toVersion: Int = Int.MAX_VALUE, // apply to versions <= this
    val message: String = "",
)

data class AdminBanner(
    val message: String,
    val type: AdminBannerType,
    val actionUrl: String = "",
)

enum class AdminBannerType {
    NONE, INFO, WARNING, CRITICAL, UPDATE
}

object RemoteConfigDefaults {
    val defaults: Map<String, Any> = buildMap {
        // Zman enable flags - match ZmanId.defaultEnabled
        ZmanId.entries.forEach { zman ->
            put("zman_${zman.name.lowercase()}_enabled", zman.defaultEnabled)
        }

        // Calculation
        put("calc_candle_lighting_minutes_default", 18L)
        put("calc_candle_lighting_minutes_jerusalem", 40L)
        put("calc_use_elevation", false)
        put("calc_tzais_degrees_override", -1.0)

        // UI
        put("ui_show_seconds", false)
        put("ui_show_countdown_to_next", true)
        put("ui_dark_mode_default", "system")
        put("ui_show_parasha", true)
        put("ui_show_daf_yomi", true)
        put("ui_font_size_scale", 1.0)

        // Alerts
        put("alert_max_per_day", 30L)
        put("alert_vibrate_default", true)
        put("alert_sound_enabled", true)

        // Admin
        put("admin_banner_message", "")
        put("admin_banner_type", "none")
        put("admin_banner_url", "")
        put("admin_force_update_version", 0L)
        put("admin_recommended_version", 0L)
        put("admin_maintenance_mode", false)
        put("admin_maintenance_message", "")

        // App version control
        put("app_min_supported_version", 1L)
        put("app_latest_version", 1L)
        put("app_update_url", "")

        // Experiments
        put("exp_new_clock_ui", false)
        put("exp_analog_clock", false)
    }
}
```

### 2. FeatureFlags.kt

```kotlin
package com.zmanimclock.app.remote

import com.zmanimclock.app.feature.zmanim.data.model.ZmanId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central registry of all feature flags in the app.
 * Reads from RemoteConfigManager (Firebase Remote Config with local defaults).
 *
 * Usage:
 *   if (featureFlags.showSeconds) { ... }
 *   if (featureFlags.isZmanEnabled(ZmanId.TZAIS_8_5)) { ... }
 */
@Singleton
class FeatureFlags @Inject constructor(
    private val config: RemoteConfigManager,
) {
    // ================================================================
    // ZMAN VISIBILITY FLAGS
    // ================================================================

    /**
     * Check if a specific zman should be shown.
     * Remote Config can override the default from ZmanId.defaultEnabled.
     */
    fun isZmanEnabled(zmanId: ZmanId): Boolean = config.isZmanEnabled(zmanId)

    /**
     * Get all currently enabled zman IDs.
     */
    fun enabledZmanim(): Set<ZmanId> = ZmanId.entries.filter { isZmanEnabled(it) }.toSet()

    // ================================================================
    // UI FLAGS
    // ================================================================

    /** Show seconds in time display (HH:MM:SS vs HH:MM) */
    val showSeconds: Boolean get() = config.getBoolean("ui_show_seconds", false)

    /** Show countdown timer to the next upcoming zman */
    val showCountdownToNext: Boolean get() = config.getBoolean("ui_show_countdown_to_next", true)

    /** Default dark mode: "system", "dark", "light" */
    val darkModeDefault: String get() = config.getString("ui_dark_mode_default", "system")

    /** Show this week's parasha */
    val showParasha: Boolean get() = config.getBoolean("ui_show_parasha", true)

    /** Show Daf Yomi */
    val showDafYomi: Boolean get() = config.getBoolean("ui_show_daf_yomi", true)

    /** Font size scale factor (1.0 = normal) */
    val fontSizeScale: Double get() = config.getDouble("ui_font_size_scale", 1.0)

    // ================================================================
    // CALCULATION FLAGS
    // ================================================================

    /** Default candle lighting minutes before shkia */
    val candleLightingMinutes: Long
        get() = config.getLong("calc_candle_lighting_minutes_default", 18)

    /** Candle lighting minutes for Jerusalem */
    val candleLightingMinutesJerusalem: Long
        get() = config.getLong("calc_candle_lighting_minutes_jerusalem", 40)

    /** Use elevation-adjusted sunrise/sunset */
    val useElevation: Boolean get() = config.getBoolean("calc_use_elevation", false)

    // ================================================================
    // ALERT FLAGS
    // ================================================================

    /** Maximum alerts per day (safety limit) */
    val maxAlertsPerDay: Long get() = config.getLong("alert_max_per_day", 30)

    /** Default vibration for alerts */
    val alertVibrateDefault: Boolean get() = config.getBoolean("alert_vibrate_default", true)

    /** Enable alert sounds */
    val alertSoundEnabled: Boolean get() = config.getBoolean("alert_sound_enabled", true)

    // ================================================================
    // ADMIN FLAGS
    // ================================================================

    /** App is in maintenance mode - show maintenance screen */
    val isMaintenanceMode: Boolean get() = config.isMaintenanceMode()

    /** Maintenance mode message */
    val maintenanceMessage: String get() = config.getMaintenanceMessage()

    /** Admin banner to show at top of screen, or null */
    val adminBanner: AdminBanner? get() = config.getAdminBanner()

    /** Minimum version code that is still supported */
    val minSupportedVersion: Long
        get() = config.getLong("app_min_supported_version", 1)

    /** Version code that users should update to */
    val forceUpdateVersion: Long
        get() = config.getLong("admin_force_update_version", 0)

    // ================================================================
    // EXPERIMENT FLAGS
    // ================================================================

    /** New clock UI experiment */
    val expNewClockUi: Boolean get() = config.getBoolean("exp_new_clock_ui", false)

    /** Analog clock experiment */
    val expAnalogClock: Boolean get() = config.getBoolean("exp_analog_clock", false)

    // ================================================================
    // OBSERVING CHANGES
    // ================================================================

    /**
     * Observe when any feature flag changes after a Remote Config fetch.
     * Useful for triggering UI recomposition or recalculation.
     */
    fun observeChanges(): Flow<Set<String>> = config.changedKeys

    /**
     * Observe when zman-related flags change (to trigger recalculation).
     */
    fun observeZmanChanges(): Flow<Set<String>> = config.changedKeys.map { keys ->
        keys.filter { it.startsWith("zman_") || it.startsWith("calc_") }.toSet()
    }
}
```

### 3. AdminMessageHandler.kt

```kotlin
package com.zmanimclock.app.remote

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.zmanimclock.app.R
import com.zmanimclock.app.ZmanimApp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// ================================================================
// FCM Service - receives push messages from Firebase
// ================================================================

@AndroidEntryPoint
class ZmanimFcmService : FirebaseMessagingService() {

    @Inject lateinit var messageHandler: AdminMessageHandler
    @Inject lateinit var topicManager: FcmTopicManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "FCM message received: ${message.data}")

        serviceScope.launch {
            messageHandler.handleMessage(this@ZmanimFcmService, message.data)
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM token refreshed")

        serviceScope.launch {
            topicManager.resubscribeAll()
        }
    }

    companion object {
        private const val TAG = "ZmanimFcmService"
    }
}

// ================================================================
// AdminMessageHandler - processes and routes FCM messages
// ================================================================

@Singleton
class AdminMessageHandler @Inject constructor(
    private val remoteConfigManager: RemoteConfigManager,
) {
    private val _messages = MutableSharedFlow<AdminMessage>(replay = 1)
    val messages: SharedFlow<AdminMessage> = _messages.asSharedFlow()

    /**
     * Process an incoming FCM data message.
     * Routes to appropriate handler based on message type.
     */
    suspend fun handleMessage(context: Context, data: Map<String, String>) {
        val type = try {
            AdminMessageType.valueOf(data["type"] ?: "GENERAL")
        } catch (e: IllegalArgumentException) {
            AdminMessageType.GENERAL
        }

        val message = AdminMessage(
            id = data["message_id"] ?: UUID.randomUUID().toString(),
            type = type,
            title = data["title"] ?: "",
            body = data["body"] ?: "",
            action = data["action"] ?: "SHOW",
            priority = data["priority"] ?: "normal",
            persist = data["persist"]?.toBooleanStrictOrNull() ?: false,
            extras = data.filterKeys { it !in KNOWN_KEYS },
            timestamp = System.currentTimeMillis(),
        )

        // Emit to in-app observers
        _messages.emit(message)

        // Route based on type
        when (type) {
            AdminMessageType.BUG_FIX -> handleBugFix(context, message, data)
            AdminMessageType.UPDATE_AVAILABLE -> handleUpdateAvailable(context, message, data)
            AdminMessageType.RABBINICAL -> handleRabbinical(context, message)
            AdminMessageType.DST_CHANGE -> handleDstChange(context, message)
            AdminMessageType.MAINTENANCE -> handleMaintenance(context, message)
            AdminMessageType.GENERAL -> handleGeneral(context, message)
        }
    }

    // ---- Type-Specific Handlers ----

    private fun handleBugFix(context: Context, message: AdminMessage, data: Map<String, String>) {
        val affectedZman = data["affected_zman"]
        val fixedInVersion = data["fixed_in_version"]?.toIntOrNull()

        showNotification(
            context = context,
            channelId = ZmanimApp.CHANNEL_REMINDER,
            title = message.title,
            body = message.body,
            notificationId = NOTIF_ID_BUG_FIX,
        )

        Log.i(TAG, "Bug fix notification: zman=$affectedZman, fixedIn=$fixedInVersion")
    }

    private fun handleUpdateAvailable(
        context: Context,
        message: AdminMessage,
        data: Map<String, String>
    ) {
        val action = data["action"] ?: "SHOW"
        val updateUrl = data["update_url"] ?: ""

        showNotification(
            context = context,
            channelId = if (action == "FORCE_UPDATE") {
                ZmanimApp.CHANNEL_ALARM
            } else {
                ZmanimApp.CHANNEL_REMINDER
            },
            title = message.title,
            body = message.body,
            notificationId = NOTIF_ID_UPDATE,
        )
    }

    private fun handleRabbinical(context: Context, message: AdminMessage) {
        showNotification(
            context = context,
            channelId = ZmanimApp.CHANNEL_REMINDER,
            title = message.title,
            body = message.body,
            notificationId = NOTIF_ID_RABBINICAL,
        )
    }

    private suspend fun handleDstChange(context: Context, message: AdminMessage) {
        // Trigger zmanim recalculation
        if (message.action == "RECALCULATE") {
            remoteConfigManager.fetchAndActivate()
        }

        showNotification(
            context = context,
            channelId = ZmanimApp.CHANNEL_ALARM,
            title = message.title,
            body = message.body,
            notificationId = NOTIF_ID_DST,
        )
    }

    private suspend fun handleMaintenance(context: Context, message: AdminMessage) {
        // Force refresh Remote Config to get maintenance mode flag
        remoteConfigManager.fetchAndActivate()
    }

    private fun handleGeneral(context: Context, message: AdminMessage) {
        showNotification(
            context = context,
            channelId = ZmanimApp.CHANNEL_REMINDER,
            title = message.title,
            body = message.body,
            notificationId = NOTIF_ID_GENERAL,
        )
    }

    // ---- Notification Helper ----

    private fun showNotification(
        context: Context,
        channelId: String,
        title: String,
        body: String,
        notificationId: Int,
    ) {
        val manager = context.getSystemService<NotificationManager>() ?: return

        // Launch app on tap
        val intent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP }

        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(notificationId, notification)
    }

    companion object {
        private const val TAG = "AdminMessageHandler"

        private const val NOTIF_ID_BUG_FIX = 9001
        private const val NOTIF_ID_UPDATE = 9002
        private const val NOTIF_ID_RABBINICAL = 9003
        private const val NOTIF_ID_DST = 9004
        private const val NOTIF_ID_GENERAL = 9005

        private val KNOWN_KEYS = setOf(
            "type", "title", "body", "action", "priority",
            "persist", "message_id",
        )
    }
}

// ================================================================
// Data Models
// ================================================================

enum class AdminMessageType {
    BUG_FIX,
    UPDATE_AVAILABLE,
    RABBINICAL,
    DST_CHANGE,
    MAINTENANCE,
    GENERAL,
}

data class AdminMessage(
    val id: String,
    val type: AdminMessageType,
    val title: String,
    val body: String,
    val action: String,
    val priority: String,
    val persist: Boolean,
    val extras: Map<String, String>,
    val timestamp: Long,
)

// ================================================================
// FCM Topic Manager - manages topic subscriptions
// ================================================================

@Singleton
class FcmTopicManager @Inject constructor() {

    private val messaging = FirebaseMessaging.getInstance()

    /**
     * Subscribe to appropriate topics based on user settings.
     * Call during onboarding and when settings change.
     */
    fun subscribe(
        cityId: String,
        nusach: String,     // "sephardi" or "ashkenazi"
        versionCode: Int,
        isBetaTester: Boolean = false,
    ) {
        // Everyone gets "all"
        messaging.subscribeToTopic("all")

        // City-specific
        if (cityId.isNotEmpty()) {
            messaging.subscribeToTopic("city_$cityId")
        }

        // Nusach-specific
        messaging.subscribeToTopic("nusach_$nusach")

        // Version-specific
        messaging.subscribeToTopic("version_$versionCode")

        // Beta
        if (isBetaTester) {
            messaging.subscribeToTopic("beta")
        }

        Log.i(TAG, "Subscribed to topics: all, city_$cityId, nusach_$nusach, version_$versionCode")
    }

    /**
     * Unsubscribe from a city topic (when user changes city).
     */
    fun unsubscribeCity(oldCityId: String) {
        messaging.unsubscribeFromTopic("city_$oldCityId")
    }

    /**
     * Re-subscribe to all topics after token refresh.
     * Reads current settings to determine topics.
     */
    suspend fun resubscribeAll() {
        // In a real implementation, read settings from DataStore
        // and call subscribe() with current values.
        messaging.subscribeToTopic("all")
    }

    companion object {
        private const val TAG = "FcmTopicManager"
    }
}
```

### 4. AppInitializer.kt

```kotlin
package com.zmanimclock.app.startup

import android.content.Context
import android.os.Trace
import android.util.Log
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.startup.Initializer
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.zmanimclock.app.remote.FeatureFlags
import com.zmanimclock.app.remote.FcmTopicManager
import com.zmanimclock.app.remote.RemoteConfigManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AppInitializer orchestrates the startup sequence.
 *
 * STARTUP PHASES:
 *
 * Phase 1 - BLOCKING (before first frame):
 *   - Read cached zmanim from DataStore
 *   - Read cached feature flags
 *   - Initialize notification channels (in Application.onCreate)
 *
 * Phase 2 - NON-BLOCKING (after first frame, in coroutine):
 *   - Check if zmanim cache is stale (different day)
 *   - If stale, recalculate and emit new values
 *   - Fetch Remote Config
 *   - Register FCM topics
 *   - Schedule WorkManager tasks
 *
 * Phase 3 - LAZY (on-demand):
 *   - Location refresh
 *   - Hebrew calendar data
 *   - Crashlytics deferred init
 */
@Singleton
class AppInitializer @Inject constructor(
    private val remoteConfigManager: RemoteConfigManager,
    private val featureFlags: FeatureFlags,
    private val fcmTopicManager: FcmTopicManager,
    private val zmanimCache: ZmanimCacheManager,
    private val zmanimRecalculator: ZmanimRecalculator,
) {
    private val initScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var isInitialized = false

    /**
     * Phase 1: Synchronous initialization.
     * Called from Application.onCreate(). Must be fast (< 50ms).
     */
    fun initializeSync(context: Context) {
        Trace.beginSection("AppInit.sync")
        try {
            // Initialize Remote Config with local defaults (no network)
            remoteConfigManager.initialize(isDebug = isDebugBuild(context))

            Log.i(TAG, "Phase 1 complete: sync init done")
        } finally {
            Trace.endSection()
        }
    }

    /**
     * Phase 2: Async initialization.
     * Called from Application.onCreate() but runs in a coroutine.
     * Does NOT block the main thread.
     */
    fun initializeAsync(context: Context) {
        if (isInitialized) return
        isInitialized = true

        initScope.launch {
            Trace.beginSection("AppInit.async")
            try {
                // 2a. Check if zmanim need recalculation
                val cacheDate = zmanimCache.getCachedDate()
                val today = LocalDate.now()

                if (cacheDate != today) {
                    Log.i(TAG, "Zmanim cache stale (cached=$cacheDate, today=$today). Recalculating...")
                    zmanimRecalculator.recalculateToday()
                } else {
                    Log.i(TAG, "Zmanim cache is fresh for today")
                }

                // 2b. Fetch Remote Config (network call)
                val configChanged = remoteConfigManager.fetchAndActivate()
                if (configChanged) {
                    Log.i(TAG, "Remote config changed, checking if recalculation needed")
                    val changedKeys = remoteConfigManager.changedKeys.first()
                    if (changedKeys.any { it.startsWith("calc_") || it.startsWith("zman_") }) {
                        zmanimRecalculator.recalculateToday()
                    }
                }

                // 2c. Schedule background workers
                scheduleWorkers(context)

                // 2d. FCM topic subscription (fire-and-forget)
                fcmTopicManager.subscribe(
                    cityId = zmanimCache.getCurrentCityId(),
                    nusach = zmanimCache.getCurrentNusach(),
                    versionCode = getVersionCode(context),
                )

                Log.i(TAG, "Phase 2 complete: async init done")
            } catch (e: Exception) {
                Log.e(TAG, "Async init failed (non-fatal)", e)
            } finally {
                Trace.endSection()
            }
        }
    }

    // ================================================================
    // WorkManager Scheduling
    // ================================================================

    private fun scheduleWorkers(context: Context) {
        val workManager = WorkManager.getInstance(context)

        // ---- Midnight Recalculation ----
        // Calculate delay until next midnight
        val now = LocalDateTime.now()
        val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay()
        val delayMillis = Duration.between(now, nextMidnight).toMillis()

        val midnightWork = OneTimeWorkRequestBuilder<MidnightRecalcWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .addTag(WORK_TAG_MIDNIGHT)
            .build()

        workManager.enqueueUniqueWork(
            WORK_NAME_MIDNIGHT,
            ExistingWorkPolicy.REPLACE,
            midnightWork,
        )

        // ---- Periodic Config Sync (every 6 hours) ----
        val configSync = PeriodicWorkRequestBuilder<ConfigSyncWorker>(
            6, TimeUnit.HOURS,
            30, TimeUnit.MINUTES, // flex window
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag(WORK_TAG_CONFIG_SYNC)
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_NAME_CONFIG_SYNC,
            ExistingPeriodicWorkPolicy.KEEP,
            configSync,
        )

        // ---- Periodic Location Refresh (every 12 hours, GPS mode only) ----
        if (zmanimCache.isGpsMode()) {
            val locationRefresh = PeriodicWorkRequestBuilder<LocationRefreshWorker>(
                12, TimeUnit.HOURS,
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .addTag(WORK_TAG_LOCATION)
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME_LOCATION,
                ExistingPeriodicWorkPolicy.KEEP,
                locationRefresh,
            )
        }

        Log.i(TAG, "Workers scheduled. Next midnight recalc in ${delayMillis / 1000}s")
    }

    // ================================================================
    // Utilities
    // ================================================================

    private fun isDebugBuild(context: Context): Boolean {
        return context.applicationInfo.flags and
            android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
    }

    private fun getVersionCode(context: Context): Int {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            info.longVersionCode.toInt()
        } catch (e: Exception) {
            1
        }
    }

    companion object {
        private const val TAG = "AppInitializer"

        const val WORK_NAME_MIDNIGHT = "midnight_recalc"
        const val WORK_NAME_CONFIG_SYNC = "config_sync"
        const val WORK_NAME_LOCATION = "location_refresh"

        const val WORK_TAG_MIDNIGHT = "tag_midnight"
        const val WORK_TAG_CONFIG_SYNC = "tag_config_sync"
        const val WORK_TAG_LOCATION = "tag_location"
    }
}

// ================================================================
// Supporting interfaces (implementations elsewhere)
// ================================================================

/**
 * Manages the zmanim cache in DataStore.
 * Provides fast access to cached values during startup.
 */
interface ZmanimCacheManager {
    /** Get the date for which zmanim are currently cached */
    suspend fun getCachedDate(): LocalDate?

    /** Get current city ID from settings */
    fun getCurrentCityId(): String

    /** Get current nusach from settings */
    fun getCurrentNusach(): String

    /** Whether the user is using GPS-based location */
    fun isGpsMode(): Boolean
}

/**
 * Handles zmanim recalculation.
 * Calculates all enabled zmanim and updates cache + UI state.
 */
interface ZmanimRecalculator {
    /** Recalculate all zmanim for today with current location */
    suspend fun recalculateToday()

    /** Recalculate and reschedule all alerts */
    suspend fun recalculateAndRescheduleAlerts()
}

// ================================================================
// Worker Stubs (full implementations in :scheduling package)
// ================================================================

/**
 * Runs at midnight to recalculate zmanim for the new day.
 * Also reschedules itself for the next midnight.
 */
// class MidnightRecalcWorker - implemented in scheduling package

/**
 * Periodically syncs Firebase Remote Config.
 */
// class ConfigSyncWorker - implemented in scheduling package

/**
 * Periodically refreshes GPS location.
 */
// class LocationRefreshWorker - implemented in scheduling package

// ================================================================
// Updated Application class using AppInitializer
// ================================================================

/*
@HiltAndroidApp
class ZmanimApp : Application() {

    @Inject lateinit var appInitializer: AppInitializer

    override fun onCreate() {
        super.onCreate()

        // Phase 1: Sync (fast, blocking)
        createNotificationChannels()
        appInitializer.initializeSync(this)

        // Phase 2: Async (non-blocking)
        appInitializer.initializeAsync(this)
    }

    // ... notification channels same as before
}
*/
```

---

## Appendix A: Gradle Dependencies to Add

```kotlin
// In app/build.gradle.kts, add to dependencies block:

// Firebase BoM
implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
implementation("com.google.firebase:firebase-config-ktx")
implementation("com.google.firebase:firebase-messaging-ktx")
implementation("com.google.firebase:firebase-crashlytics-ktx")
implementation("com.google.firebase:firebase-analytics-ktx")

// AndroidX Startup (for Initializer)
implementation("androidx.startup:startup-runtime:1.2.0")

// Kotlinx Serialization (for CalcOverride JSON parsing)
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
```

Also add to `settings.gradle.kts` or project-level `build.gradle.kts`:
```kotlin
plugins {
    id("com.google.gms.google-services") version "4.4.2" apply false
    id("com.google.firebase.crashlytics") version "3.0.2" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.0" apply false
}
```

And in app-level:
```kotlin
plugins {
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    id("org.jetbrains.kotlin.plugin.serialization")
}
```

## Appendix B: AndroidManifest Additions

```xml
<!-- FCM Service -->
<service
    android:name=".remote.ZmanimFcmService"
    android:exported="false">
    <intent-filter>
        <action android:name="com.google.firebase.MESSAGING_EVENT" />
    </intent-filter>
</service>

<!-- Boot receiver for rescheduling workers -->
<receiver
    android:name=".scheduling.BootReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
    </intent-filter>
</receiver>

<!-- Admin notification channel (add to existing channels) -->
<!-- Handled programmatically in ZmanimApp.kt -->
```

## Appendix C: File Placement Map

```
app/src/main/java/com/zmanimclock/app/
  |
  +-- ZmanimApp.kt                              (updated with AppInitializer)
  |
  +-- remote/
  |     +-- RemoteConfigManager.kt               (Firebase Remote Config wrapper)
  |     +-- FeatureFlags.kt                       (all feature flag definitions)
  |     +-- AdminMessageHandler.kt                (FCM message processing)
  |     +-- FcmTopicManager.kt                    (topic subscription management)
  |     +-- CrashlyticsManager.kt                 (crash reporting helper)
  |     +-- RemoteConfigDefaults.kt               (default config values)
  |     +-- VersionChecker.kt                     (force update logic)
  |     +-- model/
  |           +-- AdminMessage.kt
  |           +-- AdminBanner.kt
  |           +-- CalcOverride.kt
  |
  +-- startup/
  |     +-- AppInitializer.kt                     (smart startup orchestrator)
  |     +-- ZmanimCacheManager.kt                 (cache interface)
  |     +-- ZmanimRecalculator.kt                 (recalculation interface)
  |
  +-- scheduling/
  |     +-- MidnightRecalcWorker.kt               (midnight zmanim recalc)
  |     +-- ConfigSyncWorker.kt                   (periodic Remote Config sync)
  |     +-- LocationRefreshWorker.kt              (periodic GPS refresh)
  |     +-- BootReceiver.kt                       (reschedule on boot)
  |
  +-- di/
  |     +-- AppModule.kt                          (Hilt module - singletons)
  |     +-- RepositoryModule.kt                   (Hilt module - bindings)
  |     +-- ZmanPluginModule.kt                   (Hilt module - zman plugins)
  |
  +-- feature/
        +-- zmanim/
        |     +-- domain/
        |     |     +-- ZmanimRepository.kt        (interface)
        |     |     +-- ZmanCalculator.kt           (interface)
        |     |     +-- ZmanPlugin.kt               (plugin interface)
        |     |     +-- ZmanPluginRegistry.kt
        |     +-- data/
        |           +-- ZmanimRepositoryImpl.kt
        |           +-- ZmanCalculatorImpl.kt
        |           +-- model/
        |                 +-- ZmanDefinition.kt     (existing, unchanged)
        |                 +-- HalachicProfile.kt
        +-- alerts/
        +-- calendar/
        +-- settings/
        +-- widget/
        +-- onboarding/
```
