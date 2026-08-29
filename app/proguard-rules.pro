# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# Keep official TDLib JNI bindings
-keep class org.drinkless.tdlib.** { *; }
-dontwarn org.drinkless.tdlib.**

# These classes are reached by Android/FCM from the manifest rather than by a
# direct Kotlin call site. Keep their names and entry points stable.
-keep class com.foresightlabs.aether.AetherApplication { *; }
-keep class com.foresightlabs.aether.MainActivity { *; }
-keep class com.foresightlabs.aether.data.push.AetherFirebaseMessagingService { *; }
-keep class com.foresightlabs.aether.data.notifications.NotificationActionReceiver { *; }

# call-media exposes native methods whose generated JNI names include the
# declaring class and method names.
-keep class com.foresightlabs.aether.calls.media.** { *; }
