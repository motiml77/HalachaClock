package com.zmanimclock.app.feature.widget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zmanimclock.app.feature.zmanim.model.ZmanKind

/**
 * The zman picker, bucketed by time of day.
 *
 * A single flat list of all 19 kinds is genuinely hard to scan: several
 * entries differ only by a shita suffix, and the halachic names do not sort
 * into anything a reader can predict. Grouping by when they occur matches how
 * someone thinks about their own day and makes the one they want findable
 * without reading every row.
 *
 * Every ZmanKind appears exactly once — pinned by a test, so a kind added
 * later cannot silently vanish from the picker.
 */
internal val ZMAN_GROUPS: List<Pair<String, List<ZmanKind>>> = listOf(
    "לילה ובוקר מוקדם" to listOf(
        ZmanKind.CHATZOT_LAYLA,
        ZmanKind.ALOT_HASHACHAR,
        ZmanKind.MISHEYAKIR,
        // Mishor before visible: the terrain pushes the visible netz LATER, so
        // this is the order the widget itself renders them in.
        ZmanKind.HANETZ_MISHOR,
        ZmanKind.HANETZ,
    ),
    "זמני תפילה" to listOf(
        ZmanKind.SOF_ZMAN_SHMA_MGA_72_ZMANIYOT,
        ZmanKind.SOF_ZMAN_SHMA_MGA_16_1_DEG,
        ZmanKind.SOF_ZMAN_SHMA_GRA,
        ZmanKind.SOF_ZMAN_TFILA_MGA_72_ZMANIYOT,
        ZmanKind.SOF_ZMAN_TFILA_MGA_16_1_DEG,
        ZmanKind.SOF_ZMAN_TFILA_GRA,
    ),
    "צהריים ואחר הצהריים" to listOf(
        ZmanKind.CHATZOT,
        ZmanKind.MINCHA_GEDOLA,
        ZmanKind.MINCHA_KETANA,
        // GRA before the luach's: the gap between them is exactly the 13.5
        // zmaniyot minutes from shkia to tzeit, so the GRA one is always
        // earlier and always renders first.
        ZmanKind.PLAG_HAMINCHA_GRA,
        ZmanKind.PLAG_HAMINCHA,
    ),
    "שקיעה וצאת הכוכבים" to listOf(
        ZmanKind.SHKIA,
        ZmanKind.TZEIT_HAKOCHAVIM,
        ZmanKind.TZEIT_LECHUMRA,
        ZmanKind.TZEIT_RABBEINU_TAM,
    ),
    "שבת וחג" to listOf(
        ZmanKind.CANDLE_LIGHTING,
        ZmanKind.TZEIT_SHABBAT,
    ),
)

/**
 * Illustrative times for the mock, in the order the real widget lists them.
 *
 * Illustrative, but not arbitrary: they are internally consistent with a netz
 * of 05:57 and a shkia of 19:32, so every derived row really is what that day
 * would produce. Someone who knows these zmanim will read the mock and check
 * it against the ones next to it, and a set that does not hold together reads
 * as a bug in the times themselves.
 *
 * Every kind in [ZMAN_GROUPS] must have an entry, and each group must stay in
 * time order — both pinned by WidgetConfigGroupsTest, because the fallback
 * here is a literal "--:--" appearing in the picker.
 */
internal val SAMPLE_TIMES = mapOf(
    ZmanKind.CHATZOT_LAYLA to "00:45",
    ZmanKind.ALOT_HASHACHAR to "04:33",
    ZmanKind.MISHEYAKIR to "04:47",
    ZmanKind.HANETZ_MISHOR to "05:54",
    ZmanKind.HANETZ to "05:57",
    ZmanKind.SOF_ZMAN_SHMA_MGA_72_ZMANIYOT to "08:39",
    ZmanKind.SOF_ZMAN_SHMA_MGA_16_1_DEG to "08:39",
    ZmanKind.SOF_ZMAN_SHMA_GRA to "09:21",
    ZmanKind.SOF_ZMAN_TFILA_MGA_72_ZMANIYOT to "10:01",
    ZmanKind.SOF_ZMAN_TFILA_MGA_16_1_DEG to "10:01",
    ZmanKind.SOF_ZMAN_TFILA_GRA to "10:28",
    ZmanKind.CHATZOT to "12:45",
    ZmanKind.MINCHA_GEDOLA to "13:20",
    ZmanKind.MINCHA_KETANA to "16:42",
    // shaah = (19:32 - 05:57) / 12 = 67:55, so 1¼ of them is 1:25.
    // GRA:   shkia 19:32 - 1:25 = 18:07
    // luach: tzeit 19:47 - 1:25 = 18:22
    ZmanKind.PLAG_HAMINCHA_GRA to "18:07",
    ZmanKind.PLAG_HAMINCHA to "18:22",
    ZmanKind.SHKIA to "19:32",
    ZmanKind.TZEIT_HAKOCHAVIM to "19:47",
    ZmanKind.TZEIT_LECHUMRA to "19:58",
    ZmanKind.TZEIT_RABBEINU_TAM to "20:44",
    ZmanKind.CANDLE_LIGHTING to "19:12",
    ZmanKind.TZEIT_SHABBAT to "20:12",
)

private val Navy = Color(0xFF123A8B)
private val Gold = Color(0xFFF5C518)
private val SoftBlue = Color(0xFF9FB3D9)
private val BodyBlue = Color(0xFFEAF0FF)
private val Hairline = Color(0x33FFFFFF)

/**
 * A live mock of the widget, shown at the top of the config screen and
 * updating as the user toggles.
 *
 * Deliberately a Compose approximation rather than the real RemoteViews: the
 * point is to answer "what will this look like" WHILE choosing. Without it
 * the screen is a list of abstract switches whose effect only becomes visible
 * after saving and returning to the home screen — and before the reconfigure
 * flag existed, getting back here meant deleting the widget and starting over.
 *
 * The times are illustrative and labelled as such, so nobody mistakes the
 * mock for real zmanim.
 */
@Composable
internal fun WidgetMockPreview(
    showDate: Boolean,
    showNext: Boolean,
    zmanim: List<String>,
    showAlarms: Boolean,
    alarmCount: Int,
) {
    Column {
        Text(
            "כך זה ייראה",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Navy),
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (showDate) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "כ״ב אב תשפ״ו",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        Text("ירושלים", color = SoftBlue, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(2.dp))
                    Text("5.8.2026", color = SoftBlue, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                }

                if (showNext) {
                    Text(
                        "הזמן הבא: מנחה קטנה",
                        color = Gold,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "16:42",
                            color = Color.White,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "בעוד 3:54 שע׳",
                            color = BodyBlue,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }

                val kinds = zmanim.mapNotNull { ZmanKind.fromNameOrNull(it) }
                    .take(WidgetPrefs.MAX_ZMANIM)
                if (kinds.isNotEmpty()) {
                    HorizontalDivider(color = Hairline)
                    Spacer(Modifier.height(6.dp))
                    kinds.forEach { kind ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(
                                kind.hebrewName,
                                color = BodyBlue,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                SAMPLE_TIMES[kind] ?: "--:--",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                if (showAlarms) {
                    Spacer(Modifier.height(6.dp))
                    HorizontalDivider(color = Hairline)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "שעונים מעוררים",
                        color = Gold,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    repeat(alarmCount.coerceAtMost(2)) { i ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                            Text(
                                if (i == 0) "השכמה" else "הנץ החמה",
                                color = BodyBlue,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                if (i == 0) "06:15" else "05:57",
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
        Text(
            "השעות להמחשה בלבד",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
