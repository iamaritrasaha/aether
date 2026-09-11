# ntgcalls (patched Android arm64-v8a distribution)

## Upstream Project
- **Project**: [pytgcalls/ntgcalls](https://github.com/pytgcalls/ntgcalls)
- **Upstream Tag**: `v3.0.0-rc02`
- **Exact Source Commit**: `a1616e280947452d86ac28d140fd1250d31e6959`
- **WebRTC Revision**: `m152.7977.0.2` (upstream commit `6f37672d358475cd17544121a12494da454d85fb`)
- **License**: GNU Lesser General Public License v3.0 (LGPL-3.0). See [LICENSE](LICENSE).

This artifact carries **two** independent patches against the same pinned
upstream commit. Both are required; neither supersedes the other.

## Patch 1: JNI entry-point retention (`patches/retain-jni-zero-entry-points.patch`)

Stock Maven Central `io.github.pytgcalls:ntgcalls:3.0.0-rc02` has a linker symbol retention defect in `cmake/FindWebRTC.cmake`:
The retention filter checked only for classic-style WebRTC JNI symbols matching `Java_org_webrtc_`.
In WebRTC `m152.7977.0.2`, `jni_zero` routes JNI calls through hashed entry points on `J.N` (`Java_J_N_<hash>`).
Because the linker retention script did not retain `Java_J_N_*` function symbols, 188 of 191 `Java_J_N_*` entry points were dead-stripped during final shared library linking. When a call attempted to initialize hardware or software video encoding, `SoftwareVideoEncoderFactory` invoked `J.N.MM6G5xGU()`, which threw `java.lang.UnsatisfiedLinkError` and crashed the process.

The patch updates `cmake/FindWebRTC.cmake` to retain all JNI function symbols (`FUNC`) matching `^Java_`, retaining all 191 `Java_J_N_*` symbols including `Java_J_N_MM6G5xGU`.

## Patch 2: Android class-loader-safe native callback conversion (`patches/android-callback-classloader.patch`)

### The crash
Reproduced on physical hardware (Samsung SM-M145F, Android 15) mid-call, immediately after real signalling had begun flowing (outgoing signalling packets successfully reached TDLib):

```
JNI DETECTED ERROR IN APPLICATION: JNI GetMethodID called with pending
exception java.lang.ClassNotFoundException: Didn't find class
"io.github.pytgcalls.ConnectionInfo" on path: DexPathList[[directory "."],
nativeLibraryDirectories=[...]]
```
— a full ART process abort, not a caught exception.

### Root cause — proven, not guessed
`wrtc::utils::GetJNIEnv()` (`wrtc/src/utils/java_context.cpp`) correctly calls
`webrtc::AttachCurrentThreadIfNeeded()` — **the calling thread genuinely is
attached to the JVM.** The defect is not missing attachment; it is JNI
class-loader context, a distinct and well-documented Android pitfall
(see the [Android JNI FAQ](https://developer.android.com/training/articles/perf-jni#faq_FindClass)
and the comment header on `webrtc::GetClass` in
`sdk/android/native_api/jni/class_loader.h`, both quoted almost verbatim in
the patch itself): `JNIEnv::FindClass` resolves against the ClassLoader
associated with the *nearest Java stack frame on the calling thread*. A
thread that was created natively and merely `AttachCurrentThread`-ed (never
started from Java) has no such frame, so `FindClass` silently falls back to
the system/bootstrap ClassLoader — which cannot see any app-packaged class,
`io.github.pytgcalls.*` included.

The exact call chain proven by reading the pinned source:
```
onConnectionChange fires on a native-created WebRTC thread
  (wrtc/src/interfaces/native_connection.cpp posts this work to
   network_thread()/signaling_thread() -- rtc::Thread instances WebRTC
   creates itself, never passed through Java)
  -> wrtc::utils::GetJNIEnv() -> webrtc::AttachCurrentThreadIfNeeded()
     (thread IS JVM-attached at this point)
  -> generated callback conversion: parseJConnectionInfo(env, value)
     (targets/android/app/src/main/jni/utils.hpp, from utils.hpp.tpl)
  -> findClass(env, "io/github/pytgcalls/ConnectionInfo")
  -> [BEFORE THE PATCH] raw env->FindClass(name)
  -> wrong (system) ClassLoader consulted -> ClassNotFoundException
  -> generated code did not check the pending exception before the next
     JNI call (env->GetMethodID on the now-null class) -> CheckJNI fatal
     abort, escalated by the ART runtime itself, not by Aether's Kotlin code
     (nothing at the Kotlin layer could have caught this: JNI's own
     "GetMethodID called with pending exception" check aborts the process
     before any Java exception handler runs).
```
Confirmed `ConnectionInfo` **was** present, correctly, throughout: verified
in the shipped `classes.jar` (`io.github.pytgcalls.ConnectionInfo`, exactly
the fields/constructor the generated conversion expects), verified present
in Aether's final debug APK dex files, and this is a debug (non-minified)
build, so R8 was not a factor. The failure is exclusively a ClassLoader
lookup defect, not a packaging one.

### Why the fix is correct — proven against WebRTC's own source
`sdk/android/native_api/jni/class_loader.h` (fetched at the pinned WebRTC
commit `6f37672d358475cd17544121a12494da454d85fb`) documents and solves this
exact problem:
```cpp
// Android's FindClass() is tricky because the app-specific ClassLoader is not
// consulted when there is no app-specific frame on the stack (i.e. when called
// from a thread created from native C++ code). These helper functions provide a
// workaround for this.
void InitClassLoader(JNIEnv* env);
ScopedJavaLocalRef<jclass> GetClass(JNIEnv* env, const char* name);
```
And `sdk/android/native_api/base/init.cc` (same pinned commit) confirms
`webrtc::InitAndroid(jvm)` — which ntgcalls' own `JNI_OnLoad`
(`targets/android/app/src/main/jni/jni_onload.cpp`) already calls — already
calls `InitClassLoader(jni::GetEnv())` internally:
```cpp
void InitAndroid(JavaVM* jvm) {
  RTC_CHECK_GE(jni::InitGlobalJniVariables(jvm), 0);
  InitClassLoader(jni::GetEnv());
}
```
So the correct ClassLoader (captured from `JNI_OnLoad`'s thread — a genuine
Java thread, invoked via `System.loadLibrary`) was already cached and ready.
ntgcalls' generated binding code simply never used it.

### The fix
`targets/android/app/src/main/jni/utils.hpp.tpl` defines exactly one
`findClass(JNIEnv*, const char*)` helper that every generated struct/enum
`parseJ<Name>` conversion in this file routes through — the fix lands there,
once, generically, covering every current and future generated callback DTO
(`ConnectionInfo` was only the first one a physical call happened to reach;
nothing about the defect was specific to that one class):
```diff
+#include <sdk/android/native_api/jni/class_loader.h>
...
 inline webrtc::ScopedJavaLocalRef<jclass> findClass(JNIEnv* env, const char* name) {
-    return webrtc::ScopedJavaLocalRef<jclass>::Adopt(env, env->FindClass(name));
+    return webrtc::GetClass(env, name);
 }
```
This patches the **generator template**, not generated build output — every
future `cmake` configure regenerates `utils.hpp` from this `.tpl` with the
fix already in place.

## Artifact Checksums (SHA-256)
- **AAR (`ntgcalls-3.0.0-rc02-aetherfix-arm64.aar`)**: `7b63506df1b0c7c99d2d6e7d5351dbdee8c84d80e89dd3fc02fd644f4fbc2766`
- **`jni/arm64-v8a/libntgcalls.so`**: `c8f48a0682987bbda592ce4bb84e2fb6d0b2c663c9ac42c47de01a89c093ceb9`
- **`libs/webrtc.jar`**: `eb51aa8a751acd27296abec35c212571ce0e59f9144fbc8d649f659b6fff18a6` — **byte-identical** to the patch-1-only build; this patch touches no Java/WebRTC-jar code, only ntgcalls' own C++ JNI generator template.
- **`classes.jar`**: `4dfb1489c937e965176de427f196779dd8ac27b889aae8944a9f34dab3235b43` — also byte-identical, for the same reason.
- **ELF Build ID (`libntgcalls.so`)**: `1ece9c70eb6964e27fad9d789e8af02416b9de74`
- **`.so` size**: 21,609,776 bytes (previous patch-1-only build: 21,610,768 bytes — a ~1 KB delta, consistent with one `FindClass` call site becoming one `GetClass` call plus one new header include; nothing structurally different).

### Static verification performed on the rebuilt `.so`
- `Java_J_N_MM6G5xGU`: present (1 match).
- Total `Java_J_N_*` dynamic `T`-type exports: **191/191** (full retention proof re-verified after rebuild, patch 1 not regressed by patch 2).
- Total `Java_io_github_pytgcalls_*` dynamic exports: **49/49** (unchanged, matches every native method on `NTgCalls`).
- `PT_LOAD` alignment: `0x4000` (16 KB, unchanged).
- No duplicate `org.webrtc` classes introduced; exactly one `libntgcalls.so` packaged for `arm64-v8a`.

## Build Toolchain
- **Host OS**: Linux x86_64
- **Android NDK**: `r28b` (`28.1.13356709`) — installed via `sdkmanager --install "ndk;28.1.13356709"`, unpacked and referenced directly (not the Chromium-vendored clang from the original patch-1 build; see note below).
- **Clang**: NDK r28b's own bundled toolchain — `Android (13324770, +pgo, +bolt, +lto, +mlgo, based on r530567d) clang version 19.0.0`. **Deviation from the original patch-1 provenance**, which used a separately-fetched "Chromium Clang 22" toolchain. Documented honestly rather than asserting false precision: the rebuild used the NDK's own bundled Clang, and produced a `.so` with matching symbol counts, matching 16 KB page alignment, and a size within ~1 KB of the original — strong evidence the toolchain difference did not change the result, but it is a real, disclosed difference in build provenance.
- **JDK**: same JDK used by the host Gradle/Android build (OpenJDK, Android Studio-managed).
- **CMake**: 4.1.2 (Android SDK-managed `cmake;4.1.2` component, used by the `targets/android` Gradle project's `externalNativeBuild`); the standalone static-library configure/build step used the host's system CMake/Ninja.
- **Target ABI**: `arm64-v8a` only (`abiFilters 'arm64-v8a'` in `targets/android/app/build.gradle`, restricted from the stock 4-ABI default to match what is actually built and shipped).
- **Page Size Alignment**: 16 KB (`PT_LOAD` `p_align = 0x4000`), reverified after rebuild.
- **WebRTC prebuilt**: `webrtc.android.tar.gz` fetched from `github.com/pytgcalls/webrtc-build` release `m152.7977.0.2` (SHA-256 of the downloaded archive not re-pinned here; it is fetched by ntgcalls' own `cmake/FindWebRTC.cmake` `DownloadProject` step, keyed to `version.webrtc` in `version.properties`, which is itself pinned to the exact commit checked out).

## How to Reproduce
1. Clone `https://github.com/pytgcalls/ntgcalls.git` and check out commit `a1616e280947452d86ac28d140fd1250d31e6959`.
2. Initialize submodules: `git submodule update --init --recursive`.
3. Apply **both** patches: `patches/retain-jni-zero-entry-points.patch` (to `cmake/FindWebRTC.cmake`) and `patches/android-callback-classloader.patch` (to `targets/android/app/src/main/jni/utils.hpp.tpl`).
4. In `targets/android/app/build.gradle`:
   - Set `ndkPath = ndkDir` and `ndkVersion = "28.1.13356709"`.
   - Set `ndk { abiFilters 'arm64-v8a' }` (stock is 4 ABIs; building all 4 requires the WebRTC prebuilt's `webrtc.ldflags` for each, generated per-ABI by the standalone configure step in 6 below — restrict to what you actually need).
5. Download Android NDK r28b (`https://dl.google.com/android/repository/android-ndk-r28b-linux.zip`, or `sdkmanager --install "ndk;28.1.13356709"`) and make it available at `deps/ndk/src` (a symlink to the installed NDK directory works).
6. Configure and compile the static target (this step also fetches/extracts the pinned WebRTC prebuilt and generates `targets/android/app/src/main/jni/utils.hpp`, `jni.cpp`, etc. from their `.tpl` templates — confirm the patch landed by grepping the generated `utils.hpp` for `webrtc::GetClass` before proceeding):
   ```bash
   cmake -S . -B build_android_arm64 -G Ninja \
     -DCMAKE_BUILD_TYPE=Release \
     -DSTATIC_BUILD=ON \
     -DBINDING=android \
     -DPython_EXECUTABLE=/usr/bin/python3 \
     -DANDROID_ABI=arm64-v8a \
     -DCMAKE_TOOLCHAIN_FILE=cmake/Toolchain.cmake
   cmake --build build_android_arm64 --config Release -j$(nproc)
   ```
7. Build Android release AAR:
   ```bash
   cd targets/android
   echo "sdk.dir=/path/to/Android/Sdk" > local.properties
   ./gradlew assembleRelease --no-daemon --no-configuration-cache
   ```
   The resulting AAR will be located at `targets/android/app/build/outputs/aar/app-release.aar`.
8. Verify before integrating: symbol counts (`Java_J_N_*` = 191, `Java_io_github_pytgcalls_*` = 49, `Java_J_N_MM6G5xGU` present), `PT_LOAD` alignment `0x4000`, and that `classes.jar`/`libs/webrtc.jar` are unchanged (this patch touches no Java code).

## Physical verification
Both patches integrated and tested end-to-end on a Samsung SM-M145F (Android
15, arm64-v8a), consenting test-peer voice calls. See
`docs/architecture/calling-native-stack.md` for the full call trace and
verification results.

## Source Code and Licensing Notice
ntgcalls is licensed under the GNU Lesser General Public License v3.0 (LGPL-3.0). The complete source code of upstream ntgcalls is available at https://github.com/pytgcalls/ntgcalls. The modifications applied to build this artifact are documented herein and provided in `patches/retain-jni-zero-entry-points.patch` and `patches/android-callback-classloader.patch`. Neither patch has been submitted upstream; both are local to this artifact.
