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

    implementation("androidx.annotation:annotation:1.10.0")

    // Pinned fixed Telegram call transport (resolves WebRTC jni_zero symbol retention
    // in libntgcalls.so; see docs/architecture/calling-native-stack.md and
    // call-media/third-party/ntgcalls/README.md).
    implementation(files("libs/ntgcalls-3.0.0-rc02-aetherfix-arm64.aar"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
