# ntgcalls (patched Android arm64-v8a distribution)

## Upstream Project
- **Project**: [pytgcalls/ntgcalls](https://github.com/pytgcalls/ntgcalls)
- **Upstream Tag**: `v3.0.0-rc02`
- **Exact Source Commit**: `a1616e280947452d86ac28d140fd1250d31e6959`
- **WebRTC Revision**: `m152.7977.0.2` (upstream commit `6f37672d358475cd17544121a12494da454d85fb`)
- **License**: GNU Lesser General Public License v3.0 (LGPL-3.0). See [LICENSE](LICENSE).

## Purpose of Patch
Stock Maven Central `io.github.pytgcalls:ntgcalls:3.0.0-rc02` has a linker symbol retention defect in `cmake/FindWebRTC.cmake`:
The retention filter checked only for classic-style WebRTC JNI symbols matching `Java_org_webrtc_`.
In WebRTC `m152.7977.0.2`, `jni_zero` routes JNI calls through hashed entry points on `J.N` (`Java_J_N_<hash>`).
Because the linker retention script did not retain `Java_J_N_*` function symbols, 188 of 191 `Java_J_N_*` entry points were dead-stripped during final shared library linking. When a call attempted to initialize hardware or software video encoding, `SoftwareVideoEncoderFactory` invoked `J.N.MM6G5xGU()`, which threw `java.lang.UnsatisfiedLinkError` and crashed the process.

The patch [`patches/retain-jni-zero-entry-points.patch`](patches/retain-jni-zero-entry-points.patch) updates `cmake/FindWebRTC.cmake` to retain all JNI function symbols (`FUNC`) matching `^Java_`, retaining all 191 `Java_J_N_*` symbols including `Java_J_N_MM6G5xGU`.

## Artifact Checksums (SHA-256)
- **AAR (`ntgcalls-3.0.0-rc02-aetherfix-arm64.aar`)**: `9c1fcedf664e389cdb07c3475eb37fecd27c5f07c102f916517e2c000cf13394`
- **`jni/arm64-v8a/libntgcalls.so`**: `3529fdd5964bc52b08c90bcd4474be14a28b97a2c6dbb21e464210e13d7d3e83`
- **`libs/webrtc.jar`**: `eb51aa8a751acd27296abec35c212571ce0e59f9144fbc8d649f659b6fff18a6`
- **`classes.jar`**: `4dfb1489c937e965176de427f196779dd8ac27b889aae8944a9f34dab3235b43`
- **ELF Build ID (`libntgcalls.so`)**: `09cf6a6dc1b6ede352fb147c3d352cd2d3664ac5`

## Build Toolchain
- **Host OS**: Linux x86_64
- **Android NDK**: `r28b` (`28.1.13356709`)
- **Clang**: Chromium Clang 22 (`clang-llvmorg-22-init-20115-g2a8be8bd-1`)
- **JDK**: OpenJDK 21
- **CMake**: 3.27+
- **Ninja**: 1.11+
- **Target ABI**: `arm64-v8a`
- **Page Size Alignment**: 16 KB (`-Wl,-z,max-page-size=16384`, PT_LOAD `p_align = 0x4000`)

## How to Reproduce
1. Clone `https://github.com/pytgcalls/ntgcalls.git` and check out commit `a1616e280947452d86ac28d140fd1250d31e6959`.
2. Initialize submodules: `git submodule update --init --recursive`.
3. Apply `patches/retain-jni-zero-entry-points.patch` to `cmake/FindWebRTC.cmake`.
4. In `targets/android/app/build.gradle`:
   - Set `ndkPath = ndkDir` and `ndkVersion = "28.1.13356709"`.
   - Set `ndk { abiFilters 'arm64-v8a' }`.
5. Download Android NDK r28b (`https://dl.google.com/android/repository/android-ndk-r28b-linux.zip`) and unpack into `deps/ndk/src`.
6. Configure and compile static target:
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
   ./gradlew assembleRelease
   ```
   The resulting AAR will be located at `targets/android/app/build/outputs/aar/app-release.aar`.

## Source Code and Licensing Notice
ntgcalls is licensed under the GNU Lesser General Public License v3.0 (LGPL-3.0). The complete source code of upstream ntgcalls is available at https://github.com/pytgcalls/ntgcalls. The modifications applied to build this artifact are documented herein and provided in `patches/retain-jni-zero-entry-points.patch`.
