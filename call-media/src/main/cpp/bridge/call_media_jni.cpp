#include <jni.h>
#include <android/log.h>
#include <memory>
#include <string>
#include <vector>
#include <mutex>
#include "call_session.h"

#define TAG "CallMediaJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static JavaVM* g_jvm = nullptr;
static jobject g_callbackRef = nullptr;
static jmethodID g_onConnectionStateChangedMethod = nullptr;
static jmethodID g_onSignalBarsChangedMethod = nullptr;
static jmethodID g_onAudioLevelsChangedMethod = nullptr;
static jmethodID g_onErrorMethod = nullptr;

static std::unique_ptr<callmedia::CallSession> g_session;
static std::mutex g_jniMutex;

class JniCallSessionListener : public callmedia::CallSessionListener {
public:
    void onStateChanged(callmedia::ConnectionState state) override {
        if (!g_jvm || !g_callbackRef || !g_onConnectionStateChangedMethod) return;

        JNIEnv* env = nullptr;
        bool shouldDetach = false;
        int getEnvStat = g_jvm->GetEnv((void**)&env, JNI_VERSION_1_6);

        if (getEnvStat == JNI_EDETACHED) {
            if (g_jvm->AttachCurrentThread(&env, nullptr) != 0) {
                return;
            }
            shouldDetach = true;
        }

        env->CallVoidMethod(g_callbackRef, g_onConnectionStateChangedMethod, static_cast<jint>(state));

        if (shouldDetach) {
            g_jvm->DetachCurrentThread();
        }
    }

    void onSignalBarsChanged(int bars) override {
        if (!g_jvm || !g_callbackRef || !g_onSignalBarsChangedMethod) return;

        JNIEnv* env = nullptr;
        bool shouldDetach = false;
        if (g_jvm->GetEnv((void**)&env, JNI_VERSION_1_6) == JNI_EDETACHED) {
            if (g_jvm->AttachCurrentThread(&env, nullptr) != 0) return;
            shouldDetach = true;
        }

        env->CallVoidMethod(g_callbackRef, g_onSignalBarsChangedMethod, static_cast<jint>(bars));

        if (shouldDetach) {
            g_jvm->DetachCurrentThread();
        }
    }

    void onAudioLevelsChanged(float localLevel, float remoteLevel) override {
        if (!g_jvm || !g_callbackRef || !g_onAudioLevelsChangedMethod) return;

        JNIEnv* env = nullptr;
        bool shouldDetach = false;
        if (g_jvm->GetEnv((void**)&env, JNI_VERSION_1_6) == JNI_EDETACHED) {
            if (g_jvm->AttachCurrentThread(&env, nullptr) != 0) return;
            shouldDetach = true;
        }

        env->CallVoidMethod(g_callbackRef, g_onAudioLevelsChangedMethod, static_cast<jfloat>(localLevel), static_cast<jfloat>(remoteLevel));

        if (shouldDetach) {
            g_jvm->DetachCurrentThread();
        }
    }

    void onError(const std::string& error) override {
        if (!g_jvm || !g_callbackRef || !g_onErrorMethod) return;

        JNIEnv* env = nullptr;
        bool shouldDetach = false;
        if (g_jvm->GetEnv((void**)&env, JNI_VERSION_1_6) == JNI_EDETACHED) {
            if (g_jvm->AttachCurrentThread(&env, nullptr) != 0) return;
            shouldDetach = true;
        }

        jstring jErr = env->NewStringUTF(error.c_str());
        env->CallVoidMethod(g_callbackRef, g_onErrorMethod, jErr);
        env->DeleteLocalRef(jErr);

        if (shouldDetach) {
            g_jvm->DetachCurrentThread();
        }
    }
};

static JniCallSessionListener g_sessionListener;

extern "C" {

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* /* reserved */) {
    g_jvm = vm;
    LOGI("CallMedia JNI_OnLoad initialized");
    return JNI_VERSION_1_6;
}

