plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
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
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}