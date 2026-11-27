# Phone ↔ TV protocol

Source of truth: `shared/.../sync/SyncProtocol.kt` (messages), `SyncCrypto.kt` (crypto),
`TvSyncServer.kt` (Android TV side), `HostSyncClient.kt` (phone side), `samsung-tv/sync.js`
(Samsung side). **Any change must be made in all of them together.**

## Discovery

| TV | How the phone finds it | Details |
|---|---|---|
| Android TV | DNS-SD `_guesttv._tcp`, announced only while the app is open | TXT `id` = TV id, `v` = protocol version (`SyncController.kt`, `TvDiscovery.kt`) |
| Samsung | DNS-SD `_samsungmsf._tcp` (every Samsung TV) | TXT `id`, `fn` (name), `se` (service URL). Or the host types the IP; `SamsungChannelDialer.lookup()` checks `http://<ip>:8001/api/v2/` |

## Transport

| TV | Connection | Framing |
|---|---|---|
| Android TV | One TCP connection per request, 15 s timeout | 4-byte big-endian length + UTF-8 JSON, max 512 KB (`FrameIO.kt`) |
| Samsung | WebSocket `ws://<tv>:8001/api/v2/channels/com.guestexperience.welcome?name=<base64 phone name>`. The phone starts the app with `POST /api/v2/applications/GuestExpTV.GuestWelcome` if it isn't running, then waits for the app to join as host | `{"method":"ms.channel.emit","params":{"event":"guesttv","to":"host","data":"<JSON message>"}}`. The WebSocket client **must not** offer `permessage-deflate` |

Every message is JSON with a `type` field.

## Pairing

| # | Direction | `type` | Fields |
|---|---|---|---|
| 1 | phone → TV | `pair_hello` | `protocol` (1), `phoneId`, `phoneName`, `phonePublicKey` (P-256, X.509, base64) |
| 2 | TV → phone | `pair_challenge` | `tvId`, `tvName`, `tvPublicKey`, `nonce` |
| 3 | phone → TV | `pair_confirm` | `mac` = HMAC(pairKey, `confirm|phone|<nonce>`) |
| 4 | TV → phone | `pair_result` | `ok`, `mac` = HMAC(pairKey, `confirm|tv|<nonce>`), or `error` |

`pairKey = HMAC-SHA256(ECDH secret, "guesttv-pair-v1|<code>|<phonePub>|<tvPub>|<nonce>")`.
A wrong code gives a different key, so the MAC check fails. Code: 6 digits, 5-minute life, 5 attempts.

## After pairing

`secure` envelope: `phoneId`, `counter`, `iv`, `ciphertext` (AES-256-GCM with the pair key, base64).
AAD = `p2t|<phoneId>|<counter>` (phone → TV) or `t2p|<phoneId>|<counter>` (TV → phone).
The TV rejects any counter that is not larger than the last one it accepted from that phone.

| Command (decrypted) | Reply (decrypted) |
|---|---|
| `{"type":"get_content"}` | `{"type":"content","content":{…}}` |
| `{"type":"put_content","content":{…}}` | `{"type":"saved","updatedAt":…}` or `{"type":"rejected","error":"INVALID_CONTENT","field":"…"}` |

Error codes (`SyncError`): `BAD_PROTOCOL`, `NO_PAIRING_CODE`, `WRONG_CODE`, `TOO_MANY_ATTEMPTS`,
`UNKNOWN_PHONE`, `REPLAY`, `DECRYPT_FAILED`, `INVALID_CONTENT`, `UNREACHABLE` (phone side), `INTERNAL`.
Unencrypted errors come back as `{"type":"error","error":"…"}`.

## Versioning rules

- `PropertyContent`: only **add** fields, always with a default. Never rename or change a field's meaning.
  Unknown fields and enum values are ignored (`ContentJson`). A breaking change means bumping
  `schemaVersion` and adding a migration in `ContentJson.migrate`.
- Protocol: bump `SyncProtocol.VERSION` (and `PROTOCOL_VERSION` in `sync.js`) only for a breaking
  change, and keep accepting the old version until all phones are updated.

## `update.json` (GitHub Releases)

Published by `.github/workflows/sideload-release.yml` at
`https://github.com/hiteshramawat01-oss/WelcomeTV/releases/latest/download/update.json`:

```json
{
  "schema": 1,
  "commit": "<git sha>",
  "host": { "versionCode": 101, "versionName": "1.2.1", "apk": "https://github.com/…/GuestExperienceHost-1.2.1.apk", "sha256": "…", "size": 2302667 },
  "tv":   { "versionCode": 101, "versionName": "1.2.1", "apk": "https://github.com/…/GuestExperienceTV-1.2.1.apk",   "sha256": "…", "size": 2353963 }
}
```

Parsed by `shared/.../update/UpdateManifest.kt`. The app accepts an `apk` URL only if it is HTTPS and in
this repository's releases (`AppRelease.isTrusted`).
