import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * "Halacha Clock" (שעון מעורר - זמנים הלכתיים) for Windows.
 *
 * A zmanim board and Hebrew calendar — NOT an alarm clock. No ringing, no
 * vibration, no snooze; at most a silent pop-up reminder the user opted into.
 * See docs/DESKTOP_PLAN.md.
 *
 * The BRAND changed (Aug 2026); the internal `%LOCALAPPDATA%\HalachClock\`
 * data folder and the `ZmanimClock` registry value name (StartupManager) did
 * NOT, and must not. Those are plumbing an existing install already has on
 * disk — renaming them would orphan a user's saved city and reminders on their
 * next update, not rebrand anything they can see.
 *
 * Every halachic value comes from :zmanim-engine, the same module the Android
 * app uses, so the two can never show different times.
 *
 * TWO PINNED DECISIONS, BOTH LEARNED THE HARD WAY:
 *
 * 1. Compose Multiplatform 1.10.0, NOT the newer 1.11.x. 1.11 fails at
 *    configuration time with "Minimal supported Kotlin Gradle Plugin version
 *    is 2.2.0"; this repo is on Kotlin 2.1.0, and moving it would drag AGP,
 *    KSP and Hilt along with it for the shipping Android app.
 *
 * 2. NOT `kotlin { jvmToolchain(17) }`. Gradle does not detect the JDK 17
 *    installed on this machine and fails with "Toolchain download repositories
 *    have not been configured". Setting the target explicitly emits Java 17
 *    bytecode on the running JDK.
 */
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// TESTS NEVER TOUCH THE REAL PROFILE. DesktopPrefs.save() writes to
// %LOCALAPPDATA%\HalachClock by default, and the alert CRUD tests drive the
// real service — which put two test alerts into the developer's own running
// app before this existed. One property redirects the whole data directory,
// including the fired-reminder log.
tasks.withType<Test>().configureEach {
    systemProperty(
        "halachclock.data.dir",
        layout.buildDirectory.dir("test-data").get().asFile.absolutePath,
    )
}

dependencies {
    implementation(project(":zmanim-engine"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    // Win32 for the desktop widget's window styles. Pure Java, no native build.
    implementation(libs.jna)
    implementation(libs.jna.platform)
    testImplementation(libs.junit)
}

compose.desktop {
    application {
        mainClass = "com.zmanimclock.desktop.MainKt"

        nativeDistributions {
            // EXE, not MSI. jpackage's exe bundle is a WIZARD — welcome page,
            // licence page, install-location page, progress, finish — which is
            // what a person expects when they double-click something a friend
            // sent them. The MSI runs silently or throws a bare Windows
            // installer dialog, has no place to show terms, and reinstalling
            // the same version fails with error 1638 instead of just running.
            // Both are produced: the EXE is the one to hand out, the MSI stays
            // for anyone deploying by policy.
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)

            // Shown as its own page in the EXE wizard, which the user must
            // accept before Next becomes available. Plain text, CRLF, ASCII —
            // the installer renders it in a fixed-width control with no
            // wrapping of its own, so the file is hard-wrapped to fit.
            licenseFile.set(project.file("LICENSE.txt"))
            // ASCII: the installer identity. The product's real name inside
            // the app is Hebrew, but packageName/menuGroup/description feed
            // file paths and WiX. ASCII ONLY, all three of these — verified by
            // bisection: WiX's light.exe exits 311 with no diagnostic when
            // menuGroup or description contain Hebrew, because it links with
            // -cultures:en-us and chokes on the non-ASCII literals. Hebrew
            // menuGroup alone is enough to break it. "Halacha Clock" is the
            // ASCII form of the brand for exactly this reason — the Hebrew
            // form lives in the window title and the whole in-app UI.
            packageName = "Halacha Clock"
            // Bumped from 1.0.0: same-version reinstall fails with MSI error
            // 1638 ("another version of this product is already installed"),
            // confirmed in practice — an update MUST raise this or nobody can
            // upgrade in place.
            packageVersion = "1.8.0"
            description = "Halacha Clock - Zmanim and Hebrew calendar"
            vendor = "Halacha Clock"

            // jlink does not work these out on its own, and a missing module is
            // a ClassNotFoundException at RUNTIME, not at build time.
            //   java.desktop  — Compose/Skiko host window and the tray
            //   java.naming   — pulled in transitively by HTTP stacks
            //   java.prefs    — settings persistence
            modules("java.desktop", "java.naming", "java.prefs")

            windows {
                menuGroup = "Halacha Clock"
                // The wizard's own pages. Without these the EXE installs
                // silently on a double-click, which is the opposite of what an
                // installer sent to a stranger should do: they get no licence
                // page, no choice of location, and no sign anything happened.
                //   dirChooser    — the "where to install" page
                //   perUserInstall— no admin prompt; a UAC dialog on a
                //                   personal utility from an unsigned author
                //                   is what makes people cancel
                //   shortcut      — desktop icon
                //   menu          — Start-menu entry under menuGroup
                perUserInstall = true
                dirChooser = true
                shortcut = true
                menu = true
                iconFile.set(project.file("src/main/resources/branding/app.ico"))
                // FROZEN. Change this and every upgrade installs alongside the
                // old version instead of replacing it.
                upgradeUuid = "8f3a6c21-4e97-4b1d-9c8a-2d5e7b0a1f64"
            }
        }
    }
}
