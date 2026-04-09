# Location System - Technical Specification
## Halachic Zmanim App

---

## Table of Contents
1. [Overview](#1-overview)
2. [GPS Location](#2-gps-location)
3. [Manual City Selection](#3-manual-city-selection)
4. [Manual Coordinates Entry](#4-manual-coordinates-entry)
5. [How Location Affects Zmanim](#5-how-location-affects-zmanim)
6. [Elevation Data Strategy](#6-elevation-data-strategy)
7. [Edge Cases](#7-edge-cases)
8. [Data Model (Kotlin)](#8-data-model-kotlin)
9. [Storage Layer](#9-storage-layer)
10. [UI Flow](#10-ui-flow)
11. [Integration with ZmanimCalculator](#11-integration-with-zmanimcalculator)
12. [City Database](#12-city-database)

---

## 1. Overview

The location system determines where the user is so that sunrise, sunset, and all derivative zmanim are calculated correctly. Location affects zmanim through four parameters:

| Parameter | Effect | Required Accuracy |
|-----------|--------|-------------------|
| Latitude | Day length (shaah zmanit) | ~1 km (city-level) |
| Longitude | Solar noon timing | ~1 km |
| Elevation | Sunrise/sunset shift | ~50 m for meaningful impact |
| Timezone | Local clock mapping | Must be exact (IANA ID) |

Three input methods are supported: GPS auto-detection, manual city selection from a built-in database, and manual coordinate entry.

---

## 2. GPS Location

### 2.1 Android API: FusedLocationProviderClient

Use Google Play Services' `FusedLocationProviderClient`, which fuses GPS, Wi-Fi, and cell tower signals for the best accuracy-to-battery tradeoff.

**Dependency:**
```kotlin
implementation("com.google.android.gms:play-services-location:21.3.0")
```

### 2.2 Permissions

| Permission | Level | Accuracy | Use Case |
|-----------|-------|----------|----------|
| `ACCESS_COARSE_LOCATION` | Normal-ish | ~1-3 km (Wi-Fi/cell) | Sufficient for zmanim |
| `ACCESS_FINE_LOCATION` | Dangerous | ~5-10 m (GPS) | Better elevation, not required |
| `ACCESS_BACKGROUND_LOCATION` | Dangerous | Same | Only if updating while app is closed |

**Recommendation:** Request `ACCESS_FINE_LOCATION` because:
- Coarse location is city-level accurate (~1-3 km) which is technically sufficient for latitude/longitude.
- However, `ACCESS_FINE_LOCATION` also enables GPS-based elevation readings which, while imperfect, are better than nothing.
- Android 12+ requires requesting `ACCESS_FINE_LOCATION` to later downgrade to `ACCESS_COARSE_LOCATION`; the reverse is not possible.
- Do NOT request `ACCESS_BACKGROUND_LOCATION`. The app only needs location on open or once daily via WorkManager.

### 2.3 Getting Location - Code Pattern

```kotlin
class LocationProvider(
    private val context: Context,
    private val fusedClient: FusedLocationProviderClient
) {
    /**
     * Get current location once (not continuous tracking).
     * Uses getCurrentLocation() which is preferred over getLastLocation()
     * because getLastLocation() can return null or stale data.
     */
    suspend fun getCurrentLocation(): LocationResult {
        // Check permission first
        if (!hasLocationPermission()) {
            return LocationResult.PermissionDenied
        }

        return try {
            val location = fusedClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                CancellationTokenSource().token
            ).await()

            if (location != null) {
                LocationResult.Success(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    elevation = if (location.hasAltitude()) location.altitude else null,
                    accuracy = location.accuracy
                )
            } else {
                LocationResult.Unavailable
            }
        } catch (e: SecurityException) {
            LocationResult.PermissionDenied
        } catch (e: Exception) {
            LocationResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Battery-efficient: use PRIORITY_BALANCED_POWER_ACCURACY
     * This uses Wi-Fi + cell towers (~100m accuracy) with 40-50% less
     * battery drain than HIGH_ACCURACY. Since zmanim only need ~1km
     * accuracy for lat/lon, this is ideal.
     */
    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
}

sealed class LocationResult {
    data class Success(
        val latitude: Double,
        val longitude: Double,
        val elevation: Double?,
        val accuracy: Float
    ) : LocationResult()
    object PermissionDenied : LocationResult()
    object Unavailable : LocationResult()
    data class Error(val message: String) : LocationResult()
}
```

### 2.4 Accuracy Needed for Zmanim

- **Latitude:** 1 km error = ~0.5 second error in sunrise/sunset. City-level is fine.
- **Longitude:** 1 km error = ~4 seconds error in solar noon. City-level is fine.
- **Elevation:** 100 m error = ~1 minute error in sunrise/sunset. This matters and is addressed in Section 6.
- **Conclusion:** `PRIORITY_BALANCED_POWER_ACCURACY` (~100m) is perfect for lat/lon. Elevation needs supplementation.

### 2.5 Elevation from GPS

Phone GPS altitude comes from the satellite signal and represents height above the WGS84 ellipsoid, NOT above sea level. The difference (geoid undulation) can be 30-50 meters in Israel. Additionally, GPS altitude has typical error of +/- 20-50m.

**Strategy:**
1. Read `location.altitude` if `location.hasAltitude()` is true.
2. Cross-reference against the bundled city database (Section 6). If the user is within 5 km of a known city, prefer the database elevation.
3. Optionally query an elevation API for precise data.

### 2.6 Fallback Strategy When GPS Unavailable

```
GPS Request
    |
    +-- Success --> Use coordinates + elevation logic
    |
    +-- Permission Denied --> Show manual city picker
    |
    +-- Location Unavailable (airplane mode, etc.) --> Use last saved location
    |
    +-- No last saved location --> Force manual city selection
```

- Always persist the last known location to DataStore.
- On app open, try GPS. If it fails, silently fall back to saved location.
- If no saved location exists (first launch), the onboarding flow forces city selection.

### 2.7 Battery Strategy

| Scenario | Method | Frequency |
|----------|--------|-----------|
| App opened (foreground) | `getCurrentLocation()` once | Each app open, max once per 30 min |
| Daily recalculation | WorkManager periodic task | Once at 00:00 local time |
| User travels | Detect via geofencing OR manual "refresh" button | On demand |

- No continuous location tracking.
- No background location permission needed.
- WorkManager task uses `PRIORITY_BALANCED_POWER_ACCURACY`.
- Throttle: if last GPS fix is < 30 minutes old and user hasn't moved > 5 km, skip re-fetch.

---

## 3. Manual City Selection

### 3.1 Design

The city picker is a searchable list with sections:

```
[Search field: "חפש עיר..."]

--- ישראל ---
ירושלים
תל אביב - יפו
חיפה
באר שבע
...

--- ערים בעולם ---
New York, USA
London, UK
Paris, France
...
```

- Search works on both Hebrew and English names.
- Most recently used city appears at the top.
- GPS-detected city highlighted with a location pin icon.
- Tapping a city immediately updates all zmanim.

### 3.2 City Database - Israel (25+ cities)

| # | Hebrew Name | English Name | Latitude | Longitude | Elevation (m) |
|---|------------|--------------|----------|-----------|----------------|
| 1 | ירושלים | Jerusalem | 31.7683 | 35.2137 | 754 |
| 2 | תל אביב - יפו | Tel Aviv | 32.0853 | 34.7818 | 5 |
| 3 | חיפה | Haifa | 32.7940 | 34.9896 | 40 |
| 4 | באר שבע | Beer Sheva | 31.2530 | 34.7915 | 285 |
| 5 | אילת | Eilat | 29.5577 | 34.9519 | 15 |
| 6 | צפת | Tzfat (Safed) | 32.9646 | 35.4960 | 834 |
| 7 | טבריה | Tiberias | 32.7922 | 35.5312 | -200 |
| 8 | נתניה | Netanya | 32.3215 | 34.8532 | 32 |
| 9 | אשדוד | Ashdod | 31.8044 | 34.6553 | 26 |
| 10 | פתח תקווה | Petah Tikva | 32.0841 | 34.8878 | 50 |
| 11 | בני ברק | Bnei Brak | 32.0834 | 34.8332 | 30 |
| 12 | רמת גן | Ramat Gan | 32.0700 | 34.8243 | 40 |
| 13 | הרצליה | Herzliya | 32.1629 | 34.7915 | 20 |
| 14 | כפר סבא | Kfar Saba | 32.1751 | 34.9066 | 50 |
| 15 | רעננה | Ra'anana | 32.1837 | 34.8708 | 45 |
| 16 | מודיעין-מכבים-רעות | Modi'in | 31.8939 | 35.0104 | 280 |
| 17 | אשקלון | Ashkelon | 31.6688 | 34.5743 | 35 |
| 18 | עפולה | Afula | 32.6071 | 35.2886 | 60 |
| 19 | קריית שמונה | Kiryat Shmona | 33.2072 | 35.5713 | 210 |
| 20 | נהריה | Nahariya | 33.0056 | 35.0953 | 10 |
| 21 | עכו | Akko (Acre) | 32.9215 | 35.0764 | 8 |
| 22 | מעלה אדומים | Ma'ale Adumim | 31.7781 | 35.3001 | 480 |
| 23 | אריאל | Ariel | 32.1065 | 35.1734 | 540 |
| 24 | גוש עציון (אפרת) | Gush Etzion (Efrat) | 31.6581 | 35.1550 | 900 |
| 25 | קרני שומרון | Karnei Shomron | 32.1710 | 35.0966 | 380 |
| 26 | ראשון לציון | Rishon LeZion | 31.9642 | 34.8044 | 30 |
| 27 | חולון | Holon | 32.0116 | 34.7831 | 15 |
| 28 | בית שמש | Beit Shemesh | 31.7510 | 34.9884 | 320 |
| 29 | רחובות | Rehovot | 31.8928 | 34.8113 | 40 |
| 30 | ים המלח (עין גדי) | Dead Sea (Ein Gedi) | 31.4504 | 35.3868 | -390 |

**Notes on special elevations:**
- Tzfat (834m) and Gush Etzion (900m) are among the highest inhabited locations. Sunrise is noticeably earlier here.
- Tiberias (-200m) and Dead Sea (-390m) are below sea level. At the Dead Sea, sunrise is later and sunset earlier compared to sea level due to surrounding cliffs and negative elevation.
- KosherJava's GeoLocation does not accept negative elevation values; for below-sea-level locations, set elevation to 0 and note that sea-level sunrise/sunset is the correct halachic baseline for these locations.

### 3.3 City Database - Worldwide Jewish Communities (15+ cities)

| # | City | Country | Latitude | Longitude | Elevation (m) | Timezone |
|---|------|---------|----------|-----------|----------------|----------|
| 1 | New York | USA | 40.7128 | -74.0060 | 10 | America/New_York |
| 2 | Los Angeles | USA | 34.0522 | -118.2437 | 71 | America/Los_Angeles |
| 3 | Chicago | USA | 41.8781 | -87.6298 | 181 | America/Chicago |
| 4 | Miami | USA | 25.7617 | -80.1918 | 2 | America/New_York |
| 5 | Boston | USA | 42.3601 | -71.0589 | 14 | America/New_York |
| 6 | London | UK | 51.5074 | -0.1278 | 11 | Europe/London |
| 7 | Manchester | UK | 53.4808 | -2.2426 | 38 | Europe/London |
| 8 | Paris | France | 48.8566 | 2.3522 | 35 | Europe/Paris |
| 9 | Toronto | Canada | 43.6532 | -79.3832 | 76 | America/Toronto |
| 10 | Montreal | Canada | 45.5017 | -73.5673 | 36 | America/Toronto |
| 11 | Melbourne | Australia | -37.8136 | 144.9631 | 31 | Australia/Melbourne |
| 12 | Sydney | Australia | -33.8688 | 151.2093 | 3 | Australia/Sydney |
| 13 | Buenos Aires | Argentina | -34.6037 | -58.3816 | 25 | America/Argentina/Buenos_Aires |
| 14 | Johannesburg | South Africa | -26.2041 | 28.0473 | 1753 | Africa/Johannesburg |
| 15 | Mexico City | Mexico | 19.4326 | -99.1332 | 2240 | America/Mexico_City |
| 16 | Sao Paulo | Brazil | -23.5505 | -46.6333 | 760 | America/Sao_Paulo |
| 17 | Antwerp | Belgium | 51.2194 | 4.4025 | 7 | Europe/Brussels |
| 18 | Berlin | Germany | 52.5200 | 13.4050 | 34 | Europe/Berlin |

**All Israel cities use timezone `Asia/Jerusalem`.**

---

## 4. Manual Coordinates Entry

### 4.1 Input UI

For advanced users (e.g., kibbutzim, moshavim, or isolated locations not in the database):

```
+------------------------------------------+
| הזנת מיקום ידני                           |
|                                          |
| קו רוחב (Latitude):  [ 31.7683     ]    |
| קו אורך (Longitude): [ 35.2137     ]    |
| גובה מעל פני הים:     [ 754   ] מטרים    |
|                                          |
| אזור זמן: [Asia/Jerusalem     v]        |
|   (זוהה אוטומטית)                        |
|                                          |
| [שמור מיקום]                              |
+------------------------------------------+
```

### 4.2 Validation Rules

```kotlin
fun validateCoordinates(lat: Double, lon: Double, elevation: Double): ValidationResult {
    return when {
        lat < -90.0 || lat > 90.0 ->
            ValidationResult.Error("Latitude must be between -90 and 90")
        lon < -180.0 || lon > 180.0 ->
            ValidationResult.Error("Longitude must be between -180 and 180")
        elevation < -500.0 || elevation > 9000.0 ->
            ValidationResult.Error("Elevation must be between -500 and 9000 meters")
        // Warn about extreme latitudes where zmanim become problematic
        lat > 65.0 || lat < -65.0 ->
            ValidationResult.Warning("Extreme latitude: some zmanim may not be calculable")
        else -> ValidationResult.Valid
    }
}
```

### 4.3 Timezone Auto-Detection

Given coordinates, determine timezone without an API call:

**Option A (recommended for offline):** Bundle the `TimeZoneMap` library:
```kotlin
implementation("us.dustinj.timezonemap:timezonemap:4.5")
```
This embeds a ~5 MB shapefile. At runtime:
```kotlin
val tzMap = TimeZoneMap.forRegion(minLat, minLon, maxLat, maxLon)
val tzId = tzMap.getOverlappingTimeZone(lat, lon)?.zoneId
    ?: TimeZone.getDefault().id
```

**Option B (online, smaller APK):** Use Google Time Zone API:
```
GET https://maps.googleapis.com/maps/api/timezone/json
    ?location=31.7683,35.2137
    &timestamp=1609459200
    &key=API_KEY
```

**Recommendation:** Use Option A for Israel (the only region where offline is critical--Shabbat usage). For worldwide, fall back to Option B or let users pick from a dropdown of IANA timezone IDs grouped by region.

---

## 5. How Location Affects Zmanim Calculations

### 5.1 KosherJava's GeoLocation Class

The `GeoLocation` class encapsulates all location data needed for astronomical calculations:

```java
GeoLocation geoLocation = new GeoLocation(
    "Jerusalem",                              // location name (display only)
    31.7683,                                  // latitude (decimal degrees, N positive)
    35.2137,                                  // longitude (decimal degrees, E positive)
    754,                                      // elevation (meters above sea level, >= 0)
    TimeZone.getTimeZone("Asia/Jerusalem")    // timezone
);
```

This `GeoLocation` is then passed to:
```java
ComplexZmanimCalendar czc = new ComplexZmanimCalendar(geoLocation);
czc.getCalendar().set(2026, Calendar.APRIL, 9);
czc.setUseElevation(true); // Enable elevation-adjusted sunrise/sunset

Date sunrise = czc.getSunrise();          // elevation-adjusted
Date seaLevelSunrise = czc.getSeaLevelSunrise(); // always sea level
```

### 5.2 How Latitude Affects Zmanim

Latitude determines the length of daylight and thus the length of a **shaah zmanit** (proportional hour).

| Latitude | Example | Summer Day Length | Winter Day Length | Impact |
|----------|---------|-------------------|-------------------|--------|
| 29.5 (Eilat) | Southernmost Israel | ~14h 05m | ~10h 15m | Smallest seasonal variation |
| 31.8 (Jerusalem) | Central Israel | ~14h 15m | ~10h 05m | Moderate |
| 33.2 (Kiryat Shmona) | Northernmost Israel | ~14h 30m | ~9h 50m | Largest variation in Israel |
| 40.7 (New York) | US East Coast | ~15h 05m | ~9h 15m | Large swing |
| 51.5 (London) | UK | ~16h 30m | ~7h 50m | Very large swing |
| 64.0 (Fairbanks) | Near Arctic | ~21h+ | ~3h 40m | Extreme - some zmanim fail |

**Effect on shaah zmanit (GRA):**
- shaah zmanit = (sunset - sunrise) / 12
- In Jerusalem on June 21: ~71.25 min
- In Jerusalem on December 21: ~50.4 min
- In London on June 21: ~82.5 min
- In London on December 21: ~39.2 min

This means Sof Zman Kriat Shma (3 shaot zmaniyot after sunrise) can differ by over an hour between winter and summer in London, versus a more moderate swing in Israel.

### 5.3 How Longitude Affects Zmanim

Longitude determines the exact local solar noon (chatzot). Within a single timezone, cities at different longitudes have different solar noon times:

| City | Longitude | Approx Solar Noon Difference from Jerusalem |
|------|-----------|----------------------------------------------|
| Eilat | 34.95 E | ~1 min earlier |
| Jerusalem | 35.21 E | Reference |
| Haifa | 34.99 E | ~1 min earlier |

Within Israel, the longitude range is narrow (34.3-35.9 E), so the effect is small (~4 minutes maximum). But worldwide:

| City | Longitude | Solar Noon vs NYC |
|------|-----------|-------------------|
| New York | -74.0 W | Reference |
| Chicago | -87.6 W | ~55 min later (same timezone!) |
| Los Angeles | -118.2 W | ~2h 57min later (different tz) |

### 5.4 How Elevation Affects Sunrise/Sunset

Higher elevation = wider horizon = earlier sunrise and later sunset.

The mathematical adjustment (used by KosherJava's NOAACalculator):
```
adjustedZenith = 90.833 + toDegrees(acos(earthRadius / (earthRadius + elevationMeters)))
```
Where `earthRadius = 6356.9 km`.

Practical examples:

| Location | Elevation | Sunrise Shift | Sunset Shift |
|----------|-----------|---------------|--------------|
| Tel Aviv | 5 m | ~0 sec | ~0 sec |
| Jerusalem | 754 m | ~3 min 30 sec earlier | ~3 min 30 sec later |
| Tzfat | 834 m | ~3 min 40 sec earlier | ~3 min 40 sec later |
| Gush Etzion | 900 m | ~3 min 50 sec earlier | ~3 min 50 sec later |
| Dead Sea* | -390 m | Use 0 m (sea level) | Use 0 m (sea level) |
| Johannesburg | 1753 m | ~5 min 20 sec earlier | ~5 min 20 sec later |
| Mexico City | 2240 m | ~6 min earlier | ~6 min later |

*KosherJava does not accept negative elevation. Below-sea-level locations should use elevation = 0.

**Halachic significance:** The difference between sea-level and elevated sunrise in Jerusalem is about 3.5 minutes. This affects Sof Zman Kriat Shma by ~3.5 minutes (since it cascades through the shaah zmanit calculation). For a zman with a tight deadline like Shma, this matters.

### 5.5 Timezone and DST

**Timezone is critical.** A wrong timezone shifts ALL zmanim by hours. The system must use IANA timezone IDs (e.g., `Asia/Jerusalem`), never raw UTC offsets, because offsets change with DST.

**Israel DST rules (since 2013 law, amended):**
- **Clocks forward:** Friday before the last Sunday of March, at 02:00 (becomes 03:00 IDT, UTC+3)
- **Clocks back:** Last Sunday of October, at 02:00 (becomes 01:00 IST, UTC+2)

These are handled automatically by `java.util.TimeZone("Asia/Jerusalem")` / `java.time.ZoneId.of("Asia/Jerusalem")` as long as the device's timezone database is up to date.

**Important:** The app should use `ZoneId` (java.time) rather than the legacy `TimeZone` class where possible. KosherJava accepts `java.util.TimeZone`, so convert:
```kotlin
val tz = TimeZone.getTimeZone(ZoneId.of("Asia/Jerusalem"))
```

### 5.6 setUseElevation() - When True vs False

| Setting | Effect | When to Use |
|---------|--------|-------------|
| `setUseElevation(true)` | `getSunrise()`/`getSunset()` return elevation-adjusted values. All dependent zmanim (Shma, Tefillah, etc.) shift accordingly. | Default for Israeli users following R' Ovadia Yosef and the Ohr HaChaim calendar. Most poskim who use elevation. |
| `setUseElevation(false)` | `getSunrise()`/`getSunset()` return sea-level values even if elevation is set on GeoLocation. | Some Ashkenazi poskim. Also as a safe fallback when elevation data is unreliable. |

**App default:** `setUseElevation(true)` with a toggle in settings for users who follow poskim that don't use elevation.

**Note:** Degree-based zmanim (e.g., `getAlos16Point1Degrees()`) are NOT affected by elevation because they measure the angle of the sun below the horizon, which relates to light level, not geographic horizon. Only geometric sunrise/sunset and zmanim derived from them are affected.

---

## 6. Elevation Data Strategy

### 6.1 The Problem with Phone GPS Elevation

Phone GPS altitude is unreliable:
- Typical error: +/- 20-50 meters
- Measures height above WGS84 ellipsoid, not sea level (geoid undulation in Israel is ~20-30m)
- In urban canyons or indoors, can be off by 100+ meters
- A 50m error at Jerusalem's elevation produces ~25 second shift in sunrise -- acceptable but not ideal.

### 6.2 Multi-Source Strategy

```
Priority 1: Bundled database lookup
   If user is within 5 km of a known city, use the database elevation.
   This covers >90% of Israeli users.

Priority 2: User manually entered elevation
   Trust the user if they entered it in manual coordinates mode.

Priority 3: Corrected GPS altitude
   GPS altitude minus local geoid undulation (can be hardcoded for Israel at ~24m).
   Formula: elevation_msl = gps_altitude - geoid_undulation

Priority 4: Google Elevation API (if online)
   GET https://maps.googleapis.com/maps/api/elevation/json
       ?locations=31.7683,35.2137
       &key=API_KEY
   Returns elevation in meters above sea level. Cache result.

Priority 5: Raw GPS altitude
   Last resort. Better than nothing.

Default: 0 (sea level)
   If all else fails, use sea level. This is conservative:
   the user gets "later" sunrise and "earlier" sunset,
   which is the chumra (stricter) direction for most zmanim.
```

### 6.3 Bundled Elevation Database

For the 30 Israeli cities and 18 worldwide cities in Section 3, elevation is included in the database. No API call needed. This covers the vast majority of users.

### 6.4 Below Sea Level Locations

KosherJava's `GeoLocation` throws `IllegalArgumentException` for negative elevation. For Tiberias (-200m) and the Dead Sea area (-390m):

```kotlin
fun safeElevation(rawElevation: Double): Double {
    return max(0.0, rawElevation)
}
```

**Halachic note:** For below-sea-level locations, sea-level sunrise/sunset is actually the correct halachic baseline because the sun is visible at the geometric horizon at sea level. The surrounding terrain (mountains) may block the sun, but that is a "visible sunrise" issue (Chai Tables), not an elevation calculation issue.

---

## 7. Edge Cases

### 7.1 User Moves to a Different City During the Day

**Scenario:** User is in Jerusalem in the morning, drives to Tel Aviv in the afternoon.

**Policy:** Zmanim do NOT retroactively change. The principle is:
- Morning zmanim (already passed) remain as calculated at the morning location.
- Future zmanim should update if the user opens the app and a new location is detected.

**Implementation:**
- On each app open, check GPS (throttled to max once per 30 min).
- If new location is > 5 km from saved location, show a subtle banner: "Location changed to Tel Aviv. Updating zmanim."
- Update only future zmanim on the display.
- Store both "morning location" and "current location" for the day if they differ.

### 7.2 User is on an Airplane

**Scenario:** User is flying, GPS shows high altitude and rapidly changing coordinates.

**Detection:** If altitude > 3000m AND speed > 200 km/h (from Location object), assume flight.

**Policy:**
- Show last ground-based location.
- Display a notice: "Location not updated during flight. Showing zmanim for [last city]."
- Zmanim on a plane are a complex halachic question (which timezone? which horizon?). The app should not attempt to solve this; show the last known ground location.

### 7.3 Extreme Latitudes (Near Arctic/Antarctic)

**Scenario:** User is in Tromso, Norway (69.6 N) during summer -- the sun never fully sets.

**Problem:** Sunset returns `null` from KosherJava. All sunset-dependent zmanim also return `null`.

**Handling:**
```kotlin
val sunset = czc.getSunset()
if (sunset == null) {
    // Extreme latitude: sun does not set (or rise)
    // Display: "שקיעה: אין (קו רוחב קיצוני)"
    // Offer to use nearest "normal" latitude or fixed 72-minute offsets
}
```

Latitude thresholds for common issues:
| Latitude | Issue Period | Problem |
|----------|-------------|---------|
| > 48.5 N | ~Jun 15-27 | Astronomical twilight doesn't end (Rabbeinu Tam issues) |
| > 55 N | ~May-Jul | Very short nights, some degree-based zmanim fail |
| > 60 N | ~May-Aug | Some days have no halachic "night" at all |
| > 66.5 N | Arctic Circle | Midnight sun / polar night |

**Strategy:** For latitudes > 60, show a warning and suggest consulting a local rabbi. Offer a settings toggle to use a "reference latitude" (e.g., 51.5 for London conventions).

### 7.4 High vs. Low Elevation Extremes

**Jerusalem (754m) vs Dead Sea (-390m):**
- The sunrise difference is ~3.5 minutes between Jerusalem and a hypothetical sea-level Jerusalem.
- Dead Sea uses elevation = 0, so sunrise is "normal" sea-level time -- but the sun actually appears later because high cliffs surround the Dead Sea basin. This is a visible-sunrise issue, not calculable by simple geometry.
- For Dead Sea, recommend Chai Tables data if available, or accept sea-level as the baseline.

### 7.5 User Denies GPS Permission

**Flow:**
1. First launch: explain why location is needed (in Hebrew): "The app needs your location to calculate accurate prayer times for your area."
2. If denied: immediately show city picker. No nagging.
3. Store `locationPermissionDenied = true` in DataStore.
4. In settings, show a "Grant location permission" option that opens system settings.
5. Never block app usage. Manual city selection works perfectly.

### 7.6 GPS Gives Wrong Elevation

**Common scenario:** User is indoors; GPS reports altitude of 0 or wildly wrong value.

**Mitigations:**
1. Compare GPS elevation to bundled database. If difference > 200m and user is near a known city, prefer database value.
2. If no database match, use GPS elevation but clamp to reasonable range (-500 to 5000m).
3. Show elevation in settings so user can manually correct it.
4. Log elevation source (GPS / database / manual / API) for debugging.

---

## 8. Data Model (Kotlin)

```kotlin
/**
 * Represents a geographic location for zmanim calculation.
 * This is the app's domain model, separate from KosherJava's GeoLocation.
 */
@Serializable
data class ZmanimLocation(
    val id: String,                          // Unique ID (city slug or "custom" or "gps")
    val nameHebrew: String,                  // "ירושלים"
    val nameEnglish: String,                 // "Jerusalem"
    val latitude: Double,                    // 31.7683
    val longitude: Double,                   // 35.2137
    val elevation: Double,                   // 754.0 (meters, always >= 0)
    val timezoneId: String,                  // "Asia/Jerusalem"
    val source: LocationSource,              // How this location was obtained
    val elevationSource: ElevationSource,    // Where elevation data came from
    val lastUpdated: Long                    // epoch millis of last GPS fix (0 if manual)
) {
    /**
     * Convert to KosherJava GeoLocation for calculation.
     */
    fun toGeoLocation(): GeoLocation {
        return GeoLocation(
            nameEnglish,
            latitude,
            longitude,
            max(0.0, elevation),  // KosherJava requires non-negative
            TimeZone.getTimeZone(timezoneId)
        )
    }
}

@Serializable
enum class LocationSource {
    GPS,              // Auto-detected via FusedLocationProviderClient
    CITY_DATABASE,    // Selected from built-in city list
    MANUAL_ENTRY,     // User typed coordinates
    LAST_KNOWN        // Fallback to previously saved location
}

@Serializable
enum class ElevationSource {
    DATABASE,         // From bundled city database
    GPS_RAW,          // Raw GPS altitude (unreliable)
    GPS_CORRECTED,    // GPS altitude minus geoid undulation
    API,              // Google Elevation API or similar
    USER_MANUAL,      // User entered manually
    DEFAULT_SEA_LEVEL // Fallback: 0m
}

/**
 * Predefined city entry in the bundled database.
 */
data class CityEntry(
    val id: String,                // "jerusalem", "tel_aviv", etc.
    val nameHebrew: String,
    val nameEnglish: String,
    val latitude: Double,
    val longitude: Double,
    val elevation: Double,
    val timezoneId: String,
    val country: String,           // "IL", "US", "GB", etc.
    val isIsrael: Boolean
) {
    fun toZmanimLocation(): ZmanimLocation {
        return ZmanimLocation(
            id = id,
            nameHebrew = nameHebrew,
            nameEnglish = nameEnglish,
            latitude = latitude,
            longitude = longitude,
            elevation = elevation,
            timezoneId = timezoneId,
            source = LocationSource.CITY_DATABASE,
            elevationSource = ElevationSource.DATABASE,
            lastUpdated = 0
        )
    }
}

/**
 * Location-related settings/preferences.
 */
@Serializable
data class LocationSettings(
    val useElevation: Boolean = true,            // setUseElevation()
    val autoDetectLocation: Boolean = true,       // Try GPS on app open
    val showElevatedSunrise: Boolean = true,       // Display HaNetz with elevation
    val showSeaLevelSunrise: Boolean = false,      // Also show HaNetz mishor
    val selectedLocationId: String? = null,        // Currently active location ID
    val lastGpsLatitude: Double? = null,
    val lastGpsLongitude: Double? = null,
    val lastGpsElevation: Double? = null,
    val lastGpsTimestamp: Long = 0
)
```

---

## 9. Storage Layer

Use Jetpack DataStore (Preferences or Proto) for location persistence.

```kotlin
class LocationDataStore(private val context: Context) {

    private val Context.dataStore by preferencesDataStore(name = "location_prefs")

    companion object {
        val KEY_LOCATION_JSON = stringPreferencesKey("current_location_json")
        val KEY_SETTINGS_JSON = stringPreferencesKey("location_settings_json")
    }

    /**
     * Save the current location. Called after GPS fix or manual selection.
     */
    suspend fun saveLocation(location: ZmanimLocation) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LOCATION_JSON] = Json.encodeToString(location)
        }
    }

    /**
     * Load the saved location. Returns null on first launch.
     */
    fun getLocation(): Flow<ZmanimLocation?> {
        return context.dataStore.data.map { prefs ->
            prefs[KEY_LOCATION_JSON]?.let { json ->
                try {
                    Json.decodeFromString<ZmanimLocation>(json)
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    suspend fun saveSettings(settings: LocationSettings) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SETTINGS_JSON] = Json.encodeToString(settings)
        }
    }

    fun getSettings(): Flow<LocationSettings> {
        return context.dataStore.data.map { prefs ->
            prefs[KEY_SETTINGS_JSON]?.let { json ->
                try {
                    Json.decodeFromString<LocationSettings>(json)
                } catch (e: Exception) {
                    LocationSettings() // defaults
                }
            } ?: LocationSettings()
        }
    }
}
```

---

## 10. UI Flow

### 10.1 First Launch (Onboarding)

```
+------------------------------------------+
| Welcome / ברוך הבא                        |
|                                          |
| [App logo / illustration]                |
|                                          |
| כדי לחשב את הזמנים במדויק,                |
| אנחנו צריכים לדעת את המיקום שלך.          |
|                                          |
| [  זהה מיקום אוטומטית  ]    <-- Primary  |
| [  בחר עיר ידנית          ]   <-- Secondary|
|                                          |
+------------------------------------------+
```

**Flow A: Auto-detect**
1. Request `ACCESS_FINE_LOCATION` permission (with rationale dialog if needed).
2. On grant: get GPS fix, find nearest city in database, show confirmation:
   ```
   "זיהינו שאתה ב-ירושלים (גובה: 754 מ'). נכון?"
   [כן, המשך]  [לא, בחר עיר אחרת]
   ```
3. On deny: fall through to Flow B.

**Flow B: Manual city selection**
1. Show city picker (Section 3.1).
2. User taps a city, immediately proceeds to main screen.

### 10.2 Settings Screen - Location Section

```
+------------------------------------------+
| מיקום                                     |
|                                          |
| מיקום נוכחי: ירושלים        [שנה]         |
| קו רוחב: 31.7683                         |
| קו אורך: 35.2137                         |
| גובה: 754 מ' (ממאגר ערים)                 |
| אזור זמן: Asia/Jerusalem (UTC+2/+3)      |
|                                          |
| [Toggle] זיהוי מיקום אוטומטי     [ON]     |
| [Toggle] שימוש בגובה לחישוב     [ON]      |
| [Toggle] הצג זריחה לפי גובה     [ON]      |
| [Toggle] הצג גם זריחה מישורית   [OFF]     |
|                                          |
| [הזנת קואורדינטות ידנית]                   |
|                                          |
+------------------------------------------+
```

### 10.3 Quick Location Change (from Home Screen)

Tapping the location name in the top bar opens a bottom sheet:

```
+------------------------------------------+
| שנה מיקום                                 |
|                                          |
| [pin] ירושלים (נוכחי)          [v]        |
|                                          |
| --- אחרונים ---                           |
| תל אביב                                  |
| חיפה                                     |
|                                          |
| [חפש עיר...]                             |
| [זהה מיקום GPS]                           |
| [הזנה ידנית]                              |
+------------------------------------------+
```

---

## 11. Integration with ZmanimCalculator

### 11.1 LocationManager Repository

```kotlin
class LocationRepository(
    private val locationProvider: LocationProvider,
    private val dataStore: LocationDataStore,
    private val cityDatabase: CityDatabase,
    private val elevationResolver: ElevationResolver
) {
    /**
     * Get the best available location for zmanim calculation.
     * Called on app open and when user changes location.
     */
    suspend fun resolveLocation(settings: LocationSettings): ZmanimLocation {
        // If auto-detect is enabled and permission granted, try GPS
        if (settings.autoDetectLocation) {
            val gpsResult = locationProvider.getCurrentLocation()
            if (gpsResult is LocationResult.Success) {
                val nearestCity = cityDatabase.findNearest(
                    gpsResult.latitude, gpsResult.longitude
                )

                val elevation = elevationResolver.resolve(
                    gpsLat = gpsResult.latitude,
                    gpsLon = gpsResult.longitude,
                    gpsAltitude = gpsResult.elevation,
                    nearestCity = nearestCity
                )

                val location = ZmanimLocation(
                    id = nearestCity?.id ?: "gps",
                    nameHebrew = nearestCity?.nameHebrew ?: "מיקום GPS",
                    nameEnglish = nearestCity?.nameEnglish ?: "GPS Location",
                    latitude = gpsResult.latitude,
                    longitude = gpsResult.longitude,
                    elevation = elevation.value,
                    timezoneId = nearestCity?.timezoneId
                        ?: resolveTimezone(gpsResult.latitude, gpsResult.longitude),
                    source = LocationSource.GPS,
                    elevationSource = elevation.source,
                    lastUpdated = System.currentTimeMillis()
                )
                dataStore.saveLocation(location)
                return location
            }
        }

        // Fall back to saved location
        val saved = dataStore.getLocation().first()
        if (saved != null) return saved

        // Absolute fallback: Jerusalem
        return cityDatabase.getCity("jerusalem")!!.toZmanimLocation()
    }
}
```

### 11.2 ElevationResolver

```kotlin
class ElevationResolver(
    private val cityDatabase: CityDatabase
) {
    data class ElevationResult(
        val value: Double,
        val source: ElevationSource
    )

    /**
     * Determine the best elevation for a given GPS coordinate.
     */
    fun resolve(
        gpsLat: Double,
        gpsLon: Double,
        gpsAltitude: Double?,
        nearestCity: CityEntry?
    ): ElevationResult {

        // Priority 1: If near a known city (< 5 km), use database elevation
        if (nearestCity != null) {
            val distance = haversineKm(gpsLat, gpsLon,
                nearestCity.latitude, nearestCity.longitude)
            if (distance < 5.0) {
                return ElevationResult(
                    max(0.0, nearestCity.elevation),
                    ElevationSource.DATABASE
                )
            }
        }

        // Priority 2: Corrected GPS altitude (subtract geoid for Israel ~24m)
        if (gpsAltitude != null && gpsAltitude > -500 && gpsAltitude < 9000) {
            val isInIsrael = gpsLat in 29.0..34.0 && gpsLon in 34.0..36.0
            val geoidCorrection = if (isInIsrael) 24.0 else 0.0
            val corrected = gpsAltitude - geoidCorrection
            return ElevationResult(
                max(0.0, corrected),
                ElevationSource.GPS_CORRECTED
            )
        }

        // Priority 3: Nearest city elevation as rough estimate
        if (nearestCity != null) {
            return ElevationResult(
                max(0.0, nearestCity.elevation),
                ElevationSource.DATABASE
            )
        }

        // Default: sea level
        return ElevationResult(0.0, ElevationSource.DEFAULT_SEA_LEVEL)
    }
}
```

### 11.3 Feeding into ZmanimCalendar

```kotlin
class ZmanimCalculator(
    private val locationRepository: LocationRepository,
    private val settingsRepository: SettingsRepository
) {
    /**
     * Calculate all zmanim for a given date.
     */
    suspend fun calculateZmanim(date: LocalDate): ZmanimDay {
        val settings = settingsRepository.getLocationSettings().first()
        val location = locationRepository.resolveLocation(settings)

        val geoLocation = location.toGeoLocation()

        val calendar = ComplexZmanimCalendar(geoLocation).apply {
            this.calendar.set(date.year, date.monthValue - 1, date.dayOfMonth)
            setUseElevation(settings.useElevation)
        }

        return ZmanimDay(
            date = date,
            location = location,
            alotHashachar = calendar.alos72Zmanis,
            misheyakir = calendar.getMisheyakir10Point2Degrees(),
            haNetzMishor = calendar.seaLevelSunrise,
            haNetz = if (settings.useElevation) calendar.sunrise else calendar.seaLevelSunrise,
            sofZmanShmaGra = calendar.sofZmanShmaGRA,
            sofZmanShmaMga = calendar.sofZmanShmaMGA,
            sofZmanTfilaGra = calendar.sofZmanTfilaGRA,
            sofZmanTfilaMga = calendar.sofZmanTfilaMGA,
            chatzot = calendar.chatzos,
            minchaGedolah = calendar.minchaGedolah,
            minchaKetanah = calendar.minchaKetana,
            plagHamincha = calendar.plagHamincha,
            shekia = if (settings.useElevation) calendar.sunset else calendar.seaLevelSunset,
            shekiaMishor = calendar.seaLevelSunset,
            tzeitHakochavim = calendar.tzeit,
            tzeitRabbeenuTam = calendar.tzaisGeonim8Point5Degrees,
            chatzotLayla = null, // calculated separately
            shaahZmanitGra = calendar.shaahZmanisGra,
            shaahZmanitMga = calendar.shaahZmanisMGA
        )
    }
}
```

---

## 12. City Database

### 12.1 Implementation

The city database is a hardcoded Kotlin object compiled into the app (no Room/SQLite needed for ~50 entries):

```kotlin
object CityDatabase {

    private val cities: List<CityEntry> = listOf(
        // === ISRAEL ===
        CityEntry("jerusalem", "ירושלים", "Jerusalem",
            31.7683, 35.2137, 754.0, "Asia/Jerusalem", "IL", true),
        CityEntry("tel_aviv", "תל אביב - יפו", "Tel Aviv",
            32.0853, 34.7818, 5.0, "Asia/Jerusalem", "IL", true),
        CityEntry("haifa", "חיפה", "Haifa",
            32.7940, 34.9896, 40.0, "Asia/Jerusalem", "IL", true),
        CityEntry("beer_sheva", "באר שבע", "Beer Sheva",
            31.2530, 34.7915, 285.0, "Asia/Jerusalem", "IL", true),
        CityEntry("eilat", "אילת", "Eilat",
            29.5577, 34.9519, 15.0, "Asia/Jerusalem", "IL", true),
        CityEntry("tzfat", "צפת", "Tzfat (Safed)",
            32.9646, 35.4960, 834.0, "Asia/Jerusalem", "IL", true),
        CityEntry("tiberias", "טבריה", "Tiberias",
            32.7922, 35.5312, 0.0, "Asia/Jerusalem", "IL", true), // -200m, clamped to 0
        CityEntry("netanya", "נתניה", "Netanya",
            32.3215, 34.8532, 32.0, "Asia/Jerusalem", "IL", true),
        CityEntry("ashdod", "אשדוד", "Ashdod",
            31.8044, 34.6553, 26.0, "Asia/Jerusalem", "IL", true),
        CityEntry("petah_tikva", "פתח תקווה", "Petah Tikva",
            32.0841, 34.8878, 50.0, "Asia/Jerusalem", "IL", true),
        CityEntry("bnei_brak", "בני ברק", "Bnei Brak",
            32.0834, 34.8332, 30.0, "Asia/Jerusalem", "IL", true),
        CityEntry("ramat_gan", "רמת גן", "Ramat Gan",
            32.0700, 34.8243, 40.0, "Asia/Jerusalem", "IL", true),
        CityEntry("herzliya", "הרצליה", "Herzliya",
            32.1629, 34.7915, 20.0, "Asia/Jerusalem", "IL", true),
        CityEntry("kfar_saba", "כפר סבא", "Kfar Saba",
            32.1751, 34.9066, 50.0, "Asia/Jerusalem", "IL", true),
        CityEntry("raanana", "רעננה", "Ra'anana",
            32.1837, 34.8708, 45.0, "Asia/Jerusalem", "IL", true),
        CityEntry("modiin", "מודיעין-מכבים-רעות", "Modi'in",
            31.8939, 35.0104, 280.0, "Asia/Jerusalem", "IL", true),
        CityEntry("ashkelon", "אשקלון", "Ashkelon",
            31.6688, 34.5743, 35.0, "Asia/Jerusalem", "IL", true),
        CityEntry("afula", "עפולה", "Afula",
            32.6071, 35.2886, 60.0, "Asia/Jerusalem", "IL", true),
        CityEntry("kiryat_shmona", "קריית שמונה", "Kiryat Shmona",
            33.2072, 35.5713, 210.0, "Asia/Jerusalem", "IL", true),
        CityEntry("nahariya", "נהריה", "Nahariya",
            33.0056, 35.0953, 10.0, "Asia/Jerusalem", "IL", true),
        CityEntry("akko", "עכו", "Akko (Acre)",
            32.9215, 35.0764, 8.0, "Asia/Jerusalem", "IL", true),
        CityEntry("maale_adumim", "מעלה אדומים", "Ma'ale Adumim",
            31.7781, 35.3001, 480.0, "Asia/Jerusalem", "IL", true),
        CityEntry("ariel", "אריאל", "Ariel",
            32.1065, 35.1734, 540.0, "Asia/Jerusalem", "IL", true),
        CityEntry("gush_etzion", "גוש עציון (אפרת)", "Gush Etzion (Efrat)",
            31.6581, 35.1550, 900.0, "Asia/Jerusalem", "IL", true),
        CityEntry("karnei_shomron", "קרני שומרון", "Karnei Shomron",
            32.1710, 35.0966, 380.0, "Asia/Jerusalem", "IL", true),
        CityEntry("rishon_lezion", "ראשון לציון", "Rishon LeZion",
            31.9642, 34.8044, 30.0, "Asia/Jerusalem", "IL", true),
        CityEntry("holon", "חולון", "Holon",
            32.0116, 34.7831, 15.0, "Asia/Jerusalem", "IL", true),
        CityEntry("beit_shemesh", "בית שמש", "Beit Shemesh",
            31.7510, 34.9884, 320.0, "Asia/Jerusalem", "IL", true),
        CityEntry("rehovot", "רחובות", "Rehovot",
            31.8928, 34.8113, 40.0, "Asia/Jerusalem", "IL", true),
        CityEntry("dead_sea", "ים המלח (עין גדי)", "Dead Sea (Ein Gedi)",
            31.4504, 35.3868, 0.0, "Asia/Jerusalem", "IL", true), // -390m, clamped to 0

        // === WORLDWIDE ===
        CityEntry("new_york", "ניו יורק", "New York",
            40.7128, -74.0060, 10.0, "America/New_York", "US", false),
        CityEntry("los_angeles", "לוס אנג'לס", "Los Angeles",
            34.0522, -118.2437, 71.0, "America/Los_Angeles", "US", false),
        CityEntry("chicago", "שיקגו", "Chicago",
            41.8781, -87.6298, 181.0, "America/Chicago", "US", false),
        CityEntry("miami", "מיאמי", "Miami",
            25.7617, -80.1918, 2.0, "America/New_York", "US", false),
        CityEntry("boston", "בוסטון", "Boston",
            42.3601, -71.0589, 14.0, "America/New_York", "US", false),
        CityEntry("london", "לונדון", "London",
            51.5074, -0.1278, 11.0, "Europe/London", "GB", false),
        CityEntry("manchester", "מנצ'סטר", "Manchester",
            53.4808, -2.2426, 38.0, "Europe/London", "GB", false),
        CityEntry("paris", "פריז", "Paris",
            48.8566, 2.3522, 35.0, "Europe/Paris", "FR", false),
        CityEntry("toronto", "טורונטו", "Toronto",
            43.6532, -79.3832, 76.0, "America/Toronto", "CA", false),
        CityEntry("montreal", "מונטריאול", "Montreal",
            45.5017, -73.5673, 36.0, "America/Toronto", "CA", false),
        CityEntry("melbourne", "מלבורן", "Melbourne",
            -37.8136, 144.9631, 31.0, "Australia/Melbourne", "AU", false),
        CityEntry("sydney", "סידני", "Sydney",
            -33.8688, 151.2093, 3.0, "Australia/Sydney", "AU", false),
        CityEntry("buenos_aires", "בואנוס איירס", "Buenos Aires",
            -34.6037, -58.3816, 25.0, "America/Argentina/Buenos_Aires", "AR", false),
        CityEntry("johannesburg", "יוהנסבורג", "Johannesburg",
            -26.2041, 28.0473, 1753.0, "Africa/Johannesburg", "ZA", false),
        CityEntry("mexico_city", "מקסיקו סיטי", "Mexico City",
            19.4326, -99.1332, 2240.0, "America/Mexico_City", "MX", false),
        CityEntry("sao_paulo", "סאו פאולו", "Sao Paulo",
            -23.5505, -46.6333, 760.0, "America/Sao_Paulo", "BR", false),
        CityEntry("antwerp", "אנטוורפן", "Antwerp",
            51.2194, 4.4025, 7.0, "Europe/Brussels", "BE", false),
        CityEntry("berlin", "ברלין", "Berlin",
            52.5200, 13.4050, 34.0, "Europe/Berlin", "DE", false),
    )

    fun getAllCities(): List<CityEntry> = cities

    fun getIsraelCities(): List<CityEntry> = cities.filter { it.isIsrael }

    fun getWorldwideCities(): List<CityEntry> = cities.filter { !it.isIsrael }

    fun getCity(id: String): CityEntry? = cities.find { it.id == id }

    /**
     * Search cities by Hebrew or English name.
     * Supports partial matching.
     */
    fun search(query: String): List<CityEntry> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return cities
        return cities.filter {
            it.nameHebrew.contains(q) ||
            it.nameEnglish.lowercase().contains(q)
        }
    }

    /**
     * Find the nearest city to given coordinates.
     * Returns null if no city is within 100 km.
     */
    fun findNearest(lat: Double, lon: Double): CityEntry? {
        return cities
            .map { it to haversineKm(lat, lon, it.latitude, it.longitude) }
            .filter { it.second < 100.0 }
            .minByOrNull { it.second }
            ?.first
    }
}

/**
 * Haversine formula for distance in km between two points.
 */
fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val R = 6371.0 // Earth radius in km
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2).pow(2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return R * c
}
```

---

## Summary of Key Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Permission level | `ACCESS_FINE_LOCATION` | Enables GPS altitude; can downgrade to coarse |
| GPS method | `getCurrentLocation()` once | Not continuous; battery-efficient |
| GPS priority | `PRIORITY_BALANCED_POWER_ACCURACY` | ~100m accuracy is sufficient; saves battery |
| Elevation primary source | Bundled city database | Covers 90%+ of users; no API call needed |
| Elevation fallback | GPS altitude - geoid correction | Better than raw GPS; geoid ~24m in Israel |
| Below-sea-level | Clamp to 0 | KosherJava requirement; halachically correct |
| Timezone | IANA timezone IDs via `ZoneId` | Handles DST automatically |
| Storage | Jetpack DataStore (Preferences) | Simple, async, corruption-resistant |
| City database | Hardcoded Kotlin object | Fast, offline, no DB migration headaches for ~50 entries |
| Default location | Jerusalem | Safe fallback; largest religious Jewish population |
| `setUseElevation` default | `true` | Follows R' Ovadia Yosef / Ohr HaChaim calendar |
| Extreme latitude handling | Warning + optional reference latitude | Complex halachic issue; defer to local rabbi |
| In-flight detection | Freeze at last ground location | Airborne zmanim are unresolved halachically |
