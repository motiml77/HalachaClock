package com.zmanimclock.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.zmanimclock.desktop.data.DesktopZmanimService

/** PLACEHOLDER — city picker, reminders and autostart land here next. */
@Composable
fun SettingsPane(service: DesktopZmanimService) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("הגדרות")
    }
}
