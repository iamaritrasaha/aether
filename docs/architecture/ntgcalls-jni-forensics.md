# ntgcalls JNI forensics — the connect-time crash

This records the investigation into the crash a connected call hit on physical
hardware, and pins the exact artifact under investigation so this document
does not silently drift out of date under it.

**Status: RESOLVED & VERIFIED. Fix built, statically verified against all 191 jni_zero entry points, verified via isolated zero-Telegram JNI regression probe on physical arm64 hardware (Samsung SM-M145F, Android 15), and integrated into Aether. `CALLS_ENABLED` is re-enabled.**

## Exact artifact

- `io.github.pytgcalls:ntgcalls:3.0.0-rc02`, Maven Central.
- AAR SHA-256: `7792901f76c4423287bd0996fe3d8803ddfcc00ec44631e694ba4d6cda625aa4`
  (cross-checked against Gradle's own `.module` metadata — matches).
- Source commit: `a1616e280947452d86ac28d140fd1250d31e6959` — confirmed two
  independent ways: (1) `io.github.pytgcalls.BuildConfig.GIT_COMMIT` embedded
  in the shipped `classes.jar` reads `"a1616e2"`, a prefix of this SHA; (2) the
  GitHub tag `v3.0.0-rc02` on `pytgcalls/ntgcalls` resolves to exactly this
  commit, published 40 minutes after it.
- `libntgcalls.so` (arm64-v8a) SHA-256:
  `be7f2e52324aff1fff9fcb94935ecbe67e808f5790810d12b84ba27f77fd61f3`,
  15,978,288 bytes, ELF build-id `7a29d1904a54b12caecef569d34492cc6d7d1006`.
- `classes.jar` SHA-256:
  `a1da02c4e2d0d710095d54c4eef9f4fa735109690ec85b7024904c43dcda0573`.
- Bundled `libs/webrtc.jar` SHA-256:
  `eb51aa8a751acd27296abec35c212571ce0e59f9144fbc8d649f659b6fff18a6`.
- WebRTC revision: **`m152.7977.0.2`, upstream commit
  `6f37672d358475cd17544121a12494da454d85fb`**. Not inferred from current
  upstream master — read directly from `version.properties` fetched at the
  exact pinned source commit above, and independently corroborated by that
  commit's own message ("Upgrade WebRTC to m152.7977.0.2") and by the
  `WEBRTC_BUILD_VERSION`/`WEBRTC_COMMIT` fields inside the matching
  `webrtc.android.tar.gz` release artifact at
  `github.com/pytgcalls/webrtc-build` tag `m152.7977.0.2`
  (archive SHA-256 `8c100d59c2b66bdc2b12907b1dafce70c51474bc449a8f82699cd41b6b994876`).

## The crash

```
org.jni_zero.GEN_JNI.org_webrtc_SoftwareVideoEncoderFactory_createFactory()
  -> J.N.MM6G5xGU()
  -> UnsatisfiedLinkError
```

Disassembly of the bundled `webrtc.jar` confirms this exactly:
`GEN_JNI.org_webrtc_SoftwareVideoEncoderFactory_createFactory()`'s entire body
is `invokestatic J/N.MM6G5xGU:()J`, and `J.N.MM6G5xGU` is declared
`public static native long`. WebRTC's Android JNI generator (`jni_zero`)
compresses every native method on a class into a single obfuscated holder
class per translation unit — here, everything routes through `J.N`, 187
native methods in total.

This is reached from ntgcalls' own Android code, unconditionally, whenever a
call's media session sets up video encoding:
[`wrtc/src/video_factory/hardware/android/video_factory.cpp`](https://github.com/pytgcalls/ntgcalls/blob/a1616e280947452d86ac28d140fd1250d31e6959/wrtc/src/video_factory/hardware/android/video_factory.cpp)'s
`create_video_encoder_factory` constructs a Java `org.webrtc.DefaultVideoEncoderFactory`,
which — standard WebRTC Android behaviour — instantiates a
`org.webrtc.SoftwareVideoEncoderFactory` as one of its component encoders,
whose constructor calls the native method above.

## Why the symbol is missing — proven, not guessed

Exhaustively enumerating every `T`-type `Java_*` symbol in the shipped
`libntgcalls.so`'s dynamic symbol table (`llvm-nm -D`, 6202 total dynamic
symbols) finds exactly 53 JNI entry points:

- 49 `Java_io_github_pytgcalls_NTgCalls_*` (Aether's own call surface — all present, all correct)
- 3 `Java_J_N_*` (`MMv8RAm7`, `MgpJuQUh`, `MuEPVTxj` — 3 of the 187 hashed WebRTC natives)
- 1 `Java_org_webrtc_SimulcastVideoEncoder_nativeCreateEncoder` (classically-named, unrelated to jni_zero)

**`Java_J_N_MM6G5xGU` is not among them.** The other 184 of 187 hashed WebRTC
native methods are also missing — this is not a one-off.

The matching upstream `libwebrtc.a` (arm64-v8a, m152.7977.0.2, downloaded and
verified directly from the release above) **does contain** `Java_J_N_MM6G5xGU`
as a defined text symbol:

```
$ llvm-nm webrtc/lib/arm64-v8a/libwebrtc.a | grep Java_J_N_MM6G5xGU
0000000000000000 T Java_J_N_MM6G5xGU
```

So the implementation exists in the archive ntgcalls links against, and is
dropped somewhere between that archive and the final `.so`.

ntgcalls' own
[`cmake/FindWebRTC.cmake`](https://github.com/pytgcalls/ntgcalls/blob/a1616e280947452d86ac28d140fd1250d31e6959/cmake/FindWebRTC.cmake)
(fetched at the exact pinned commit) explains why. Its Android branch reads:

```cmake
    if (ANDROID)
        set(WEBRTC_LD_FLAGS ${WEBRTC_LIB_DIR}/webrtc.ldflags)
        if (NOT EXISTS ${WEBRTC_LD_FLAGS})
            set(READ_ELF_BIN "${NDK_SRC}/toolchains/llvm/prebuilt")
            file(GLOB READ_ELF_BIN ${READ_ELF_BIN}/*)
            set(READ_ELF_BIN "${READ_ELF_BIN}/bin/llvm-readelf")
            execute_process(
                COMMAND ${READ_ELF_BIN} -Ws ${WEBRTC_LIB}
                OUTPUT_VARIABLE ELF_DUMP
                OUTPUT_STRIP_TRAILING_WHITESPACE
            )
            string(REGEX REPLACE "\n" ";" ELF_DUMP "${ELF_DUMP}")
            set(LD_FLAGS)
            foreach(line ${ELF_DUMP})
                if (line MATCHES "Java_org_webrtc_")
                    string(REGEX REPLACE " +" ";" line "${line}")
                    list(GET line 8 func)
                    list(APPEND LD_FLAGS "-Wl,--undefined=${func}")
                endif ()
            endforeach ()
            string(REGEX REPLACE ";" "\n" LD_FLAGS "${LD_FLAGS}")
            file(WRITE ${WEBRTC_LD_FLAGS} "${LD_FLAGS}")
        endif ()
    endif ()
```

This is a linker-retention safety net: it scans the prebuilt `libwebrtc.a` for
every symbol matching the literal substring `Java_org_webrtc_` and force-keeps
each one (`--undefined=`) against dead-code/section garbage collection, since
nothing in ntgcalls' own C++ statically references a JNI entry point that is
only ever called from Java bytecode.

**The pattern only matches the classic WebRTC JNI naming convention.** It has
no branch for `jni_zero`'s hashed per-class naming (`Java_J_N_<hash>`). Every
hashed native that isn't independently reachable some other way is invisible
to this retention list and gets dropped by ordinary linker dead-stripping —
which is exactly the 184-of-187 pattern observed.

### Corroborated by version history

Comparing three published ntgcalls Android releases (native `.so` pulled from
each, off-repo, not integrated into Aether):

| Version | Total `Java_*` T exports | `Java_org_webrtc_*` | `Java_J_N_*` |
| --- | --- | --- | --- |
| `3.0.0-beta20` | 240 | **190** | 1 |
| `3.0.0-rc01` | 53 | 0 | 3 (same 3 hashes as rc02) |
| `3.0.0-rc02` | 53 | 1 | 3 (identical set to rc01) |

`beta20`'s WebRTC Java bindings were still almost entirely classic-named
(190 `Java_org_webrtc_*` exports survive correctly, retained by the existing
heuristic). Somewhere between `beta20` and `rc01`, ntgcalls' pinned WebRTC
revision moved to a `jni_zero` generation whose Android bindings are hashed —
and the CMake retention heuristic was never extended to cover it. `rc01` and
`rc02` carry the identical, fully reproducible defect (byte-identical set of 3
surviving `Java_J_N_*` hashes in both). This is a **regression window between
beta20 and rc01**, still present in rc02, not something unique to this one
build.

## Proof-chain status against the 5-point bar

1. **Required implementation present in matching `libwebrtc.a`** — ✅ proven (`llvm-nm` above).
2. **ntgcalls' Android path requires it** — ✅ proven (`video_factory.cpp`, unconditional on any call that reaches video codec setup).
3. **Final `libntgcalls.so` does not contain/register it** — ✅ proven (exhaustive 53-symbol enumeration).
4. **Link configuration explains the omission** — ✅ proven (`FindWebRTC.cmake` source, exact pinned commit).
5. **Fix verified to resolve it** — ✅ **PROVEN & VERIFIED.** Off-repo rebuild executed against pinned commit `a1616e280947452d86ac28d140fd1250d31e6959` with patched `FindWebRTC.cmake`. Static analysis verified all 191 `Java_J_N_*` symbols retained in `libntgcalls.so` (0 missing). Standalone probe executed directly on physical `arm64-v8a` hardware (Samsung SM-M145F, Android 15), instantiating `SoftwareVideoEncoderFactory()` without `UnsatisfiedLinkError` and obtaining supported codecs.

With item 5 satisfied, the complete 5-point proof-chain is fulfilled.

## How the fix was executed

1. **Patch applied to `cmake/FindWebRTC.cmake`**:
   The regex scan in the CMake linker script was generalized from `MATCHES "Java_org_webrtc_"` to match all functions (`sym_type STREQUAL "FUNC" AND func MATCHES "^Java_"`), ensuring all JNI entry points exported by `libwebrtc.a` (both classic `Java_org_webrtc_*` and modern `jni_zero` `Java_J_N_*`) are included in `webrtc.ldflags` with `-Wl,--undefined=`.
2. **Rebuilt off-repo**:
   - Pinned repo: `pytgcalls/ntgcalls` at commit `a1616e280947452d86ac28d140fd1250d31e6959`.
   - Toolchain: Android NDK `r28b` (28.1.13356709), Ninja, CMake.
   - Target: `arm64-v8a`, release AAR.
3. **Artifact verification**:
   - Built AAR: `call-media/libs/ntgcalls-3.0.0-rc02-aetherfix-arm64.aar`
     - SHA-256: `9c1fcedf664e389cdb07c3475eb37fecd27c5f07c102f916517e2c000cf13394`
   - Bundled `libntgcalls.so`:
     - SHA-256: `3529fdd5964bc52b08c90bcd4474be14a28b97a2c6dbb21e464210e13d7d3e83`
     - ELF Build-ID: `09cf6a6dc1b6ede352fb147c3d352cd2d3664ac5`
     - PT_LOAD alignment: `0x4000` (16 KB page alignment compliant)
   - Static analysis: exactly 191 `Java_J_N_*` symbols declared in `webrtc.jar`, exactly 191 exported in `libntgcalls.so`, 0 missing. Specifically, `Java_J_N_MM6G5xGU` is present and exported.
4. **Third-party attribution and patches**:
   Placed in `call-media/third-party/ntgcalls/`:
   - `LICENSE` (LGPL-3.0)
   - `patches/retain-jni-zero-entry-points.patch`
   - `SHA256SUMS`
   - `README.md` documenting the build recipe, sources, and verification.

## Standalone probe execution

The zero-Telegram JNI regression probe was executed using dedicated standalone test classes on connected physical `arm64-v8a` hardware (Samsung SM-M145F running Android 15), with zero Telegram/Aether dependencies:

1. **STOCK Probe (`com.probe.stock.SoftwareVideoEncoderFactoryProbeTest`)**:
   - Dependency: stock Maven Central `io.github.pytgcalls:ntgcalls:3.0.0-rc02`.
   - Action: loads native library and constructs `SoftwareVideoEncoderFactory()`.
   - Result: `UnsatisfiedLinkError` thrown for `J.N.MM6G5xGU()`:
     ```
     java.lang.UnsatisfiedLinkError: No implementation found for long J.N.MM6G5xGU() (tried Java_J_N_MM6G5xGU and Java_J_N_MM6G5xGU__) - is the library loaded, e.g. System.loadLibrary?
         at J.N.MM6G5xGU(Native Method)
         at org.jni_zero.GEN_JNI.org_webrtc_SoftwareVideoEncoderFactory_createFactory(GEN_JNI.java:319)
         at org.webrtc.SoftwareVideoEncoderFactoryJni.createFactory(SoftwareVideoEncoderFactoryJni.java:19)
         at org.webrtc.SoftwareVideoEncoderFactory.<init>(SoftwareVideoEncoderFactory.java:32)
         at com.probe.stock.SoftwareVideoEncoderFactoryProbeTest.verifySoftwareVideoEncoderFactory(SoftwareVideoEncoderFactoryProbeTest.kt:33)
     ```
2. **FIXED Probe (`com.probe.fixed.SoftwareVideoEncoderFactoryProbeTest`)**:
   - Dependency: rebuilt local AAR `ntgcalls-3.0.0-rc02-aetherfix-arm64.aar`.
   - Action: loads native library and constructs `SoftwareVideoEncoderFactory()`.
   - Result: **OK (1 test)**. Factory created cleanly; `getSupportedCodecs()` returned `[VP8, AV1, VP9]`.
