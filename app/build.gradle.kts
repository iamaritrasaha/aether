import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
}

val localProperties = Properties().apply {
  val file = rootProject.file("local.properties")
  if (file.exists()) {
    file.inputStream().use { load(it) }
  }
}

val telegramApiId = localProperties.getProperty("TELEGRAM_API_ID").orEmpty().trim()
val telegramApiHash = localProperties.getProperty("TELEGRAM_API_HASH").orEmpty().trim()

val signingProperties = Properties().apply {
  val configuredPath = System.getenv("AETHER_SIGNING_PROPERTIES")
  val file = configuredPath?.let(::file)
    ?: file(System.getProperty("user.home") + "/.android/keystores/aether-signing.properties")
  if (file.exists()) {
    file.inputStream().use { load(it) }
  }
}

android {
  namespace = "com.foresightlabs.aether"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.foresightlabs.aether"
    minSdk = 24
    targetSdk = 36
    versionCode = 2
    versionName = "1.1"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    ndk {
      abiFilters += listOf("arm64-v8a")
    }

    buildConfigField("int", "TELEGRAM_API_ID", telegramApiId.ifEmpty { "0" })
    // Hash is injected for TDLib initialization only. Never log or display it.
    buildConfigField("String", "TELEGRAM_API_HASH", "\"${telegramApiHash.replace("\"", "\\\"")}\"")
    buildConfigField("boolean", "HAS_TELEGRAM_CREDENTIALS", (telegramApiId.isNotEmpty() && telegramApiHash.isNotEmpty()).toString())
    buildConfigField("String", "TDLIB_COMMIT", "\"89ebded9571b7bb589ec1bd05e585fffa4c580e2\"")
  }

  signingConfigs {
    create("release") {
      storeFile = signingProperties.getProperty("storeFile")?.let(::file)
      storePassword = signingProperties.getProperty("storePassword")
      keyAlias = signingProperties.getProperty("keyAlias")
      keyPassword = signingProperties.getProperty("keyPassword")
    }
    // Debug builds use the standard Android debug keystore, which the toolchain
    // creates per developer under ~/.android. Nothing about debug signing lives in
    // the repository, so a fresh clone builds without any local setup.
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  packaging {
    jniLibs {
      useLegacyPackaging = true
    }
  }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
  lint {
    abortOnError = false
    checkReleaseBuilds = false
  }
}

dependencies {
  implementation(project(":tdlib"))
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.coil.compose)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.androidx.datastore.preferences)
  // Haze 1.2.2 targets the same Compose 1.7 generation as Aether and provides
  // real backdrop capture/RenderEffect blur with a built-in scrim fallback.
  implementation("dev.chrisbanes.haze:haze:1.2.2")
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
}
