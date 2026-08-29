package com.zmanimclock.app.feature.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * A live mock of the Hebrew-date widget, shown at the top of its config
 * screen and updating as the user picks a color and opacity.
 *
 * Real dates rather than the illustrative sample: this widget shows nothing
 * BUT the date, so today's real one IS the honest preview — there is no
 * "config affects layout" case ([WidgetMockPreview]'s reason for a labelled
 * illustrative mock) to hide here.
 *
 * An approximation, same as [WidgetMockPreview]: this composites the chosen
 * color at the chosen alpha over the CONFIG SCREEN's own background, not over
 * a home-screen wallpaper. Lighter alpha still reads as "more see-through"
 * even though what shows behind it here isn't what will actually show behind
 * the real widget.
 */
@Composable
internal fun HebrewDateWidgetMockPreview(parts: HebrewDateParts, preset: WidgetColorPreset, opacityPercent: Int) {
    Column {
        Text(
            "כך זה ייראה",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(
                    Color(preset.color).copy(alpha = opacityPercent / 100f),
                    RoundedCornerShape(20.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    parts.weekday,
                    color = Color.White,
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                    textAlign = TextAlign.Center,
                )
                Text(
                    parts.day,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.displayMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    parts.month,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Text(
                    parts.year,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
