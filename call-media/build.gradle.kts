plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.foresightlabs.aether.calls.media"
    compileSdk = 37

    // The native smoke test needs RECORD_AUDIO at runtime; "-g" grants
    // declared runtime permissions on every (re)install so connected runs
    // are self-contained.
    adbOptions {
        installOptions += "-g"
    }

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        // See app/build.gradle.kts's lint block for the rationale: zero
        // errors were present when this was enabled (verified via
        // `./gradlew :call-media:lintDebug`'s SARIF report), so this is a
        // real gate from a clean baseline, not blanket suppression.
        abortOnError = true
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    implementation("androidx.annotation:annotation:1.10.0")

    // Pinned fixed Telegram call transport (resolves WebRTC jni_zero symbol retention
    // in libntgcalls.so; see docs/architecture/calling-native-stack.md and
    // call-media/third-party/ntgcalls/README.md).
    implementation(files("libs/ntgcalls-3.0.0-rc02-aetherfix2-arm64.aar"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // On-device native smoke tests (connectedDebugAndroidTest): exercise the
    // real NTgCalls load/context/device/session seams on physical hardware.
    // These live in THIS module because the vendored AAR is an implementation
    // dependency here -- the app's androidTest classpath cannot see it.
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)
}
