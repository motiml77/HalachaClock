# Zmanim Reference - Complete Method Mapping

## Sources Analyzed
- **KosherJava**: `ComprehensiveZmanimCalendar.java` + `ZmanimCalendar.java` (GitHub: KosherJava/zmanim, master branch)
- **Zemaneh Yosef**: `ROZmanimCalendar.java` + `ZmanimFactory.java` (GitHub: Zemaneh-Yosef/RabbiOvadiahYosefCalendarAndroidApp, master branch)
- **Hebcal**: REST API at `https://www.hebcal.com/zmanim?cfg=json`

---

## 1. Chatzot Layla - Solar Midnight

| Field | Value |
|-------|-------|
| KosherJava | No direct method. Calculated via `getSunTransit()` of current day + half the difference to next day's `getSunTransit()` |
| ROZmanimCalendar | `getSolarMidnight()` -- calculates midpoint between today's chatzot and tomorrow's chatzot: `getTimeOffset(getChatzot(), (chatzotTomorrow - chatzotToday) / 2)` |
| ZmanimFactory key | `"NightChatzot"` |
| Hebcal | `chatzotNight` |
| Calculation | Midpoint between solar noon (chatzot) of current day and solar noon of next day |
| Halachic source | Based on astronomical solar midnight. Used for latest time for Kiddush Levana, Tikkun Chatzot, etc. |

---

## 2. Alos HaShachar 90 Zmaniyot Minutes

| Field | Value |
|-------|-------|
| KosherJava | `getAlos90Zmanis()` -- calls `getZmanisBasedOffset(-1.5)` (1.5 shaos zmaniyos = 90 zmaniyot minutes) |
| ROZmanimCalendar | Not used directly (ROZmanimCalendar uses 72 zmaniyot for alos) |
| Hebcal | N/A |
| Calculation | Sunrise minus 1.5 shaos zmaniyos (GRA). Based on 22.5-minute mil, 4 mil = 90 min. `sunrise - (shaahZmanisGra * 1.5)` |
| Halachic source | Opinion that a mil is 22.5 minutes. Rishonim who hold walking 4 mil takes 90 minutes. |

---

## 3. Alos HaShachar 72 Zmaniyot Minutes

| Field | Value |
|-------|-------|
| KosherJava | `getAlos72Zmanis()` -- calls `getZmanisBasedOffset(-1.2)` (1.2 shaos zmaniyos = 72 zmaniyot minutes = 1/10 of the day) |
| ROZmanimCalendar | `getAlotHashachar()` (when `amudehHoraah == false`): calls `getZmanisBasedOffset(-1.2)`. When `amudehHoraah == true`: uses equinox-based percentage from 16.04 degrees |
| ZmanimFactory key | `"Alot"` |
| Hebcal | `alotHaShachar` (but Hebcal uses 16.1-degree-based, not zmaniyot) |
| Calculation | `elevationAdjustedSunrise - (shaahZmanisGra * 1.2)`. 72 min = 1/10 of 720-min day |
| Halachic source | Rambam and others: 18-minute mil, 4 mil = 72 minutes. Minchas Cohen, Magen Avraham. R' Ovadia Yosef ZT"L per Ohr HaChaim calendar. |

---

## 4. Misheyakir 66 Zmaniyot Minutes (R' Ovadia Yosef)

