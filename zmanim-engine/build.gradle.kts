import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * The halachic engine, on its own — plain Kotlin/JVM, no Android.
 *
 * This module exists so the zmanim can never fork. The times were verified
 * second-by-second against the Ohr HaChaim / Chazon Yosef luach; a second
 * implementation for another platform would drift the moment one side gets a
 * correction the other doesn't, and the same person would see two different
 * times on two devices. Every consumer — the Android app, and a desktop app
 * later — depends on THIS, so there is only ever one answer.
 *
 * The luach verification tests live here too, for the same reason: they run
 * for every consumer automatically.
 *
 * NOTE: deliberately NOT `kotlin { jvmToolchain(17) }`. Gradle does not
 * auto-detect the JDK 17 installed on the build machine and fails with
 * "Toolchain download repositories have not been configured". Setting the
 * target explicitly emits Java 17 bytecode on the running JDK, which is what
 * :app expects.
 */
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    // `api`, not `implementation`: KosherJava types leak through this module's
    // public surface — EngineLocation.toKosherJavaGeoLocation() returns a
    // GeoLocation, and ZmanKind/FastDays construct JewishCalendar.
    api(libs.kosherjava.zmanim)
    // JSR-330, not Android. Kept so Hilt can constructor-inject the engine in
    // :app without this module knowing Hilt exists.
    api(libs.javax.inject)

    testImplementation(libs.junit)
}
