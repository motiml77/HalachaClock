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
import com.zmanimclock.desktop.reminder.PendingReminder
import com.zmanimclock.desktop.reminder.ReminderBanner
import com.zmanimclock.app.feature.zmanim.model.ZmanKind
import com.zmanimclock.desktop.data.DesktopPrefs
import com.zmanimclock.desktop.data.DesktopZmanimService
import com.zmanimclock.desktop.ui.CalendarPane
import com.zmanimclock.desktop.ui.SettingsPane
import com.zmanimclock.desktop.ui.TrayMenuContent
import com.zmanimclock.desktop.ui.TrayMenuDivider
import com.zmanimclock.desktop.ui.TrayMenuItem
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
     * THE REAL WINDOW SIZE. Keep it in step with Main.kt's rememberWindowState.
     *
     * Rendering at any other size hides the exact class of bug these shots
     * exist to catch: text that fits at 860 wide and clips at 620. Both of the
     * clipping bugs this project shipped would have been visible here, and
     * neither was, because the shots were being taken at the wrong width.
     */
    private val windowW = 320
    private val windowH = 560

    private fun shot(name: String, dark: Boolean, w: Int, h: Int = windowH, content: @Composable () -> Unit) {
        ImageComposeScene(width = w, height = h, density = Density(1f)).use { scene ->
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
    private fun both(name: String, w: Int = windowW, content: @Composable () -> Unit) {
        shot(name, dark = false, w = w, content = content)
        shot(name, dark = true, w = w, content = content)
    }

    // Each pane is shot at the width its own tab opens the window to — see
    // MainTab. Shooting them all at one width is what let the near-black
    // palette and the clipped labels through: a pane looks fine at a size
    // nobody ever sees it at.
    @Test fun zmanim() = both("desk_zmanim", 320) { ZmanimPane(service()) }

    @Test fun calendar() = both("desk_calendar", 660) { CalendarPane(service()) }
    @Test fun settings() = both("desk_settings", 560) { SettingsPane(service()) }

    // The reminder banner, at its real 378x189 size, with a realistic
    // reminder. It hangs from the top of the screen over other people's
    // windows, which makes it the single most visible surface the app owns —
    // and the one whose clipping bug would be seen by the most eyes.
    @Test
    fun reminderBanner() {
        val reminder = PendingReminder(
            kind = ZmanKind.SHKIA,
            name = ZmanKind.SHKIA.hebrewName,
            time = "19:11",
            at = java.time.Instant.now(),
        )
        for (dark in listOf(false, true)) {
            shot("desk_reminder", dark = dark, w = 378, h = 189) {
                ReminderBanner(reminder) {}
            }
        }
    }

    // At the menu's own real size, not the window's — a small popup shot at
    // 400x560 is mostly a screenshot of empty space and would not have shown
    // whether the three rows and the divider actually fit inside 132dp.
    @Test
    fun trayMenu() {
        for (dark in listOf(false, true)) {
            shot("desk_tray_menu", dark = dark, w = 190, h = 132) {
                TrayMenuContent(onDismiss = {}) {
                    TrayMenuItem("הצג חלון ראשי") {}
                    TrayMenuItem("הצג ווידג'ט") {}
                    TrayMenuDivider()
                    TrayMenuItem("יציאה") {}
                }
            }
        }
    }
}
