# GhostWave

A production-ready, end-to-end encrypted Android P2P messaging and calling app.

All communications are encrypted using the Signal Protocol (X3DH + Double Ratchet). There are no central servers that store your messages.

---

## Security model

| What | How |
|------|-----|
| Message encryption | Signal Protocol — X3DH key exchange + Double Ratchet |
| Call media | WebRTC with mandatory DTLS-SRTP |
| Local database | SQLCipher AES-256 |
| Key storage | Android Keystore (hardware-backed on supported devices) |
| Access control | Invite-only promo gate with HMAC integrity protection |
| Screenshots | `FLAG_SECURE` on all windows |

**Security rules enforced in code:**
- Plaintext is never sent over any network connection
- Messages are never stored unencrypted in Room or SharedPreferences
- Message content is never included in FCM payloads
- Peers are not trusted until X3DH handshake completes
- Non-DTLS-SRTP WebRTC connections are rejected at config level
- Private keys never leave the Android Keystore
- DTLS fingerprints are always verified against the Signal-authenticated value
- IVs and nonces always use `SecureRandom`
- Key material is zeroed from memory after use
- Safety number mismatch triggers a visible warning

---

## Features

- **Invite-only access gate** — SHA-256 hashed code list; server validation over HTTPS; rate limiting (5 → 15 min lockout, 10 → 60 min, 20 → permanent)
- **P2P messaging** — Signal-encrypted messages over WebRTC data channels; offline queue via IPFS
- **Audio and video calls** — DTLS-SRTP enforced; foreground service with notification controls
- **Disappearing messages** — configurable per-contact timer; WorkManager sweep
- **Safety numbers** — verify contact identity out-of-band
- **App lock** — biometric or PIN via `BiometricPrompt`
- **QR code contact exchange** — scan to add contacts

---

## Architecture

```
GhostWaveApplication
  ├── Hilt DI graph (SingletonComponent)
  ├── SQLCipher Room database (AES-256)
  ├── Signal Protocol store (libsignal-android 0.44.0)
  ├── WebRTC peer connections (DTLS-SRTP)
  ├── DataChannel messaging layer
  └── IPFS offline queue (OkHttp → Kubo RPC)

UI layer: Jetpack Compose + Navigation + HiltViewModel
```

---

## Build

### Requirements

- Android Studio Hedgehog or later
- JDK 17
- Android SDK 34

### Local build

```bash
# Clone
git clone https://github.com/emanthen/GhostWave.git
cd GhostWave

# Add your google-services.json
cp app/google-services.json.example app/google-services.json
# edit app/google-services.json with your Firebase project values

# Debug APK
./gradlew assembleDebug

# Unit tests
./gradlew testDebugUnitTest

# Lint
./gradlew lintDebug
```

### Release signing

Set environment variables before building release:

```bash
export KEYSTORE_PATH=/path/to/release.keystore
export KEYSTORE_PASSWORD=...
export KEY_ALIAS=...
export KEY_PASSWORD=...
./gradlew assembleRelease
```

---

## CI / CD

GitHub Actions runs three jobs on every push to `main`:

| Job | Trigger | What it does |
|-----|---------|--------------|
| `unit-test` | every push | `testDebugUnitTest` + `lintDebug` |
| `build-debug` | after unit-test passes | `assembleDebug`, uploads APK artifact |
| `build-release` | `main` branch only | signs with Keystore secrets |

Required GitHub Secrets for release builds:
- `KEYSTORE_BASE64` — base64-encoded keystore file
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

---

## Project structure

```
app/src/main/java/com/ghostwave/app/
  ├── call/          # WebRTC, CallManager, CallService, notifications
  ├── crypto/        # KeyStoreManager, MessageEncryptor, Signal store
  ├── data/          # Room DAOs, repositories, models
  ├── di/            # Hilt modules
  ├── messaging/     # DataChannel, offline queue, FCM, WorkManager
  ├── navigation/    # NavGraph, Screen routes
  ├── p2p/           # IPFS, mDNS discovery, peer registry, signaling
  ├── promo/         # Access gate (validator, repository, gate, types)
  ├── push/          # FCM service and token management
  ├── security/      # App lock manager
  ├── ui/            # Compose screens and ViewModels
  └── util/          # GW-ID derivation, QR codec
```

---

## License

Private — all rights reserved.
