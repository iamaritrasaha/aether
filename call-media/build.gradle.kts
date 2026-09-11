plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.foresightlabs.aether.calls.media"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
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
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Real Telegram-compatible call transport. See
    // docs/architecture/calling-native-stack.md for exactly what this is,
    // which native code it ships, and why it replaced this module's own
    // (never-functional) native/cpp bridge.
    implementation("io.github.pytgcalls:ntgcalls:3.0.0-rc02")

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
