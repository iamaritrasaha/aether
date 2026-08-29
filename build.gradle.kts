plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.kotlin.compose) apply false
  // Applied conditionally in :app, only when app/google-services.json is
  // present -- see the HAS_FCM_CONFIG guard there. Declaring it here with
  // apply false makes the plugin resolvable without forcing every build to
  // have Firebase configured.
  alias(libs.plugins.google.services) apply false
}