| Field | Value |
|-------|-------|
| KosherJava | No direct method for 66 zmaniyot. Has degree-based: `getMisheyakir10Point2Degrees()`, `getMisheyakir11Degrees()`, `getMisheyakir11Point5Degrees()` |
| ROZmanimCalendar | `getMisheyakir66ZmaniyotMinutes()` -- when `amudehHoraah == false`: `getZmanisBasedOffset(-1.1)` (66/60 = 1.1 hours). When `amudehHoraah == true`: equinox percentage * 11/12 |
| ZmanimFactory key | Added conditionally as `"TalitTefilin" + " (66)"` with `is66MisheyakirZman = true` |
| Hebcal | N/A |
| Calculation | Sunrise minus 1.1 shaos zmaniyos (66 zmaniyot minutes). For Amudei Horaah: equinox-based calculation with 16.04 degrees, multiplied by 11/12 (= 66/72) |
| Halachic source | Pri Chadash. This is for great need only (sha'at hadchak). R' Ovadia Yosef's Ohr HaChaim calendar. |

---

## 5. Misheyakir 60 Zmaniyot Minutes

| Field | Value |
|-------|-------|
| KosherJava | No direct 60 zmaniyot method. Has `getAlos60()` (fixed 60 min) |
| ROZmanimCalendar | `getMisheyakir60ZmaniyotMinutes()` -- when `amudehHoraah == false`: `getZmanisBasedOffset(-1)` (1 shaah zmanit = 60 zmaniyot min). When `amudehHoraah == true`: equinox percentage * 5/6 (= 60/72) |
| ZmanimFactory key | `"TalitTefilin"` |
| Hebcal | `misheyakir` (but Hebcal's calculation may differ) |
| Calculation | Sunrise minus 1 shaah zmanit (60 zmaniyot minutes). For Amudei Horaah: equinox-based with 16.04 degrees, multiplied by 5/6 |
| Halachic source | L'chatchila time for talit and tefilin. R' Ovadia Yosef's Ohr HaChaim calendar. |

---

## 6. Sea Level Sunrise (HaNetz Mishor)

| Field | Value |
|-------|-------|
| KosherJava | `getSeaLevelSunrise()` (in AstronomicalCalendar base class) |
| ROZmanimCalendar | `getSeaLevelSunrise()` (inherited) |
| ZmanimFactory key | `"HaNetz"` with `"(Mishor)"` label when Chai Tables sunrise not available |
| Hebcal | `sunrise` |
| Calculation | Sunrise at sea level (0m elevation), sun center at geometric horizon (90.833 degrees zenith, accounting for refraction) |
| Halachic source | Standard astronomical sunrise without elevation adjustment. Used as fallback when visible sunrise data not available. |

---

## 7. Elevation Adjusted Sunrise

| Field | Value |
|-------|-------|
| KosherJava | `getSunrise()` (when `isUseElevation() == true`) or `getElevationAdjustedSunrise()` |
| ROZmanimCalendar | `getElevationAdjustedSunrise()` / `getSunrise()` |
| ZmanimFactory key | `"HaNetz" + " " + "Elevated"` (shown when `ShowElevatedSunrise` preference is true) |
| Hebcal | N/A (Hebcal uses sea level) |
| Calculation | Sunrise adjusted for geographic elevation of the location. Higher elevation = earlier sunrise. Uses refraction-adjusted zenith. |
| Halachic source | Many poskim hold that elevation should be used. The Ohr HaChaim calendar uses elevation for all zmanim calculations. |

---

## 8. Visible Sunrise (HaNetz - Chai Tables)

| Field | Value |
|-------|-------|
| KosherJava | N/A (not in KosherJava) |
| ROZmanimCalendar | `getHaNetz()` -- reads from pre-downloaded Chai Tables data file containing visible sunrise times for each day |
| ZmanimFactory key | `"HaNetz"` (when Chai Tables data is available and `showMishorSunrise` is false) |
| Hebcal | N/A |
| Calculation | Exact time the sun disc becomes visible above the actual horizon (accounting for terrain/buildings). Data sourced from chaitables.com which calculates based on actual horizon profile of the location. |
| Halachic source | R' Ovadia Yosef ZT"L held that HaNetz should be the visible sunrise, not the astronomical sunrise. This is the method used in the Ohr HaChaim calendar. Data loaded via `ChaiTablesWebJava` class. |

---

## 9. Sof Zman Kriat Shma GRA

| Field | Value |
|-------|-------|
| KosherJava | `getSofZmanShmaGRA()` -- `getSofZmanShma(getElevationAdjustedSunrise(), getElevationAdjustedSunset(), true)` |
| ROZmanimCalendar | `getSofZmanShmaGRA()` (inherited from ZmanimCalendar) |
| ZmanimFactory key | `"SofZmanShmaGRA"` |
| Hebcal | `sofZmanShma` |
| Calculation | 3 shaos zmaniyos after sunrise. Day = sunrise to sunset (GRA). `sunrise + (shaahZmanisGra * 3)` |
| Halachic source | Vilna Gaon (GRA) and Baal HaTanya: day starts at sunrise and ends at sunset. Shma must be read in first 3 hours of the day. Shulchan Aruch Orach Chaim 58:1. |

---

## 10. Sof Zman Shma MGA 72 Zmaniyot Minutes

| Field | Value |
|-------|-------|
| KosherJava | `getSofZmanShmaMGA72MinutesZmanis()` -- `getSofZmanShma(getAlos72Zmanis(), getTzais72Zmanis())` |
| ROZmanimCalendar | `getSofZmanShmaMGA72MinutesZmanis()` -- `getSofZmanShma(getAlotHashachar(), getTzais72Zmanis())` |
| ZmanimFactory key | `"SofZmanShmaMGA"` |
| Hebcal | `sofZmanShmaMGA` (Hebcal uses fixed 72 min: `getSofZmanShma(getAlos72(), getTzais72())`) |
| Calculation | 3 shaos zmaniyos after alos. Day = alos 72 zmaniyot to tzais 72 zmaniyot. Shaah = (tzais72zmanis - alos72zmanis) / 12 |
| Halachic source | Magen Avraham (MGA): day starts at alos and ends at tzais. 72 zmaniyot minutes based on 18-min mil, 4 mil. |

---

## 11. Sof Zman Tfila GRA

| Field | Value |
|-------|-------|
| KosherJava | `getSofZmanTfilaGRA()` -- `getSofZmanTfila(getElevationAdjustedSunrise(), getElevationAdjustedSunset(), true)` |
| ROZmanimCalendar | `getSofZmanTfilaGRA()` (inherited from ZmanimCalendar) |
| ZmanimFactory key | `"SofZmanTefila"` |
| Hebcal | `sofZmanTfilla` |
| Calculation | 4 shaos zmaniyos after sunrise. Day = sunrise to sunset (GRA). `sunrise + (shaahZmanisGra * 4)` |
| Halachic source | GRA: Tefila must be completed within first 4 hours of the day. Shulchan Aruch Orach Chaim 89:1. |

---

## 12. Sof Zman Tfila MGA 72 Zmaniyot Minutes

| Field | Value |
|-------|-------|
| KosherJava | `getSofZmanTfilaMGA72MinutesZmanis()` -- `getSofZmanTfila(getAlos72Zmanis(), getTzais72Zmanis())` |
| ROZmanimCalendar | `getSofZmanTfilaMGA72MinutesZmanis()` -- `getSofZmanTfila(getAlotHashachar(), getTzais72Zmanis())` |
| ZmanimFactory key | N/A (Zemaneh Yosef labels this as Brachot Shma / Achilat Chametz) |
| Hebcal | `sofZmanTfillaMGA` (fixed 72 min version) |
| Calculation | 4 shaos zmaniyos after alos. Day = alos 72 zmaniyot to tzais 72 zmaniyot. `alos + (shaahMGA * 4)` |
| Halachic source | Magen Avraham: day starts at alos, ends at tzais. 4 hours into the MGA day. |

---

## 13. Mincha Gedola 30 Fixed Minutes

| Field | Value |
|-------|-------|
| KosherJava | `getMinchaGedola30Minutes()` -- `getTimeOffset(getChatzos(), 30 * MINUTE_MILLIS)` |
| KosherJava (greater) | `getMinchaGedolaGreaterThan30()` -- returns the later of `getMinchaGedola30Minutes()` and `getMinchaGedola()` (half shaah zmanit after chatzot) |
| ROZmanimCalendar | `getMinchaGedolaGreaterThan30()` -- returns later of 30 fixed minutes after chatzot or standard half-shaah-zmanit after chatzot |
| ZmanimFactory key | `"MinchaGedola"` |
| Hebcal | `minchaGedola` |
| Calculation | Maximum of: (a) chatzot + 30 fixed minutes, (b) chatzot + 0.5 shaos zmaniyos. Ensures minimum 30 real minutes after chatzot. |
| Halachic source | Shulchan Aruch OC 233:1. Mincha can be prayed from half a shaah zmanit after chatzot, but never less than 30 real minutes. R' Ovadia's Ohr HaChaim uses this approach. |

---

## 14. Mincha Ketana 16.1 Degrees

| Field | Value |
|-------|-------|
| KosherJava | `getMinchaKetana16Point1Degrees()` -- `getMinchaKetana(getAlos16Point1Degrees(), getTzais16Point1Degrees(), true)` |
| ROZmanimCalendar | N/A (ROZmanimCalendar uses standard GRA `getMinchaKetana()`) |
| Hebcal | N/A |
| Calculation | 9.5 shaos zmaniyos after alos 16.1 degrees. Day = alos 16.1 degrees to tzais 16.1 degrees. `alos16.1 + (shaahZmanis16.1 * 9.5)` |
| Halachic source | MGA-based: day defined by sun at 16.1 degrees below horizon (which corresponds to 72 fixed minutes at Jerusalem equinox). Rambam holds mincha ketana is 9.5 hours into the day. |

---

## 15. Mincha Ketana 72 Minutes

| Field | Value |
|-------|-------|
| KosherJava | `getMinchaKetana72Minutes()` -- `getMinchaKetana(getAlos72(), getTzais72(), true)` |
| ROZmanimCalendar | N/A (uses standard `getMinchaKetana()`) |
| Hebcal | N/A |
| Calculation | 9.5 shaos zmaniyos after alos 72 fixed minutes. Day = alos 72 to tzais 72. `alos72 + (shaahZmanis72 * 9.5)` |
| Halachic source | MGA with fixed 72-minute day. Based on Rishonim that 4-mil walk = 72 fixed minutes regardless of season. |

---

## 16. Plag HaMincha Yalkut Yosef

| Field | Value |
|-------|-------|
| KosherJava | N/A (no direct Yalkut Yosef plag method) |
| ROZmanimCalendar | `getPlagHaminchaYalkutYosef()` -- calculates as tzait minus 1 hour 15 zmaniyot minutes: `getTzeit() - (shaahZmanit + 15 * dakahZmanit)` |
| ZmanimFactory key | `"PlagHaMinchaYY"` |
| Hebcal | N/A |
| Calculation | 1.25 shaos zmaniyos (GRA, sunrise-to-sunset based) BEFORE tzeit hacochavim (13.5 zmaniyot minutes after sunset). `tzeit - (shaahZmanisGra * 1.25)` |
| Halachic source | Yalkut Yosef (R' Yitzchak Yosef): Plag is calculated from tzeit, not from sunset. This matches the Ohr HaChaim calendar. Distinguished from the Halacha Berurah plag which uses standard sunrise-to-sunset calculation. |

---

## 17. Sea Level Sunset (Shkia Mishor)

| Field | Value |
|-------|-------|
| KosherJava | `getSeaLevelSunset()` (in AstronomicalCalendar) |
| ROZmanimCalendar | `getSeaLevelSunset()` (inherited) |
| Hebcal | `sunset` |
| Calculation | Sunset at sea level (0m elevation), sun center at geometric zenith 90.833 degrees (with refraction). |
| Halachic source | Standard astronomical sunset without elevation adjustment. |

---

## 18. Elevation Adjusted Sunset

| Field | Value |
|-------|-------|
| KosherJava | `getSunset()` (when `isUseElevation() == true`) or `getElevationAdjustedSunset()` |
| ROZmanimCalendar | `getElevationAdjustedSunset()` |
| ZmanimFactory key | Used for Tisha B'Av start time: `"Shkia"` with `getElevationAdjustedSunset()` |
| Hebcal | N/A |
| Calculation | Sunset adjusted for geographic elevation. Higher elevation = later sunset. |
| Halachic source | Ohr HaChaim calendar uses elevation-adjusted sunset for all zmanim. |

---

## 19. General Sunset

| Field | Value |
|-------|-------|
| KosherJava | `getSunset()` -- returns elevation-adjusted or sea-level based on `isUseElevation()` setting |
| ROZmanimCalendar | `getSunset()` (inherited) |
| ZmanimFactory key | `"Shkia"` |
| Hebcal | `sunset` |
| Calculation | Depends on elevation setting. If elevation enabled: elevation-adjusted. Otherwise: sea level. |
| Halachic source | General sunset time used as the basis for many zmanim. |

---

## 20. Bein HaShmashos Yereim 13.5 Minutes

| Field | Value |
|-------|-------|
| KosherJava | `getBainHashmashosYereim13Point5Minutes()` -- `getTimeOffset(getElevationAdjustedSunset(), -13.5 * MINUTE_MILLIS)` |
| KosherJava (degrees) | `getBainHashmashosYereim2Point1Degrees()` -- degree-based equivalent: `getSunsetOffsetByDegrees(ZENITH_MINUS_2_POINT_1)` |
| ROZmanimCalendar | N/A |
| Hebcal | `beinHaShmashos` (likely this calculation) |
| Calculation | 13.5 fixed minutes BEFORE sunset. (Note: before sunset, not after.) Degree equivalent: sun at 2.1 degrees above horizon. |
| Halachic source | Yereim (Rabbi Eliezer of Metz): Bein hashmashos starts 3/4 of a mil before sunset. With 18-min mil: 3/4 * 18 = 13.5 minutes. Tzais/nightfall starts AT sunset per the Yereim. |

---

## 21. Tzais Geonim 3.8 Degrees

| Field | Value |
|-------|-------|
| KosherJava | `getTzaisGeonim3Point8Degrees()` -- `getSunsetOffsetByDegrees(ZENITH_3_POINT_8)` where `ZENITH_3_POINT_8 = 90 + 3.8` |
| ROZmanimCalendar | N/A directly (but Amudei Horaah tzeit uses equinox percentage from 3.7 degrees) |
| Hebcal | N/A |
| Calculation | Sun at 3.8 degrees below horizon after sunset. |
| Halachic source | Geonim: tzais is the time to walk 3/4 mil at 18 min/mil = 13.5 minutes after sunset. The sun is 3.8 degrees below horizon at this time in Jerusalem around the equinox/equilux. |

---

## 22. Tzais Geonim 4.61 Degrees

| Field | Value |
|-------|-------|
| KosherJava | `getTzaisGeonim4Point61Degrees()` -- `getSunsetOffsetByDegrees(ZENITH_4_POINT_61)` where `ZENITH_4_POINT_61 = 90 + 4.61` |
| ROZmanimCalendar | N/A |
| Hebcal | N/A |
| Calculation | Sun at 4.61 degrees below horizon after sunset. |
| Halachic source | Geonim: 18 minutes after sunset (3/4 of a 24-minute mil). The sun is at 4.61 degrees below horizon at this time in Jerusalem around the equinox/equilux. |

---

## 23. Tzais Geonim 4.8 Degrees

| Field | Value |
|-------|-------|
| KosherJava | `getTzaisGeonim4Point8Degrees()` -- `getSunsetOffsetByDegrees(ZENITH_4_POINT_8)` where `ZENITH_4_POINT_8 = 90 + 4.8` |
| ROZmanimCalendar | N/A |
| Hebcal | N/A |
| Calculation | Sun at 4.8 degrees below horizon after sunset. |
| Halachic source | Geonim. Slightly later than the 4.61-degree calculation. |

---

## 24. Tzais Geonim 5.95 Degrees

| Field | Value |
|-------|-------|
| KosherJava | `getTzaisGeonim5Point95Degrees()` -- `getSunsetOffsetByDegrees(ZENITH_5_POINT_95)` where `ZENITH_5_POINT_95 = 90 + 5.95` |
| ROZmanimCalendar | N/A |
| Hebcal | N/A |
| Calculation | Sun at 5.95 degrees below horizon after sunset. |
| Halachic source | Based on sun position 24 minutes after sunset in Jerusalem around the equinox/equilux, which calculates to 5.95 degrees. |

---

## 25. Tzais Geonim 7.67 Degrees

| Field | Value |
|-------|-------|
| KosherJava | `getTzaisGeonim7Point67Degrees()` -- `getSunsetOffsetByDegrees(ZENITH_7_POINT_67)` where `ZENITH_7_POINT_67 = 90 + 7.67` |
| ROZmanimCalendar | N/A |
| Hebcal | N/A |
| Calculation | Sun at 7.67 degrees below horizon after sunset. |
| Halachic source | Geonim: 45 minutes after sunset during summer solstice in New York. Igros Moshe Even HaEzer 4:4. Also R' Shmuel Kamenetsky agreed to this degree-based calculation (presented by R' Yaakov Shakow). R' Simcha Bunim Cohen's "The Radiance of Shabbos". |

---

## 26. Tzais Geonim 8.5 Degrees

| Field | Value |
|-------|-------|
| KosherJava | `getTzaisGeonim8Point5Degrees()` -- `getSunsetOffsetByDegrees(ZENITH_8_POINT_5)` where `ZENITH_8_POINT_5 = 90 + 8.5` |
| KosherJava (base) | Also the default `getTzais()` in `ZmanimCalendar` |
| ROZmanimCalendar | N/A |
| Hebcal | `tzeit85deg` |
| Calculation | Sun at 8.5 degrees below horizon after sunset. |
| Halachic source | Rabbi Meir Posen in Ohr Meir: calculated that 3 small stars are visible at this time (which is later than the required 3 medium stars). This is the default tzais in KosherJava. |

---

## 27. Tzais Geonim 9.75 Degrees

| Field | Value |
|-------|-------|
| KosherJava | `getTzaisGeonim9Point75Degrees()` -- `getSunsetOffsetByDegrees(ZENITH_9_POINT_75)` where `ZENITH_9_POINT_75 = 90 + 9.75` |
| ROZmanimCalendar | N/A |
| Hebcal | N/A |
| Calculation | Sun at 9.75 degrees below horizon after sunset. |
| Halachic source | Geonim: 60 minutes after sunset around the equinox/equilux. Opinion of R' Eliyahu Henkin. Also R' Shmuel Kamenetsky agreed (presented by R' Yaakov Shakow). |

---

## 28. Tzais 13.5 Zmaniyot Minutes (R' Ovadia)

| Field | Value |
|-------|-------|
| KosherJava | N/A (no direct 13.5 zmaniyot method) |
| ROZmanimCalendar | `getTzeit()` -- when `amudehHoraah == false`: `getZmanisBasedOffset(0.225)` (13.5/60 = 0.225 shaos). When `amudehHoraah == true`: equinox percentage from 3.7 degrees |
| ZmanimFactory key | `"TzeitHacochavim"` |
| Hebcal | N/A |
| Calculation | 13.5 zmaniyot minutes after elevation-adjusted sunset. `sunset + (shaahZmanisGra * 0.225)`. For Amudei Horaah: uses equinox-based percentage from 3.7 degrees below horizon. |
| Halachic source | Geonim: 3/4 mil after sunset (18 min/mil, so 13.5 min). R' Ovadia Yosef ZT"L's Ohr HaChaim calendar uses zmaniyot minutes (proportional to day length) rather than fixed minutes. |

---

## 29. Tzais LeChumra 20 Zmaniyot Minutes

| Field | Value |
|-------|-------|
| KosherJava | N/A |
| ROZmanimCalendar | `getTzeitLChumra()` -- when `amudehHoraah == false`: `sunset + 20 * (shaahZmanisGra / 60)`. When `amudehHoraah == true`: equinox percentage from 5.075 degrees |
| ZmanimFactory key | `"TzeitHacochavimLChumra"` |
| Hebcal | N/A |
| Calculation | 20 zmaniyot minutes after elevation-adjusted sunset. `sunset + (shaahZmanisGra / 60 * 20)`. For Amudei Horaah: equinox-based percentage from 5.075 degrees. |
| Halachic source | Stringent nightfall time per Ohr HaChaim calendar. Used for end of fasts, candle lighting on Yom Tov going into Yom Tov, etc. |

---

## 30. Tzais Shabbat Amudei Horaah

| Field | Value |
|-------|-------|
| KosherJava | N/A |
| ROZmanimCalendar | `getTzeitShabbatAmudeiHoraah()` -- sun at 7.165 degrees below horizon, with hard floor of 20 fixed minutes after sunset, and ceiling of solar midnight |
| ZmanimFactory key | `"ShabbatEnd"` (when `isUseAmudehHoraah` is true) |
| Hebcal | N/A |
| Calculation | `getSunsetOffsetByDegrees(90 + 7.165)`, but never earlier than 20 fixed minutes after sunset, and never later than solar midnight. |
| Halachic source | Rabbi Dahan (Amudei Horaah calendar): calculated the degree at which the sun is always 30+ minutes after sunset throughout the year at the northernmost point of Israel, per R' Ovadia Yosef's ruling that Shabbat ends 30 minutes after sunset in Israel. 7.165 degrees achieves this. 20-minute minimum as instructed by Rabbi Dahan. |

---

## 31. Tzais Shabbat Amudei Horaah Under 40

| Field | Value |
|-------|-------|
| KosherJava | N/A |
| ROZmanimCalendar | `getTzeitShabbatAmudeiHoraahLesserThan40()` -- returns the EARLIER of `getTzaisAteretTorah()` (40 min) and `getTzeitShabbatAmudeiHoraah()` (7.165 degrees) |
| ZmanimFactory key | `"ShabbatEnd"` (when `overrideAHEndShabbatTime` preference set to option "3") |
| Hebcal | N/A |
| Calculation | `min(getTzaisAteretTorah(), getTzeitShabbatAmudeiHoraah())`. Caps Amudei Horaah time at 40 fixed minutes. |
| Halachic source | Created by the app developer (not from Rabbi Dahan's calendar). Rationale: the degree-based Amudei Horaah time is used l'kula but we don't need to be stringent beyond 40 minutes, per Rabbi Meir Gavriel Elbaz. |

---

## 32. Tzais Rabbeinu Tam 72 Zmaniyot Amudei Horaah LeKula

| Field | Value |
|-------|-------|
| KosherJava | N/A |
| ROZmanimCalendar | `getTzais72ZmanisAmudeiHoraahLkulah()` -- returns the EARLIER of `getTzais72()` (72 fixed minutes) and `getTzais72Zmanis()` (72 zmaniyot minutes) |
| ZmanimFactory key | `"RT"` (for Rabbeinu Tam, when Amudei Horaah mode is active) |
| Hebcal | N/A |
| Calculation | `min(getTzais72(), getTzais72Zmanis())`. Takes the lenient (earlier) of fixed 72 minutes and proportional 72 zmaniyot minutes. When `amudehHoraah == true`, `getTzais72Zmanis()` uses equinox-based percentage from 16.04 degrees. |
| Halachic source | Rabbeinu Tam: nightfall is 72 minutes after sunset (4 mil). Amudei Horaah calendar prints the earlier of fixed vs. zmaniyot as a leniency. R' Ovadia himself was machmir to always use zmaniyot, but Rabbi Dahan and many poskim (including R' Ovadia's sons) hold one can be lenient in northern locations. |

---

## 33. Shaah Zmanit GRA

| Field | Value |
|-------|-------|
| KosherJava | `getShaahZmanisGra()` -- `getTemporalHour(getElevationAdjustedSunrise(), getElevationAdjustedSunset())` |
| ROZmanimCalendar | `getShaahZmanisGra()` (inherited from ZmanimCalendar) |
| Hebcal | N/A (not exposed as a separate field) |
| Calculation | `(sunset - sunrise) / 12`. Day = sunrise to sunset divided into 12 equal hours. Returns milliseconds. |
| Halachic source | Vilna Gaon (GRA): The halachic day runs from sunrise to sunset. Each of the 12 hours is a shaah zmanit. |

---

## 34. Shaah Zmanit MGA

| Field | Value |
|-------|-------|
| KosherJava | `getShaahZmanisMGA()` -- `getTemporalHour(getAlos72(), getTzais72())`. Also `getShaahZmanis72Minutes()` = `getTemporalHour(getAlos72(), getTzais72())` |
| KosherJava (zmaniyot) | `getShaahZmanis72MinutesZmanis()` -- `getTemporalHour(getAlos72Zmanis(), getTzais72Zmanis())` |
| ROZmanimCalendar | `getShaahZmanis72MinutesZmanis()` -- `getTemporalHour(getAlotHashachar(), getTzais72Zmanis())` |
| Hebcal | N/A |
| Calculation | `(tzais72 - alos72) / 12`. Day = alos to tzais (72 min before sunrise to 72 min after sunset), divided into 12 hours. |
| Halachic source | Magen Avraham (MGA): The halachic day starts at alos (dawn, 72 min before sunrise) and ends at tzais (nightfall, 72 min after sunset). |

---

## 35. Earliest Kiddush Levana 3 Days

| Field | Value |
|-------|-------|
| KosherJava | `getTchilasZmanKidushLevana3Days()` (no alos/tzais adjustment) or `getTchilasZmanKidushLevana3Days(alos, tzais)` (adjusted to nighttime) |
| ROZmanimCalendar | N/A (uses KosherJava's JewishCalendar) |
| Hebcal | N/A |
| Calculation | Exactly 3 days (72 hours) after the molad. If it falls during daytime, returns the following tzais. Based on `JewishCalendar.getTchilasZmanKidushLevana3Days()`. |
| Halachic source | Rabbeinu Yonah: Kiddush Levana may be recited 3 days after the molad. |

---

## 36. Earliest Kiddush Levana 7 Days

| Field | Value |
|-------|-------|
| KosherJava | `getTchilasZmanKidushLevana7Days()` or `getTchilasZmanKidushLevana7Days(alos, tzais)` |
| ROZmanimCalendar | N/A |
| Hebcal | N/A |
| Calculation | Exactly 7 days after the molad. If it falls during daytime, returns the following tzais. Based on `JewishCalendar.getTchilasZmanKidushLevana7Days()`. |
| Halachic source | Majority opinion (Shulchan Aruch, Rema): Kiddush Levana should not be said until 7 days after the molad. |

---

## 37. Latest Kiddush Levana 15 Days

| Field | Value |
|-------|-------|
| KosherJava | `getSofZmanKidushLevana15Days()` or `getSofZmanKidushLevana15Days(alos, tzais)` |
| KosherJava (alt) | `getSofZmanKidushLevanaBetweenMoldos()` -- halfway between this molad and the next |
| ROZmanimCalendar | N/A |
| Hebcal | N/A |
| Calculation | 15 days after the molad (the start of the molad, which is the start of the lunar month). Based on `JewishCalendar.getSofZmanKidushLevana15Days()`. If during daytime, returns preceding alos. |
| Halachic source | Latest time for Kiddush Levana is the 15th of the month (full moon). Some opinions use halfway between two consecutive molados instead (getSofZmanKidushLevanaBetweenMoldos). |

---

## 38. Candle Lighting

| Field | Value |
|-------|-------|
| KosherJava (base) | `getCandleLighting()` in ZmanimCalendar -- `getTimeOffset(getSeaLevelSunset(), -getCandleLightingOffset() * MINUTE_MILLIS)`. Default offset: 18 minutes. Uses SEA LEVEL sunset. |
| ROZmanimCalendar | `getCandleLighting()` (overrides base) -- `getTimeOffset(getElevationAdjustedSunset(), -getCandleLightingOffset() * MILLISECONDS_PER_MINUTE)`. Uses ELEVATION ADJUSTED sunset. |
| ZmanimFactory key | `"CandleLighting"` with offset shown in label: `"CandleLighting" + " (" + offset + ")"` |
| Hebcal | Returned in Shabbat API as category `"candles"` with time in `date` field. Default offset: 18 min (40 min in Jerusalem). |
| Calculation | Sunset minus configurable offset (typically 18 or 20 minutes). KosherJava base uses sea level sunset. ROZmanimCalendar overrides to use elevation-adjusted sunset. |
| Halachic source | Shulchan Aruch OC 263:4. Ashkenazi custom: 18 minutes before sunset. Sephardi custom varies. Jerusalem custom: 40 minutes. Configurable via `setCandleLightingOffset()`. |

---

## Summary: Hebcal API Fields

All available fields from `https://www.hebcal.com/zmanim?cfg=json`:

| Hebcal Field | Description |
|--------------|-------------|
| `chatzotNight` | Solar midnight |
| `alotHaShachar` | Dawn (16.1 degrees) |
| `misheyakir` | Earliest talit/tefilin |
| `misheyakirMachmir` | Misheyakir stringent |
| `dawn` | Civil dawn |
| `sunrise` | Sea level sunrise |
| `sofZmanShmaMGA19Point8` | Shma MGA 19.8 degrees |
| `sofZmanShmaMGA16Point1` | Shma MGA 16.1 degrees |
| `sofZmanShmaMGA` | Shma MGA 72 fixed minutes |
| `sofZmanShma` | Shma GRA |
| `sofZmanTfillaMGA19Point8` | Tfila MGA 19.8 degrees |
| `sofZmanTfillaMGA16Point1` | Tfila MGA 16.1 degrees |
| `sofZmanTfillaMGA` | Tfila MGA 72 fixed minutes |
| `sofZmanTfilla` | Tfila GRA |
| `chatzot` | Solar noon |
| `minchaGedola` | Earliest mincha |
| `minchaGedolaMGA` | Earliest mincha MGA |
| `minchaKetana` | Mincha ketana |
| `minchaKetanaMGA` | Mincha ketana MGA |
| `plagHaMincha` | Plag HaMincha |
| `sunset` | Sea level sunset |
| `beinHaShmashos` | Bein HaShmashos |
| `dusk` | Civil dusk |
| `tzeit7083deg` | Tzais 7.083 degrees |
| `tzeit85deg` | Tzais 8.5 degrees |
| `tzeit42min` | Tzais 42 minutes |
| `tzeit50min` | Tzais 50 minutes |
| `tzeit72min` | Tzais 72 minutes |

---

## Key Amudei Horaah Calculation Notes

When `ROZmanimCalendar.isUseAmudehHoraah()` is true, several zmanim use an equinox-based proportional calculation:

1. Calculate equinox day (March 17) sunrise-to-sunset shaah zmanit
2. Find what percentage of shaah zmanit corresponds to a given degree below horizon on the equinox
3. Apply that percentage to the current day's shaah zmanit

This method adapts degree-based zmanim to work proportionally at any latitude, following Rabbi Dahan's approach in the Amudei Horaah calendar. The degrees used:
- **Alos**: 16.04 degrees (corresponding to 72 min at equinox)
- **Tzeit**: 3.7 degrees (corresponding to 13.5 min at equinox)
- **Tzeit LeChumra**: 5.075 degrees (corresponding to 20 min at equinox)
- **Tzais 72 zmaniyot**: 16.04 degrees (corresponding to 72 min at equinox)
