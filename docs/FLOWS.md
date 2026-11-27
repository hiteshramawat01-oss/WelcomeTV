# Flows

## 1. First run on an Android TV

```mermaid
flowchart LR
  A[Install app] --> B[Welcome] --> C[Set 4–6 digit PIN] --> D[Confirm PIN] --> E[Property name and city] --> F[Wi-Fi name and password] --> G[Preview] --> H[Guest screen]
```

Code: `tv/.../ui/onboarding/OnboardingScreen.kt` (`OnboardingStep`). The Samsung app has no setup: it
opens on the welcome screen, and the host sets the name and message by remote or from the phone.

## 2. Pairing a phone with a TV (once per phone)

```mermaid
sequenceDiagram
  actor Host
  participant TV as TV app
  participant Phone as Host app
  Host->>TV: Host Mode › Pair a phone (Samsung: ⚙ › Pair a phone)
  TV->>TV: Show 6-digit code (5 min, 5 tries)
  Phone->>Phone: Find TVs (_guesttv._tcp for Android, _samsungmsf._tcp for Samsung)
  Host->>Phone: Pick the TV, type the code
  Phone->>TV: pair_hello (phone public key)
  TV->>Phone: pair_challenge (TV public key, nonce)
  Note over Phone,TV: Both compute ECDH secret → pair key bound to the code
  Phone->>TV: pair_confirm (HMAC proves the phone knows the code)
  TV->>Phone: pair_result ok (HMAC proves the TV knows it too)
  Phone->>Phone: Save TV + pair key (encrypted)
  TV->>TV: Save phone + pair key
```

Transport differs, messages don't:
- **Android TV:** the phone opens a TCP connection to the port the TV announced (`TcpDialer`, `TvSyncServer`).
- **Samsung:** the phone opens a WebSocket to `ws://<tv>:8001/api/v2/channels/com.guestexperience.welcome`,
  starts the app if needed, and each message is an `ms.channel.emit` event named `guesttv`
  (`SamsungChannelDialer.kt` ↔ `samsung-tv/sync.js`).

## 3. Host edits the welcome and sends it

```mermaid
sequenceDiagram
  actor Host
  participant Phone as Host app
  participant TV as TV app
  Phone->>TV: secure{get_content}
  TV->>Phone: secure{content}
  Host->>Phone: Edit Stay / Wi-Fi / Property tabs
  Host->>Phone: Send to TV
  Phone->>Phone: ContentValidator.validate (stop and show the field if bad)
  Phone->>TV: secure{put_content}
  TV->>TV: Decrypt, check counter, validate again
  alt valid
    TV->>TV: Save, redraw the guest screen
    TV->>Phone: secure{saved}
  else invalid
    TV->>Phone: secure{rejected INVALID_CONTENT, field}
    Phone->>Host: "The TV didn't accept <field>"
  end
```

"Next guest" on the Stay tab clears the guest name, message and check-out, ready for the next booking.

## 4. What the guest sees

```mermaid
flowchart TB
  G[Guest screen] -->|every minute| C[Clock, date, greeting refresh]
  G -->|every 12 s| T[Ticker: next house note / place / host contact]
  G -->|OK on Wi-Fi card| Q[Large Wi-Fi QR] -->|OK or Back| G
  G -->|Android: hold OK 3 s or ⚙ tile| P[PIN] --> H[Host Mode]
  G -->|Samsung: Up/Right to ⚙, OK| S[Settings: name, message, Pair a phone]
  G -->|Android: idle| D[Screensaver: GuestDreamService]
```

## 5. Update: Host phone app and Android TV app

```mermaid
sequenceDiagram
  actor Dev
  participant GH as GitHub Actions
  participant Rel as GitHub Release (latest)
  participant App as Installed app (sideload)
  Dev->>GH: git push main (code change, not docs-only)
  GH->>GH: lint, test, build sideload APKs (versionCode 100+run)
  GH->>Rel: Publish build-N: both APKs + update.json (sha256, size)
  App->>Rel: On open, at most every 6 h: GET update.json
  App->>App: Newer versionCode? Show "Update available"
  Dev->>App: Tap Update
  App->>Rel: Download APK (HTTPS, this repo only, ≤100 MB)
  App->>App: Check SHA-256, package name, signing key
  App->>App: PackageInstaller → Android asks "Install?" → user taps Install
```

Code: `.github/workflows/sideload-release.yml`, `updater/.../GitHubUpdater.kt`,
`shared/.../update/UpdateManifest.kt`. Android always needs that last tap for sideloaded apps.

## 6. Update: Samsung TV

```mermaid
sequenceDiagram
  actor Dev
  participant GH as GitHub
  participant Mac as Mac LaunchAgent (every 5 min)
  participant TV as Samsung TV (Developer mode)
  Dev->>GH: git push main (touching samsung-tv/)
  Mac->>GH: git fetch (own clone in ~/Library/Application Support/GuestExperience)
  Mac->>Mac: samsung-tv/ changed since last installed commit?
  Mac->>Mac: Stamp version 1.2.<commits>, zip, tz pack with Samsung certificate
  Mac->>TV: sdb connect :26101, sdb push .wgt
  Mac->>TV: vd_appinstall (in-place upgrade, keeps localStorage)
  Mac->>TV: Launch app, record commit
```

Code: `tools/samsung-autoupdate.sh`. Log: `~/Library/Logs/GuestExperience/samsung-update.log`.
If the TV is off, it tries again on the next run.
