package com.zmanimclock.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.zmanimclock.desktop.Ext
import com.zmanimclock.desktop.ZmanimDesktopTheme
import java.awt.Cursor

/**
 * Asked once, the first time the app runs after being installed: should it
 * start with Windows?
 *
 * WHY THIS IS NOT A PAGE IN THE INSTALLER, WHICH IS WHERE IT WAS ASKED FOR.
 * jpackage — which builds this app's EXE — has no option for it. Its Windows
 * flags cover shortcuts, the Start-menu group, the install directory and the
 * upgrade UUID, and that is the whole list; there is no "run at startup"
 * checkbox to switch on. The only way into the wizard is `--resource-dir`
 * with a hand-written replacement for jpackage's generated `main.wxs`, which
 * means maintaining a copy of a file that belongs to the JDK's internals, and
 * writing the Run key a SECOND time in WiX where it can drift from
 * [com.zmanimclock.desktop.system.StartupManager]. This project has already
 * lost an afternoon to WiX failing with exit code 311 and no diagnostic.
 *
 * So the question is asked here, one moment later, by code that can be
 * rendered and tested — and it goes through the same StartupManager the
 * settings checkbox uses, so there is one implementation of "start with
 * Windows" rather than two that can disagree.
 *
 * ASKED ONCE, whatever the answer. The flag records that the question was
 * PUT, not what was said, so declining is not re-asked at every launch;
 * settings holds the same switch for anyone who changes their mind.
 */
@Composable
fun ApplicationScope.FirstRunStartupPrompt(
    onAnswer: (enable: Boolean) -> Unit,
) {
    Window(
        // Closing the window IS "no thanks" — a dialog whose X does nothing
        // traps people, and there is no wrong answer to lose here.
        onCloseRequest = { onAnswer(false) },
        state = rememberWindowState(
            position = WindowPosition(Alignment.Center),
            size = DpSize(WIDTH, HEIGHT),
        ),
        title = "שעון מעורר - זמנים הלכתיים",
        undecorated = true,
        transparent = true,
        resizable = false,
        alwaysOnTop = true,
    ) {
        ZmanimDesktopTheme {
            StartupPromptCard(onAnswer)
        }
    }
}

/** Split from the window so RenderShotTest can shoot it at its real size. */
@Composable
fun StartupPromptCard(onAnswer: (Boolean) -> Unit) {
    val ext = Ext.colors
    val cs = MaterialTheme.colorScheme

    Column(
        Modifier.fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(ext.heroTop, ext.heroBottom)),
                RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 22.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Outlined.PowerSettingsNew,
            contentDescription = null,
            tint = ext.accentGold,
            modifier = Modifier.height(30.dp),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "להפעיל את התוכנה עם הפעלת המחשב?",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = ext.heroText,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        // The honest reason to say yes, rather than a generic "recommended":
        // an alert can only appear if the program is running, and nobody
        // remembers to open a zmanim board before the zman they wanted.
        Text(
            "התוכנה תעלה מוסתרת במגש המערכת, כדי שההתראות שתגדיר יופיעו " +
                "בזמן בלי לפתוח אותה בכל פעם.",
            style = MaterialTheme.typography.bodySmall,
            color = ext.heroLabel,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PromptButton("כן, הפעל אוטומטית", accent = true) { onAnswer(true) }
            PromptButton("לא, תודה", accent = false) { onAnswer(false) }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "אפשר לשנות זאת בכל עת בהגדרות.",
            style = MaterialTheme.typography.labelSmall,
            color = cs.onSurfaceVariant,
        )
    }
}

@Composable
private fun PromptButton(label: String, accent: Boolean, onClick: () -> Unit) {
    val ext = Ext.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    Text(
        label,
        modifier = Modifier
            .background(
                when {
                    accent && hovered -> ext.accentGold
                    accent -> ext.accentGold.copy(alpha = 0.88f)
                    hovered -> ext.heroInner
                    else -> ext.heroInner.copy(alpha = 0.7f)
                },
                RoundedCornerShape(50),
            )
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR)))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = if (accent) ext.onAccentGold else ext.heroText,
    )
}

private val WIDTH = 360.dp
private val HEIGHT = 240.dp
