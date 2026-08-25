package org.drinkless.tdlib;

/**
 * Loads official TDLib JNI plus its OpenSSL shared libraries.
 * Client.java does not load natives itself.
 */
public final class NativeLoader {
    private static volatile boolean loaded;

    private NativeLoader() {}

    public static synchronized void load() {
        if (loaded) {
            return;
        }
        System.loadLibrary("cryptox");
        System.loadLibrary("sslx");
        System.loadLibrary("tdjni");
        loaded = true;
    }
}
