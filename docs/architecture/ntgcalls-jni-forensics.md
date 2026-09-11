# ntgcalls JNI forensics — the connect-time crash

This records the investigation into the crash a connected call hit on physical
hardware, and pins the exact artifact under investigation so this document
does not silently drift out of date under it.

**Status: root cause mechanism PROVEN by static analysis. Fix NOT built.
Fix NOT verified. `CALLS_ENABLED` is `false` until it is.**

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
        ...
        execute_process(COMMAND ${READ_ELF_BIN} -Ws ${WEBRTC_LIB} OUTPUT_VARIABLE ELF_DUMP ...)
        foreach(line ${ELF_DUMP})
            if (line MATCHES "Java_org_webrtc_")
                ...
                list(APPEND LD_FLAGS "-Wl,--undefined=${func}")
            endif ()
        endforeach ()
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
5. **Fix verified to resolve it** — ❌ **not done.** No relink attempted, no device probe run.

Per the standard this investigation was held to: items 1–4 make the
**mechanism** PROVEN. The overall claim "and this fix resolves it" remains a
**hypothesis** — a very well-evidenced one — until item 5 is satisfied.

## What a fix would need to do

Extend (or replace) `cmake/FindWebRTC.cmake`'s retention scan so it also
force-retains every `Java_J_N_*` symbol found in `libwebrtc.a` (a second
`if (line MATCHES "Java_J_N_")` branch alongside the existing
`Java_org_webrtc_` one is the minimal, narrowest change), then rebuild
`libntgcalls.so` against the exact pinned `libwebrtc.a` above and re-run the
standalone probe. This has **not been attempted** — it requires ntgcalls' own
C++ source tree, the pinned NDK (`r28b`) and Boost (`1.92.0`), and produces an
artifact that itself needs the same scrutiny (checksums, symbol
re-verification, probe) applied to rc02 here before it can be trusted.

## Standalone probe (design; not executed this pass)

A minimal Android instrumentation harness, kept **outside** this repository
under `/tmp/aether-ntgcalls-probe/`, that:

1. Depends only on `io.github.pytgcalls:ntgcalls:3.0.0-rc02` (or a rebuilt
   candidate) — no TDLib, no Telegram auth, no network signalling.
2. Loads the native library and calls `NTgCalls.ping()` /
   `NTgCalls.getMediaDevices()` as a load-bearing smoke test (this exercises
   only the 49 `Java_io_github_pytgcalls_NTgCalls_*` entry points, already
   known-present).
3. Then directly instantiates `org.webrtc.SoftwareVideoEncoderFactory` (a
   public class in the bundled `webrtc.jar`) and calls the method that
   triggers `createNativeVideoEncoderFactory()` — this is the exact failing
   call, reachable without any Telegram involvement at all.
4. Captures the resulting exception (or success) directly, with stage
   markers (`NATIVE_LIBRARY_LOADED`, `WEBRTC_FACTORY_INITIALIZING`,
   `WEBRTC_FACTORY_READY`) and no call secrets, signalling payloads, auth
   data or media content anywhere in its logging.

**This was not run in this pass.** No physical `arm64-v8a` device was
attached to the host during this investigation (only an `x86_64` emulator,
which cannot load this `.so` any more than it can load Aether's own
`libtdjni.so`). The probe is designed and ready to build the moment a
physical device is available; running it is the next step, not something
this document can claim happened.
