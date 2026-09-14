import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

// Release signing — credentials live in keystore.properties (git-ignored).
// Falls back gracefully so debug builds work without the keystore present.
// See the PAYWALL SWITCH note in defaultConfig.
//
// PARSED STRICTLY. `"1".toBoolean()` and `"yes".toBoolean()` are false in
// Kotlin, so a lenient parse turns a typo into a production build that gives
// the app away. Only the words true and false are accepted.
val paywallProperty = project.findProperty("paywall") as String?
val paywallEnabled = when (paywallProperty?.trim()?.lowercase()) {
    null -> false
    "true" -> true
    "false" -> false
    else -> throw GradleException(
        "-Ppaywall must be exactly true or false (got '$paywallProperty')."
    )
}
logger.lifecycle(
    if (paywallEnabled) "PAYWALL: ON  — this build charges after the free trial"
    else "PAYWALL: OFF — closed-testing build, nobody is ever charged"
)

// A RELEASE BUILD MUST SAY WHETHER IT CHARGES. Failing closed, found by an
// adversarial review: with the flag defaulting to OFF, the ordinary way of
// building a release — `./gradlew :app:bundleRelease`, or Android Studio's
// "Generate Signed Bundle" — produced a bundle that gave the app away, and
// nothing anywhere failed. The only signal was one log line in Gradle's
// output and a Settings label visible only after installing from Play. Now
// the build refuses to start, and says what to type. Debug builds keep the
// OFF default so day-to-day development and tests need nothing extra.
gradle.taskGraph.whenReady {
    val buildsRelease = allTasks.any {
        it.project == project &&
            it.name.matches(Regex("(bundle|assemble|package|install)Release"))
    }
    if (buildsRelease && paywallProperty == null) {
        throw GradleException(
            """
            |A release build must say whether it charges. Rebuild with one of:
            |    Production (charges after the free trial):  -Ppaywall=true
            |    Closed testing (never charges):             -Ppaywall=false
            |e.g.  ./gradlew :app:bundleRelease -Ppaywall=true
            |NEVER promote a closed-testing (paywall=false) bundle to Production in Play Console.
            """.trimMargin()
        )
    }
}

val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) load(keystorePropsFile.inputStream())
}

android {
    namespace = "com.zmanimclock.app"
    compileSdk = 36

    defaultConfig {
        // Play Store identity only — deliberately decoupled from `namespace`
        // above (which stays com.zmanimclock.app, matching every Kotlin
        // source file's actual package declaration; changing THAT would mean
        // renaming ~140 files for zero benefit). applicationId is the one
        // Google checks for global uniqueness, and com.zmanimclock.app is
        // already registered to some other, unrelated Play Console account —
        // confirmed via two failed "Create app" attempts on an account that
        // itself has zero apps. Picked to match the owner's own GitHub
        // identity (github.com/motiml77/HalachaClock) rather than reusing
        // the "zmanimclock" domain that already collided once.
        applicationId = "com.motiml77.halachaclock"
        minSdk = 26
        targetSdk = 36
        // Bumped: Play requires a new versionCode per upload, and this build
        // fixes real defects an adversarial pre-upload audit found in the
        // first draft (com.motiml77.halachaclock v1 was never sent to
        // testers) — wrong foreground-service type for the alarm ringer
        // (mediaPlayback -> specialUse), a battery-optimization permission
        // Play policy restricts and the alarm engine doesn't need, and the
        // fixed-alarm timezone bug (AlarmScheduler.zoneFor).
        versionCode = 4
        versionName = "1.0.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // THE PAYWALL SWITCH. Release builds must choose explicitly (the build
        // refuses otherwise — see the check at the top of this file):
        //
        //     ./gradlew :app:bundleRelease -Ppaywall=true    production
        //     ./gradlew :app:bundleRelease -Ppaywall=false   closed testing
        //
        // Off by default because the closed-testing track needs 12 testers for
        // 14 days, and asking them to put a card into Google Play to join
        // would make that impossible to recruit. The code is identical either
        // way — only AccessPolicy's first argument changes — so what testers
        // exercise is exactly what production ships, minus the lock.
        //
        // Printed on every build (below) and shown in Settings ("גרסת בדיקה —
        // ללא חיוב") so a production bundle built WITHOUT the flag cannot go
        // out unnoticed: that mistake would silently give the app away again.
        buildConfigField("boolean", "PAYWALL_ENABLED", paywallEnabled.toString())
    }

    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }

    signingConfigs {
        create("release") {
            if (keystoreProps.isNotEmpty()) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Minification is intentionally OFF: this is a reliability-critical
            // alarm app with reflection-based paths (Room/Hilt/JSON) — an R8
            // slip would fail silently at runtime. Behaviour matches the tested
            // debug build; the release is a proper signed, non-debuggable APK.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (keystoreProps.isNotEmpty()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // KosherJava Zmanim (astronomical substrate only)
    // The halachic engine + the luach verification tests. KosherJava comes in
    // transitively (declared `api` there), so it is not repeated here — one
    // module owns the version.
    implementation(project(":zmanim-engine"))


    // Google Play Billing — the monthly subscription.
    implementation(libs.billing)

    // JSON (cities.json)
    implementation(libs.moshi.kotlin)

    // DateTime
    implementation(libs.kotlinx.datetime)

    // ChaiTables (HTML scraping)
    implementation(libs.jsoup)
    implementation(libs.okhttp)

    // Desugaring for java.time on older APIs
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // Unit tests (zmanim engine verification against the luach)
    testImplementation(libs.junit)
}
