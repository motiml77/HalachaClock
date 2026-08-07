package com.zmanimclock.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.zmanimclock.desktop.data.DesktopPrefs
import com.zmanimclock.desktop.data.DesktopZmanimService
import com.zmanimclock.desktop.ui.CalendarPane
import com.zmanimclock.desktop.ui.SettingsPane
import com.zmanimclock.desktop.ui.ZmanimPane
import org.jetbrains.skia.Image
import org.junit.Test
import java.io.File

/**
 * Renders each pane offscreen to a PNG.
 *
 * Deliberately NOT a screenshot of a live window: this runs on the user's own
 * machine, and forcing a window to the foreground to photograph it would yank
 * focus away from whatever they are doing. An offscreen Skia render shows the
 * same composition — layout, density, RTL, Hebrew shaping — without touching
 * their desktop.
 *
 * Asserts nothing; it is a visual check, not a gate.
 */
class RenderShotTest {

    private val out = File(System.getProperty("shot.dir") ?: "C:/gtmp/shots").apply { mkdirs() }

    private fun shot(name: String, w: Int = 860, h: Int = 600, content: @Composable () -> Unit) {
        ImageComposeScene(width = w, height = h, density = Density(1f)).use { scene ->
            scene.setContent {
                ZmanimDesktopTheme(dark = false) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Box(Modifier.fillMaxSize()) { content() }
                    }
                }
            }
            val img: Image = scene.render()
            File(out, "$name.png").writeBytes(img.encodeToData()!!.bytes)
            println("SHOT ${File(out, "$name.png")}")
        }
    }

    private fun service() = DesktopZmanimService(DesktopPrefs())

    @Test fun zmanim() = shot("desk_zmanim") { ZmanimPane(service()) }
    @Test fun calendar() = shot("desk_calendar") { CalendarPane(service()) }
    @Test fun settings() = shot("desk_settings") { SettingsPane(service()) }
}
