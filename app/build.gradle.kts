import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing config read from a gitignored keystore.properties (absent = unsigned,
// so F-Droid / third parties still build reproducibly).
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) FileInputStream(keystorePropsFile).use { load(it) }
}

// F-Droid reproducibility: the ART baseline profile (assets/dexopt/baseline.prof / .profm) is not
// byte-identical across build environments (profgen / build-tools differences), which breaks the
// reproducible-build byte comparison. Drop it from the release APK by disabling the ArtProfile tasks.
tasks.configureEach {
    if (name.contains("ArtProfile")) {
        enabled = false
    }
}

android {
    namespace = "net.yumicoradio.android"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "net.yumicoradio.android"
        // minSdk 24 (not 23): drops the v1 (JAR) signature, which API 23 would require. The v1
        // signature block is what F-Droid's apksigcopier could not reuse ("Unsupported compresslevel"),
        // so building v2/v3-only keeps the reproducible build verifiable on their side.
        minSdk = 24
        targetSdk = 36
        versionCode = 122
        versionName = "0.44.2108"

    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
                // v2/v3 only. v1 (JAR) signing is dropped: apksigcopier on F-Droid's side fails to
                // copy the v1 block ("Unsupported compresslevel"). minSdk 24 makes v1 unnecessary.
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
            // Don't stamp the git commit into META-INF/version-control-info.textproto. It changes
            // every commit and buys nothing here — leaving it out keeps the APK reproducible for
            // F-Droid (which rebuilds and byte-compares to reuse our own signature).
            vcsInfo {
                include = false
            }
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    // Keep the encrypted Play "dependencies" blob out of the APK: it is non-deterministic, so its
    // presence alone makes the build unreproducible — and it is useless off Google Play.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            // The JVM stub android.jar throws on every framework call. Return defaults instead, so a
            // unit test that drives the real repository (which reads SystemClock.elapsedRealtime for
            // the auto-away clock) doesn't die on an unmocked stub. Presence timing itself is tested
            // with an injected clock in PresenceControllerTest, not through this.
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Playback
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.ui)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.guava)

    // Networking + JSON
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // Image loading
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)

    // Prefs
    implementation(libs.datastore.preferences)

    // Live chat transport.
    //
    // org.json is excluded on purpose: socket.io-client declares it, but Android ships those
    // classes in android.jar, so nothing needs packaging. It also keeps the JSON License
    // ("shall be used for Good, not Evil") out of the APK — F-Droid and Debian reject it, and
    // this app ships GPLv3 on F-Droid.
    implementation(libs.socketio.client) {
        exclude(group = "org.json", module = "json")
    }

    // The JVM unit tests run off android.jar stubs, which throw on every org.json call, so the
    // real implementation is needed there — test-only, never packaged.
    testImplementation(libs.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
