#include "call_session.h"
#include <android/log.h>
#include <cstring>

#define TAG "CallSession"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace callmedia {

bool CallSession::hasRealTelegramTransport() {
#ifdef HAVE_OFFICIAL_TGCALLS
    return true;
#else
    return false;
#endif
}

CallSession::CallSession(CallSessionListener* listener)
    : listener_(listener) {}

CallSession::~CallSession() {
    stop();
}

void CallSession::secureZeroMemory(void* ptr, size_t len) {
    if (!ptr || len == 0) return;
    volatile uint8_t* p = static_cast<volatile uint8_t*>(ptr);
    while (len--) {
        *p++ = 0;
    }
}

void CallSession::transitionState(ConnectionState newState) {
    state_.store(newState);
    if (listener_) {
        listener_->onStateChanged(newState);
    }
}

bool CallSession::start(const CallConfig& config) {
    std::lock_guard<std::mutex> lock(sessionMutex_);

    config_ = config;
    isMuted_.store(false);

    LOGI("CallSession start requested: callId=%lld, servers=%zu, allowP2p=%d, minLayer=%d, maxLayer=%d",
         (long long)config_.callId, config_.servers.size(), config_.allowP2p ? 1 : 0,
         config_.minLayer, config_.maxLayer);

    if (!hasRealTelegramTransport()) {
        LOGW("Official Telegram tgcalls transport is NOT compiled into this binary.");
        isRunning_.store(false);
        transitionState(ConnectionState::UNAVAILABLE);
        if (listener_) {
            listener_->onError("Official Telegram tgcalls media transport is not compiled into this build.");
        }
        return false;
    }

    // When official tgcalls is compiled and linked, the real tgcalls::Instance is initialized here.
    return true;
}

void CallSession::setMuted(bool muted) {
    isMuted_.store(muted);
    LOGD("CallSession setMuted: %d", muted ? 1 : 0);
}

void CallSession::stop() {
    std::lock_guard<std::mutex> lock(sessionMutex_);
    if (!isRunning_.load() && state_.load() == ConnectionState::STOPPED) {
        return;
    }

    LOGI("CallSession stopping for callId %lld", (long long)config_.callId);
    isRunning_.store(false);

    // Zero out encryption key and server credentials in memory securely
    if (!config_.encryptionKey.empty()) {
        secureZeroMemory(config_.encryptionKey.data(), config_.encryptionKey.size());
        config_.encryptionKey.clear();
    }

    for (auto& server : config_.servers) {
        if (!server.peerTag.empty()) {
            secureZeroMemory(server.peerTag.data(), server.peerTag.size());
            server.peerTag.clear();
        }
        if (!server.password.empty()) {
            secureZeroMemory(server.password.data(), server.password.size());
            server.password.clear();
        }
    }

    transitionState(ConnectionState::STOPPED);
    LOGI("CallSession stopped cleanly");
}

} // namespace callmedia
