package com.zmanimclock.app.feature.location

import java.time.ZoneId

/**
 * Which holiday calendar applies where: Eretz Yisrael keeps ONE day of Yom Tov,
 * everywhere else keeps two (יום טוב שני של גלויות).
 *
 * Decided from the time zone because every consumer already carries one, and
 * the two sets never overlap — every Israeli locality in the city list, the
 * settlements included, is on Asia/Jerusalem, and no city abroad is. A GPS
 * user follows the device's zone the same way.
 *
 * ISRAEL MUST STAY EXACTLY AS IT WAS. Every function that takes this flag
 * defaults to — or resolves to — `true` for an Israeli zone, which is what was
 * hardcoded before; CityCatalogTest pins that every IL city resolves to true.
 */
object HolidayLocale {

    private val ISRAEL_ZONES = setOf("Asia/Jerusalem", "Asia/Tel_Aviv", "Israel")

    fun inIsrael(timeZoneId: String): Boolean = timeZoneId in ISRAEL_ZONES

    fun inIsrael(zone: ZoneId): Boolean = inIsrael(zone.id)
}
