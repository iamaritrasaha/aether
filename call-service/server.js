// Development Aether Call Service.
//
// Responsibilities (deliberately small):
//   - Aether calling-identity directory (Telegram <-> Aether mapping)
//   - call invitations + accept/decline state
//   - LiveKit room creation policy (one room per call, random name)
//   - short-lived LiveKit participant tokens (roomJoin, 10 min TTL)
//   - a per-invite DEV media key for LiveKit E2EE
//
// SECURITY (read docs/architecture/aether-calls.md):
//   - The LiveKit API key/secret live ONLY in this service's environment.
//     The Android app never receives credentials -- just tokens.
//   - The E2EE media key is handed to participants THROUGH this service in
//     the clear. That is development-scoped ONLY: the service (and anyone
//     who can reach it) can read every dev call's media. Production replaces
//     this with authenticated per-call key establishment; the app already
//     isolates the boundary in AetherCallSecurity.

const express = require("express");
const { AccessToken } = require("livekit-server-sdk");
const crypto = require("crypto");

const PORT = process.env.PORT || 8080;
const LIVEKIT_URL = process.env.LIVEKIT_URL; // e.g. ws://192.168.0.30:7880
const API_KEY = process.env.LIVEKIT_API_KEY;
const API_SECRET = process.env.LIVEKIT_API_SECRET;

if (!LIVEKIT_URL || !API_KEY || !API_SECRET) {
  console.error(
    "Missing LIVEKIT_URL / LIVEKIT_API_KEY / LIVEKIT_API_SECRET env vars. " +
      "Copy .env.example to .env and source it (see README.md)."
  );
  process.exit(1);
}

const app = express();
app.use(express.json());

// --- in-memory dev state (restart loses everything, by design) ------------

/** aetherId -> {aetherId, displayName, telegramUserId, lastSeen} */
const directory = new Map();
/** inviteId -> {inviteId, from, to, isVideo, roomName, roomId, state, createdAt} */
const invites = new Map();
/** roomId -> {roomId, roomName, state} */
const rooms = new Map();

const randomId = (prefix) => `${prefix}-${crypto.randomBytes(6).toString("hex")}`;
const randomKey = () => crypto.randomBytes(32).toString("base64");

async function mintToken(identity, roomName, minutes = 10) {
  const token = new AccessToken(API_KEY, API_SECRET, { identity, ttl: `${minutes}m` });
  token.addGrant({
    roomJoin: true,
    room: roomName,
    canPublish: true,
    canSubscribe: true,
    canPublishData: true,
  });
  return token.toJwt(); // async in livekit-server-sdk v2
}

async function joinPayload(roomId, roomName, identity, e2eeKey) {
  return {
    roomId,
    url: LIVEKIT_URL,
    token: await mintToken(identity, roomName),
    e2eeKey,
  };
}

// --- identity directory ---------------------------------------------------

app.post("/register", (req, res) => {
  const { aetherId, displayName, telegramUserId } = req.body || {};
  if (!aetherId) return res.status(400).json({ error: "aetherId required" });
  directory.set(aetherId, {
    aetherId,
    displayName: displayName || aetherId,
    telegramUserId: telegramUserId != null ? Number(telegramUserId) : null,
    lastSeen: Date.now(),
  });
  res.json({ ok: true });
});

app.get("/capabilities", (req, res) => {
  const entry = directory.get(req.query.aetherId);
  res.json({ callable: entry != null });
});

app.get("/lookup", (req, res) => {
  const telegramUserId = Number(req.query.telegramUserId);
  if (!Number.isFinite(telegramUserId) || telegramUserId <= 0) {
    return res.json({ aetherId: null });
  }
  for (const entry of directory.values()) {
    if (entry.telegramUserId === telegramUserId) {
      return res.json({
        aetherId: entry.aetherId,
        displayName: entry.displayName,
      });
    }
  }
  res.json({ aetherId: null });
});

// --- calls ----------------------------------------------------------------

app.post("/call/invite", async (req, res) => {
  const { from, to, isVideo } = req.body || {};
  const toEntry = directory.get(to);
  if (!toEntry) return res.status(404).json({ error: "recipient not registered" });

  const inviteId = randomId("inv");
  const roomName = randomId("aether");
  const roomId = crypto.randomBytes(8).toString("hex");
  const e2eeKey = randomKey(); // DEV-ONLY distribution; see header comment.

  const invite = {
    inviteId,
    from,
    to,
    isVideo: !!isVideo,
    roomName,
    roomId,
    e2eeKey,
    state: "ringing",
    createdAt: Date.now(),
  };
  invites.set(inviteId, invite);
  rooms.set(roomId, { roomId, roomName, state: "ringing" });

  const fromEntry = directory.get(from) || { aetherId: from, displayName: from };
  res.json({
    inviteId,
    join: await joinPayload(roomId, roomName, from, e2eeKey),
    fromName: fromEntry.displayName,
  });
});

app.get("/call/incoming", (req, res) => {
  const aetherId = req.query.aetherId;
  for (const invite of invites.values()) {
    if (
      invite.to === aetherId &&
      invite.state === "ringing" &&
      Date.now() - invite.createdAt < 60_000
    ) {
      const fromEntry = directory.get(invite.from) || { displayName: invite.from };
      return res.json({
        invite: {
          inviteId: invite.inviteId,
          from: invite.from,
          fromName: fromEntry.displayName,
          isVideo: invite.isVideo,
          telegramUserId: (directory.get(invite.from) || {}).telegramUserId ?? null,
        },
      });
    }
  }
  res.json({ invite: null });
});

app.post("/call/accept", async (req, res) => {
  const invite = invites.get(req.body?.inviteId);
  if (!invite) return res.status(404).json({ error: "unknown invite" });
  if (invite.state !== "ringing") return res.status(409).json({ error: `invite is ${invite.state}` });
  invite.state = "accepted";
  rooms.get(invite.roomId).state = "active";
  res.json({ join: await joinPayload(invite.roomId, invite.roomName, invite.to, invite.e2eeKey) });
});

app.post("/call/decline", (req, res) => {
  const invite = invites.get(req.body?.inviteId);
  if (!invite) return res.status(404).json({ error: "unknown invite" });
  invite.state = "declined";
  res.json({ ok: true });
});

app.post("/call/complete", (req, res) => {
  const room = rooms.get(req.body?.roomId);
  if (room) room.state = "ended";
  res.json({ ok: true });
});

// --- observability (safe metadata only) ------------------------------------

app.get("/health", (_req, res) => {
  res.json({
    ok: true,
    livekitUrl: LIVEKIT_URL,
    registered: directory.size,
    activeRooms: [...rooms.values()].filter((r) => r.state === "active").length,
  });
});

app.listen(PORT, "0.0.0.0", () => {
  console.log(`aether-call-service listening on 0.0.0.0:${PORT} (livekit: ${LIVEKIT_URL})`);
});