JNIEXPORT jboolean JNICALL
Java_com_foresightlabs_aether_calls_media_NativeTelegramCallMediaEngine_nativeHasRealTelegramTransport(
    JNIEnv* /* env */,
    jobject /* this */
) {
    return callmedia::CallSession::hasRealTelegramTransport() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_foresightlabs_aether_calls_media_NativeTelegramCallMediaEngine_nativeInit(
    JNIEnv* env,
    jobject /* this */,
    jobject callback
) {
    std::lock_guard<std::mutex> lock(g_jniMutex);

    if (g_callbackRef) {
        env->DeleteGlobalRef(g_callbackRef);
        g_callbackRef = nullptr;
    }

    if (callback) {
        g_callbackRef = env->NewGlobalRef(callback);
        jclass callbackClass = env->GetObjectClass(callback);
        g_onConnectionStateChangedMethod = env->GetMethodID(callbackClass, "onConnectionStateChanged", "(I)V");
        g_onSignalBarsChangedMethod = env->GetMethodID(callbackClass, "onSignalBarsChanged", "(I)V");
        g_onAudioLevelsChangedMethod = env->GetMethodID(callbackClass, "onAudioLevelsChanged", "(FF)V");
        g_onErrorMethod = env->GetMethodID(callbackClass, "onError", "(Ljava/lang/String;)V");
    }
}

JNIEXPORT void JNICALL
Java_com_foresightlabs_aether_calls_media_NativeTelegramCallMediaEngine_nativeStart(
    JNIEnv* env,
    jobject /* this */,
    jlong callId,
    jboolean isOutgoing,
    jbyteArray encryptionKey,
    jboolean allowP2p,
    jobjectArray servers,
    jstring configJson,
    jstring customParams,
    jint minLayer,
    jint maxLayer,
    jobjectArray libraryVersions
) {
    std::lock_guard<std::mutex> lock(g_jniMutex);

    callmedia::CallConfig config;
    config.callId = static_cast<int64_t>(callId);
    config.isOutgoing = (isOutgoing == JNI_TRUE);
    config.allowP2p = (allowP2p == JNI_TRUE);
    config.minLayer = static_cast<int>(minLayer);
    config.maxLayer = static_cast<int>(maxLayer);

    if (encryptionKey) {
        jsize keyLen = env->GetArrayLength(encryptionKey);
        jbyte* keyBytes = env->GetByteArrayElements(encryptionKey, nullptr);
        if (keyBytes && keyLen > 0) {
            config.encryptionKey.assign(reinterpret_cast<uint8_t*>(keyBytes), reinterpret_cast<uint8_t*>(keyBytes) + keyLen);
            env->ReleaseByteArrayElements(encryptionKey, keyBytes, JNI_ABORT);
        }
    }

    if (configJson) {
        const char* str = env->GetStringUTFChars(configJson, nullptr);
        if (str) {
            config.configJson = str;
            env->ReleaseStringUTFChars(configJson, str);
        }
    }

    if (customParams) {
        const char* str = env->GetStringUTFChars(customParams, nullptr);
        if (str) {
            config.customParameters = str;
            env->ReleaseStringUTFChars(customParams, str);
        }
    }

    if (servers) {
        jsize serverCount = env->GetArrayLength(servers);
        for (jsize i = 0; i < serverCount; ++i) {
            jobject sObj = env->GetObjectArrayElement(servers, i);
            if (!sObj) continue;

            jclass sClass = env->GetObjectClass(sObj);
            jfieldID fId = env->GetFieldID(sClass, "id", "J");
            jfieldID fIp = env->GetFieldID(sClass, "ipAddress", "Ljava/lang/String;");
            jfieldID fIpv6 = env->GetFieldID(sClass, "ipv6Address", "Ljava/lang/String;");
            jfieldID fPort = env->GetFieldID(sClass, "port", "I");
            jfieldID fPeerTag = env->GetFieldID(sClass, "peerTag", "[B");
            jfieldID fIsTcp = env->GetFieldID(sClass, "isTcp", "Z");
            jfieldID fUser = env->GetFieldID(sClass, "username", "Ljava/lang/String;");
            jfieldID fPass = env->GetFieldID(sClass, "password", "Ljava/lang/String;");
            jfieldID fTurn = env->GetFieldID(sClass, "supportsTurn", "Z");
            jfieldID fStun = env->GetFieldID(sClass, "supportsStun", "Z");

            callmedia::ServerEndpoint endpoint;
            endpoint.id = env->GetLongField(sObj, fId);
            endpoint.port = env->GetIntField(sObj, fPort);
            endpoint.isTcp = (env->GetBooleanField(sObj, fIsTcp) == JNI_TRUE);
            endpoint.supportsTurn = (env->GetBooleanField(sObj, fTurn) == JNI_TRUE);
            endpoint.supportsStun = (env->GetBooleanField(sObj, fStun) == JNI_TRUE);

            jstring jIp = static_cast<jstring>(env->GetObjectField(sObj, fIp));
            if (jIp) {
                const char* s = env->GetStringUTFChars(jIp, nullptr);
                if (s) { endpoint.ipAddress = s; env->ReleaseStringUTFChars(jIp, s); }
                env->DeleteLocalRef(jIp);
            }

            jstring jIpv6 = static_cast<jstring>(env->GetObjectField(sObj, fIpv6));
            if (jIpv6) {
                const char* s = env->GetStringUTFChars(jIpv6, nullptr);
                if (s) { endpoint.ipv6Address = s; env->ReleaseStringUTFChars(jIpv6, s); }
                env->DeleteLocalRef(jIpv6);
            }

            jbyteArray jTag = static_cast<jbyteArray>(env->GetObjectField(sObj, fPeerTag));
            if (jTag) {
                jsize tagLen = env->GetArrayLength(jTag);
                jbyte* tagBytes = env->GetByteArrayElements(jTag, nullptr);
                if (tagBytes && tagLen > 0) {
                    endpoint.peerTag.assign(reinterpret_cast<uint8_t*>(tagBytes), reinterpret_cast<uint8_t*>(tagBytes) + tagLen);
                    env->ReleaseByteArrayElements(jTag, tagBytes, JNI_ABORT);
                }
                env->DeleteLocalRef(jTag);
            }

            jstring jUser = static_cast<jstring>(env->GetObjectField(sObj, fUser));
            if (jUser) {
                const char* s = env->GetStringUTFChars(jUser, nullptr);
                if (s) { endpoint.username = s; env->ReleaseStringUTFChars(jUser, s); }
                env->DeleteLocalRef(jUser);
            }

            jstring jPass = static_cast<jstring>(env->GetObjectField(sObj, fPass));
            if (jPass) {
                const char* s = env->GetStringUTFChars(jPass, nullptr);
                if (s) { endpoint.password = s; env->ReleaseStringUTFChars(jPass, s); }
                env->DeleteLocalRef(jPass);
            }

            config.servers.push_back(std::move(endpoint));
            env->DeleteLocalRef(sClass);
            env->DeleteLocalRef(sObj);
        }
    }

    if (libraryVersions) {
        jsize verCount = env->GetArrayLength(libraryVersions);
        for (jsize i = 0; i < verCount; ++i) {
            jstring jVer = static_cast<jstring>(env->GetObjectArrayElement(libraryVersions, i));
            if (jVer) {
                const char* s = env->GetStringUTFChars(jVer, nullptr);
                if (s) { config.libraryVersions.emplace_back(s); env->ReleaseStringUTFChars(jVer, s); }
                env->DeleteLocalRef(jVer);
            }
        }
    }

    // Stop any existing call cleanly first
    if (g_session) {
        g_session->stop();
    }

    g_session = std::make_unique<callmedia::CallSession>(&g_sessionListener);
    g_session->start(config);
}

JNIEXPORT void JNICALL
Java_com_foresightlabs_aether_calls_media_NativeTelegramCallMediaEngine_nativeSetMuted(
    JNIEnv* /* env */,
    jobject /* this */,
    jboolean muted
) {
    std::lock_guard<std::mutex> lock(g_jniMutex);
    if (g_session) {
        g_session->setMuted(muted == JNI_TRUE);
    }
}

JNIEXPORT void JNICALL
Java_com_foresightlabs_aether_calls_media_NativeTelegramCallMediaEngine_nativeStop(
    JNIEnv* /* env */,
    jobject /* this */
) {
    std::lock_guard<std::mutex> lock(g_jniMutex);
    if (g_session) {
        g_session->stop();
        g_session.reset();
    }
}

} // extern "C"
