# TDLib

Aether uses the official Telegram Database Library (TDLib) Java/JNI interface (`org.drinkless.tdlib`).

## Pinned revision

* **TDLib git commit:** `89ebded9571b7bb589ec1bd05e585fffa4c580e2`
  (embedded as `TdApi.GIT_COMMIT_HASH` in generated bindings)
* **Java API + Android JNI binary source:** generated official `TdApi.java` / `Client.java` plus `libtdjni.so`
* **Release ABI packaged:** `arm64-v8a` (`x86_64` JNI artifacts remain available for local emulator development)
* **OpenSSL:** `libsslx.so` / `libcryptox.so` (renamed shared OpenSSL, loaded before `tdjni`)

These artifacts are vendored under `tdlib/` so Android Studio and CLI builds are reproducible without compiling C++ on every machine.

To rebuild from official TDLib source instead of the vendored JNI:

1. Follow https://github.com/tdlib/td/tree/master/example/android
2. Check out commit `89ebded9571b7bb589ec1bd05e585fffa4c580e2`
3. Run the official `build-openssl.sh` and `build-tdlib.sh` (Java interface)
4. Replace `tdlib/src/main/java/org/drinkless/tdlib/{TdApi,Client}.java` and `tdlib/src/main/jniLibs/**`

Do not use the Telegram Bot API, unofficial HTTP proxies, or a hand-rolled MTProto stack.
