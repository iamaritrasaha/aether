#ifndef CALL_MEDIA_CALL_SESSION_H
#define CALL_MEDIA_CALL_SESSION_H

#include <string>
#include <vector>
#include <memory>
#include <atomic>
#include <mutex>
#include <cstdint>

namespace callmedia {

enum class ConnectionState {
    IDLE = 0,
    INITIALIZING = 1,
    CONNECTING = 2,
    CONNECTED = 3,
    RECONNECTING = 4,
    FAILED = 5,
    UNAVAILABLE = 6,
    STOPPED = 7
};

struct ServerEndpoint {
    int64_t id{0};
    std::string ipAddress;
    std::string ipv6Address;
    int port{0};
    std::vector<uint8_t> peerTag;
    bool isTcp{false};
    std::string username;
    std::string password;
    bool supportsTurn{false};
    bool supportsStun{false};
};

struct CallConfig {
    int64_t callId{0};
    bool isOutgoing{false};
    std::vector<uint8_t> encryptionKey;
    bool allowP2p{false};
    std::vector<ServerEndpoint> servers;
    std::string configJson;
    std::string customParameters;
    int minLayer{65};
    int maxLayer{92};
    std::vector<std::string> libraryVersions;
};

class CallSessionListener {
public:
    virtual ~CallSessionListener() = default;
    virtual void onStateChanged(ConnectionState state) = 0;
    virtual void onSignalBarsChanged(int bars) = 0;
    virtual void onAudioLevelsChanged(float localLevel, float remoteLevel) = 0;
    virtual void onError(const std::string& error) = 0;
};

class CallSession {
public:
    explicit CallSession(CallSessionListener* listener);
    ~CallSession();

    static bool hasRealTelegramTransport();

    bool start(const CallConfig& config);
    void setMuted(bool muted);
    void stop();

    ConnectionState getState() const { return state_.load(); }
    bool isMuted() const { return isMuted_.load(); }

private:
    void transitionState(ConnectionState newState);
    void secureZeroMemory(void* ptr, size_t len);

    CallSessionListener* listener_{nullptr};
    std::atomic<ConnectionState> state_{ConnectionState::IDLE};
    std::atomic<bool> isMuted_{false};
    std::atomic<bool> isRunning_{false};

    CallConfig config_;
    std::mutex sessionMutex_;
};

} // namespace callmedia

#endif // CALL_MEDIA_CALL_SESSION_H
