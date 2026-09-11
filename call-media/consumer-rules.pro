-keep class com.foresightlabs.aether.calls.media.** { *; }

# WebRTC JNI and Java classes reached via native C++ reflection in libntgcalls.so
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# WebRTC jni_zero generated JNI bindings and hash holders
-keep class org.jni_zero.** { *; }
-dontwarn org.jni_zero.**
-keep class J.** { *; }
-dontwarn J.**

# ntgcalls JNI bindings and data models reached by native C++
-keep class io.github.pytgcalls.** { *; }
-dontwarn io.github.pytgcalls.**
