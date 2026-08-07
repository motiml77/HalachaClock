import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * "שעון זמנים" for Windows.
 *
 * A zmanim board and Hebrew calendar — NOT an alarm clock. No ringing, no
 * vibration, no snooze; at most a silent pop-up reminder the user opted into.
 * See docs/DESKTOP_PLAN.md.
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
            targetFormats(TargetFormat.Msi)
            // ASCII: the installer identity. The product name shown in the UI
            // is Hebrew, but packageName feeds file paths and WiX.
            // ASCII ONLY, all three of these. Verified by bisection: WiX's
            // light.exe exits 311 with no diagnostic when menuGroup or
            // description contain Hebrew — it links with -cultures:en-us and
            // chokes on the non-ASCII literals. Hebrew menuGroup alone is
            // enough to break it. The user-facing Hebrew lives in the window
            // title and the whole UI; only the installer metadata is Latin.
            packageName = "HalachClock"
            packageVersion = "1.0.0"
            description = "HalachClock - Zmanim and Hebrew calendar"
            vendor = "HalachClock"

            // jlink does not work these out on its own, and a missing module is
            // a ClassNotFoundException at RUNTIME, not at build time.
            //   java.desktop  — Compose/Skiko host window and the tray
            //   java.naming   — pulled in transitively by HTTP stacks
            //   java.prefs    — settings persistence
            modules("java.desktop", "java.naming", "java.prefs")

            windows {
                menuGroup = "HalachClock"
                perUserInstall = true   // no admin rights needed
                dirChooser = true
                shortcut = true
                iconFile.set(project.file("src/main/resources/branding/app.ico"))
                // FROZEN. Change this and every upgrade installs alongside the
                // old version instead of replacing it.
                upgradeUuid = "8f3a6c21-4e97-4b1d-9c8a-2d5e7b0a1f64"
            }
        }
    }
}
