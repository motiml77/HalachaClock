package com.zmanimclock.app.feature.widget

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.zmanimclock.app.MainActivity
import com.zmanimclock.app.feature.zmanim.data.ZmanimCalculator
import com.zmanimclock.app.feature.zmanim.data.model.DayZmanim
import com.zmanimclock.app.feature.zmanim.data.model.ZmanId
import com.zmanimclock.app.feature.zmanim.data.model.ZmanTime
import com.zmanimclock.app.location.LocationProvider
import com.zmanimclock.app.location.model.AppGeoLocation
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class ZmanimWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val zmanimCalculator = ZmanimCalculator()
        val locationProvider = LocationProvider(context.applicationContext)

        val location = locationProvider.getCurrentLocation() ?: getDefaultLocation()
        val dayZmanim = zmanimCalculator.calculateZmanim(location)
        val upcomingZmanim = getUpcomingZmanim(dayZmanim, 3)

        provideContent {
            GlanceTheme {
                WidgetContent(
                    hebrewDate = dayZmanim.hebrewDate,
                    locationName = dayZmanim.locationName,
                    upcomingZmanim = upcomingZmanim,
                    context = context,
                    timeZone = location.timeZone,
                )
            }
        }
    }

    private fun getUpcomingZmanim(dayZmanim: DayZmanim, count: Int): List<ZmanTime> {
        val now = Date()
        // Filter to only zmanim that have a time (not shaah zmanit) and are in the future
        // Also exclude zmanim that are multi-day (kiddush levana) for the widget
        val excludedCategories = setOf(
            ZmanId.SHAAH_ZMANIT_GRA,
            ZmanId.SHAAH_ZMANIT_MGA,
            ZmanId.KIDDUSH_LEVANA_3,
            ZmanId.KIDDUSH_LEVANA_7,
            ZmanId.KIDDUSH_LEVANA_15,
            ZmanId.HANETZ_VISIBLE,
        )

        return dayZmanim.zmanim
            .filter { zman ->
                zman.time != null &&
                    zman.time.after(now) &&
                    zman.id !in excludedCategories
            }
            .sortedBy { it.time!!.time }
            .take(count)
    }

    private fun getDefaultLocation(): AppGeoLocation {
        return AppGeoLocation(
            cityNameHebrew = "\u05D9\u05E8\u05D5\u05E9\u05DC\u05D9\u05DD",
            cityNameEnglish = "Jerusalem",
            latitude = 31.778,
            longitude = 35.235,
            elevation = 800.0,
            timeZone = TimeZone.getTimeZone("Asia/Jerusalem"),
        )
    }
}

@Composable
private fun WidgetContent(
    hebrewDate: String,
    locationName: String,
    upcomingZmanim: List<ZmanTime>,
    context: Context,
    timeZone: TimeZone,
) {
    val surfaceColor = GlanceTheme.colors.widgetBackground
    val onSurfaceColor = GlanceTheme.colors.onSurface
    val primaryColor = GlanceTheme.colors.primary

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(surfaceColor)
            .clickable(actionStartActivity<MainActivity>())
            .padding(12.dp),
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Hebrew date header
            Text(
                text = hebrewDate,
                style = TextStyle(
                    color = primaryColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
                modifier = GlanceModifier.fillMaxWidth(),
            )

            Spacer(modifier = GlanceModifier.height(2.dp))

            // Location name
            Text(
                text = locationName,
                style = TextStyle(
                    color = onSurfaceColor,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                ),
                modifier = GlanceModifier.fillMaxWidth(),
            )

            Spacer(modifier = GlanceModifier.height(6.dp))

            // Zmanim rows
            if (upcomingZmanim.isEmpty()) {
                Text(
                    text = "\u05D0\u05D9\u05DF \u05D6\u05DE\u05E0\u05D9\u05DD \u05E0\u05D5\u05E1\u05E4\u05D9\u05DD \u05DC\u05D4\u05D9\u05D5\u05DD",
                    style = TextStyle(
                        color = onSurfaceColor,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    ),
                    modifier = GlanceModifier.fillMaxWidth(),
                )
            } else {
                upcomingZmanim.forEachIndexed { index, zman ->
                    ZmanRow(
                        zman = zman,
                        isFirst = index == 0,
                        context = context,
                        timeZone = timeZone,
                        primaryColor = primaryColor,
                        onSurfaceColor = onSurfaceColor,
                    )
                    if (index < upcomingZmanim.lastIndex) {
                        Spacer(modifier = GlanceModifier.height(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ZmanRow(
    zman: ZmanTime,
    isFirst: Boolean,
    context: Context,
    timeZone: TimeZone,
    primaryColor: ColorProvider,
    onSurfaceColor: ColorProvider,
) {
    val timeFormat = if (DateFormat.is24HourFormat(context)) {
        SimpleDateFormat("HH:mm", Locale.getDefault())
    } else {
        SimpleDateFormat("h:mm a", Locale.getDefault())
    }
    timeFormat.timeZone = timeZone

    val textColor = if (isFirst) primaryColor else onSurfaceColor
    val weight = if (isFirst) FontWeight.Bold else FontWeight.Normal
    val fontSize = if (isFirst) 14.sp else 13.sp

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Indicator dot for the nearest zman
        if (isFirst) {
            Text(
                text = "\u25CF",
                style = TextStyle(
                    color = primaryColor,
                    fontSize = 8.sp,
                ),
            )
            Spacer(modifier = GlanceModifier.width(4.dp))
        }

        // Zman name
        Text(
            text = context.getString(zman.id.hebrewNameRes),
            style = TextStyle(
                color = textColor,
                fontSize = fontSize,
                fontWeight = weight,
            ),
            modifier = GlanceModifier.defaultWeight(),
        )

        // Time
        Text(
            text = zman.time?.let { timeFormat.format(it) } ?: "--:--",
            style = TextStyle(
                color = textColor,
                fontSize = fontSize,
                fontWeight = weight,
                textAlign = TextAlign.End,
            ),
        )
    }
}
