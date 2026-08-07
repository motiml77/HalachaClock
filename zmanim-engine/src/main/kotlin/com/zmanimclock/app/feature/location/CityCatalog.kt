package com.zmanimclock.app.feature.location

/**
 * One place in the bundled city list.
 *
 * NOTE ON ELEVATION: carried for the ChaiTables metro lookup only. It is NOT
 * fed into the astronomical calculation — see EngineLocation and the mishor
 * doctrine in MaranZmanimEngine.
 */
data class CityInfo(
    val id: String,
    val nameHebrew: String,
    val nameEnglish: String,
    val country: String,
    val latitude: Double,
    val longitude: Double,
    val elevation: Double,
    val timeZoneId: String,
    val region: String = "",
)

/**
 * The bundled city list, shared by every platform.
 *
 * WHY THIS LIVES HERE AND IS PARSED BY HAND
 * The coordinates ARE zmanim input: two copies of this file that drift apart
 * would give the same user different times on their phone and their computer,
 * which is the exact failure the shared-engine module exists to prevent. So
 * there is one copy, in this module's resources.
 *
 * The parser is hand-rolled rather than pulled from a library because the two
 * platforms disagree about which library is available: org.json ships with
 * Android but not with a desktop JVM, and adding the Maven org.json to a
 * module Android also consumes risks a duplicate-class conflict. The file is a
 * flat array of flat objects with no nesting and no escapes, so a scanner for
 * exactly that shape is smaller and safer than the dependency.
 */
object CityCatalog {

    private const val RESOURCE = "/cities.json"

    val cities: List<CityInfo> by lazy { parse(readResource()) }

    fun byId(id: String): CityInfo? = cities.firstOrNull { it.id == id }

    /** Substring match on either name, for a search box. */
    fun search(query: String): List<CityInfo> {
        val q = query.trim()
        if (q.isEmpty()) return cities
        return cities.filter {
            it.nameHebrew.contains(q, ignoreCase = true) ||
                it.nameEnglish.contains(q, ignoreCase = true)
        }
    }

    private fun readResource(): String =
        CityCatalog::class.java.getResourceAsStream(RESOURCE)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("cities.json missing from the classpath")

    /** Splits the flat array into objects, then each object into fields. */
    internal fun parse(json: String): List<CityInfo> {
        val out = ArrayList<CityInfo>(512)
        var i = 0
        while (true) {
            val start = json.indexOf('{', i)
            if (start < 0) break
            val end = json.indexOf('}', start)
            if (end < 0) break
            val fields = readFields(json.substring(start + 1, end))
            val id = fields["id"]
            val lat = fields["latitude"]?.toDoubleOrNull()
            val lng = fields["longitude"]?.toDoubleOrNull()
            if (id != null && lat != null && lng != null) {
                out.add(
                    CityInfo(
                        id = id,
                        nameHebrew = fields["nameHebrew"] ?: id,
                        nameEnglish = fields["nameEnglish"] ?: id,
                        country = fields["country"] ?: "",
                        latitude = lat,
                        longitude = lng,
                        elevation = fields["elevation"]?.toDoubleOrNull() ?: 0.0,
                        timeZoneId = fields["timeZoneId"] ?: "Asia/Jerusalem",
                        region = fields["region"] ?: "",
                    ),
                )
            }
            i = end + 1
        }
        return out
    }

    /** `"key": value` pairs, where a value is either quoted or a bare number. */
    private fun readFields(body: String): Map<String, String> {
        val map = HashMap<String, String>(12)
        var i = 0
        while (i < body.length) {
            val keyStart = body.indexOf('"', i)
            if (keyStart < 0) break
            val keyEnd = body.indexOf('"', keyStart + 1)
            if (keyEnd < 0) break
            val key = body.substring(keyStart + 1, keyEnd)
            val colon = body.indexOf(':', keyEnd + 1)
            if (colon < 0) break
            var v = colon + 1
            while (v < body.length && body[v] == ' ') v++
            if (v >= body.length) break
            if (body[v] == '"') {
                val close = body.indexOf('"', v + 1)
                if (close < 0) break
                map[key] = body.substring(v + 1, close)
                i = close + 1
            } else {
                var e = v
                while (e < body.length && body[e] != ',') e++
                map[key] = body.substring(v, e).trim()
                i = e + 1
            }
        }
        return map
    }
}
