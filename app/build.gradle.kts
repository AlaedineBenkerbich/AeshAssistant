plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Calendar Versioning (CalVer): the release CI workflow computes a `YYYY.MM.MICRO`
// version name/code and injects it via `-PappVersionName=... -PappVersionCode=...`.
// Local/debug builds fall back to a clearly-marked development version.
val appVersionName: String = (project.findProperty("appVersionName") as String?) ?: "0.0.0-dev"
val appVersionCode: Int = (project.findProperty("appVersionCode") as String?)?.toIntOrNull() ?: 1

// Release signing: the release workflow decodes the dedicated signing key
// (stored as encrypted GitHub Actions secrets, never committed to the repo)
// to a local file and exports its location/password through these two
// environment variables before running `assembleRelease`. This keeps every
// published GitHub release signed with the same key, which Android requires
// for users to upgrade-install a newer version without uninstalling first.
val releaseKeystorePath: String? = System.getenv("RELEASE_KEYSTORE_PATH")
val releaseKeystorePassword: String? = System.getenv("RELEASE_KEYSTORE_PASSWORD")
val hasDedicatedReleaseSigning = !releaseKeystorePath.isNullOrBlank() && !releaseKeystorePassword.isNullOrBlank()

android {
    namespace = "fr.alaedine.aesh"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "fr.alaedine.aesh"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasDedicatedReleaseSigning) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword
                keyAlias = "aesh-release"
                keyPassword = releaseKeystorePassword
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
            // Falls back to the auto-generated debug key when the dedicated
            // release secrets aren't available (e.g. local builds by
            // contributors), so `assembleRelease` always works out of the box.
            signingConfig = if (hasDedicatedReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                // Robolectric needs access to internal JDK APIs on Java 17+
                // to shadow the Android framework.
                it.jvmArgs(
                    "--add-opens=java.base/java.lang=ALL-UNNAMED",
                    "--add-opens=java.base/java.util=ALL-UNNAMED",
                    "--add-opens=java.base/java.io=ALL-UNNAMED",
                    "--add-opens=java.base/java.net=ALL-UNNAMED",
                    "--add-opens=java.base/java.security=ALL-UNNAMED",
                    "--add-opens=java.base/java.text=ALL-UNNAMED",
                    "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                    "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
                    "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
                )
            }
        }
    }
}

ksp {
    // Exports Room's schema history as JSON (checked into the repo) so future
    // migrations can be validated/auto-generated against past versions.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Schedules the daily report reminder (see `data.reminder`) so it keeps
    // firing across app restarts and device reboots without a foreground
    // service or exact alarms.
    implementation(libs.androidx.work.runtime.ktx)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Lets Room DAO/repository tests run as fast local JVM unit tests (no
    // emulator needed): Robolectric shadows the Android framework (incl.
    // SQLite) so Room behaves the same as it would on a real device.
    testImplementation(libs.robolectric)
    // Builds/runs `CoroutineWorker`s synchronously in local JVM unit tests
    // (see `DailyReportReminderWorkerTest`).
    testImplementation(libs.androidx.work.testing)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}