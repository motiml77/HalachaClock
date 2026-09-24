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
        // Play requires a UNIQUE versionCode across EVERY track, so each upload
        // takes the next number, and one source is often built twice — once
        // per paywall setting. History: 5, 7, 8, 10, then 13 went to Internal
        // Testing (paywall=true); 6, 9, 12, then 14 to Closed Testing
        // (paywall=false) — 11 was skipped; it was never sent for review, but
        // Play still burns a versionCode the moment a bundle carrying it
        // finishes upload processing on ANY draft, and deleting the draft
        // does not give it back. 15 (Internal) and 16 (Closed) are this
        // source.
        //
        // 15/16 add Sefirat HaOmer: a nightly tzeit alert for the 49 nights of
        // the count, managed entirely from Settings (a wheat-gold card there
        // toggles it; a first-night prompt offers it once a season) rather
        // than hand-built like an ordinary alarm. Skips any night that would
        // ring on Shabbat or Yom Tov — checked on the night being ENTERED, not
        // the fire date — and retires itself after the 49th count, which is
        // what flips the Settings switch back off and re-arms next year's
        // prompt. The ring screen carries its own drawn wheat-sheaf scene and
        // the day number in the huge digits slot that used to show an
        // unrelated wall-clock reading there.
        //
        // versionName moves to 1.2.0 — a real feature, not another fix pass.
        versionCode = 16
        versionName = "1.2.0"

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

    testOptions {
        unitTests {
            // Without this, any unmocked Android framework call (e.g.
            // android.util.Log, used by ChaiTablesPreloader/Repository) throws
            // "Method ... not mocked" and the test fails before its own logic
            // even runs.
            isReturnDefaultValues = true
        }
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

    // Women's Area security gate (biometric + PIN)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment.ktx)

    // KosherJava Zmanim (astronomical substrate only)
    // The halachic engine + the luach verification tests. KosherJava comes in
    // transitively (declared `api` there), so it is not repeated here — one
    // module owns the version.
    //
    // org.json excluded: :zmanim-engine needs a REAL org.json to parse the
    // bundled ChaiTables asset in its own compile/test scope and for the
    // desktop build, which has no platform-provided one. A real device
    // already provides org.json itself; bundling a second copy into the APK
    // is dead weight this app has never needed at runtime. The unit-test
    // classpath is unaffected — it gets its own real org.json below,
    // because the mockable android.jar's JSONObject cannot actually parse.
    implementation(project(":zmanim-engine")) {
        exclude(group = "org.json", module = "json")
    }


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
    testImplementation(libs.org.json)
}
