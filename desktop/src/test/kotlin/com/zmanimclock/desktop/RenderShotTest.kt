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

    /**
     * The real window size, so a shot shows what the user will actually get.
     * Rendering at some other size hides exactly the class of bug these shots
     * exist to catch — text that fits at 860 wide and clips at 720.
     */
    private val windowW = 720
    private val windowH = 560

    private fun shot(name: String, dark: Boolean, content: @Composable () -> Unit) {
        ImageComposeScene(width = windowW, height = windowH, density = Density(1f)).use { scene ->
            scene.setContent {
                ZmanimDesktopTheme(dark = dark) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Box(Modifier.fillMaxSize()) { content() }
                    }
                }
            }
            val img: Image = scene.render()
            val file = File(out, "${name}_${if (dark) "dark" else "light"}.png")
            file.writeBytes(img.encodeToData()!!.bytes)
            println("SHOT $file")
        }
    }

    private fun service() = DesktopZmanimService(DesktopPrefs())

    /**
     * Both themes, every time. The app follows the OS setting, so half the
     * users see the dark palette and a light-only shot cannot tell you whether
     * their window is legible — which is how the dark surfaces stayed at
     * near-black long enough to ship.
     */
    private fun both(name: String, content: @Composable () -> Unit) {
        shot(name, dark = false, content = content)
        shot(name, dark = true, content = content)
    }

    @Test fun zmanim() = both("desk_zmanim") { ZmanimPane(service()) }
    @Test fun calendar() = both("desk_calendar") { CalendarPane(service()) }
    @Test fun settings() = both("desk_settings") { SettingsPane(service()) }
}
