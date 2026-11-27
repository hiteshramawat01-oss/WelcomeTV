# Developing

## Set up

**Android (`tv`, `host`, `shared`, `updater`)**
- Android Studio (its bundled JDK works). For the command line on this Mac:
  `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`
- Android SDK: platform **37.2**, build-tools 36, platform-tools (`sdkmanager "platforms;android-37.2" "build-tools;36.0.0" "platform-tools"`).
- `local.properties` with `sdk.dir=…` (Android Studio creates it).

**Samsung (`samsung-tv`)**
- VS Code + the **Tizen** extension (Tizen Studio is deprecated). It installs the tools used here:
  - `tz` (pack, sign): `~/.tizen-extension-platform/server/sdktools/data/tools/tizen-core/tz`
  - `sdb` (connect, install): `~/tizentv-tools/sdb/sdb`
- A **Samsung** certificate profile (an ordinary Tizen certificate is rejected by the TV). Ours is
  named "HItesh Samsung" and is tied to the TV's DUID. A new TV must be added to the certificate.
- TV in Developer mode with this Mac's IP as the host. Details: [samsung-tv/README.md](../samsung-tv/README.md).

## Build and test

| Command | Does |
|---|---|
| `./gradlew lint test` | Android lint (fails on errors), shared unit tests, TV Compose UI tests (Robolectric, 1920×1080, D-pad focus) |
| `./gradlew :tv:installSideloadDebug :host:installSideloadDebug` | Install on a connected device or emulator |
| `./gradlew :host:assembleSideloadRelease :tv:assembleSideloadRelease` | Release APKs (signed if the `GE_SIDELOAD_*` env vars are set) |
| `./gradlew bundlePlayRelease` | Google Play AABs |
| `./gradlew :tv:testPlayDebugUnitTest --tests '*ScreenshotTest'` | Renders the TV screens to `tv/build/screenshots/` |
| Open `samsung-tv/index.html?demo` (or `?demo=night`, `?demo=plain`) in a browser at 1920×1080 | Preview the Samsung screen with sample content |

Opt-in tests (skipped unless the variable is set):
- `GE_SAMSUNG_TV=<ip> ./gradlew :shared:test`: talks to a real Samsung TV (`SamsungChannelTest`).
- `QR_MATRICES=<file>`: checks `qr.js` output decodes with ZXing (`QrJsCompatTest`; make the file with `node tools/qr-matrices.js samsung-tv/qr.js out.json`).

Manual release checklist: [TESTING.md](../TESTING.md).

## Run

- **Android TV emulator:** see the root [README](../README.md#run-on-an-emulator). The emulator is behind
  NAT, so a real phone can't reach it; test pairing with a real TV or two emulators.
- **Phone:** both apps run on a phone. On a phone, long-press opens Host Mode on the TV app.
- **Samsung TV:** `tools/samsung-autoupdate.sh --once --verbose` builds the current `origin/main` and
  installs it. To try uncommitted changes, pack and install by hand as in [samsung-tv/README.md](../samsung-tv/README.md).

## Releasing

| Target | How | Who installs |
|---|---|---|
| Phone + Android TV (sideload) | Push to `main`. `sideload-release.yml` publishes release `build-<run>` (version `1.2.<run>`, code `100+run`) with `update.json`. Docs-only, `samsung-tv/` and `tools/` changes don't trigger it | User taps **Update**, then **Install** |
| Samsung TV | Push to `main` touching `samsung-tv/`. The Mac LaunchAgent installs it within 5 minutes | Automatic (Mac awake, same Wi-Fi) |
| Google Play | Push a tag `v1.2.3`. `release.yml` builds signed `play` AABs. Upload per [PLAY_RELEASE.md](../PLAY_RELEASE.md) | Play Store |

GitHub secrets (Settings › Secrets and variables › Actions):

| Secret | Used for |
|---|---|
| `GE_SIDELOAD_KEYSTORE_BASE64`, `GE_SIDELOAD_STORE_PASSWORD`, `GE_SIDELOAD_KEY_ALIAS`, `GE_SIDELOAD_KEY_PASSWORD` | Signing sideload APKs. Currently this Mac's Android debug key; changing it means every device must uninstall and reinstall once |
| `GE_UPLOAD_KEYSTORE_BASE64`, `GE_UPLOAD_STORE_PASSWORD`, `GE_UPLOAD_KEY_ALIAS`, `GE_UPLOAD_KEY_PASSWORD` | Play upload key |

Mac auto-updater: install or remove with `tools/install-samsung-autoupdate.sh`. Settings in
`~/.config/guestexperience/samsung.env` (TV IP, certificate profile, tool paths). Log:
`~/Library/Logs/GuestExperience/samsung-update.log`.

## Troubleshooting

| Symptom | Cause → fix |
|---|---|
| Phone can't find the TV | Different or "guest/isolated" Wi-Fi → use the main network. Samsung: add it by IP on the pairing screen. Android TV: the app must be open |
| Samsung pairing never answers | WebSocket client offering `permessage-deflate` (the TV ignores it silently) → `MiniWebSocket` never sends extensions; keep it that way |
| Samsung app can't join its channel | `config.xml` must allow `http://127.0.0.1:8001` and `ws://127.0.0.1:8001` (with the port) |
| "The TV didn't accept <field>" | The TV's validation rejected it. Compare `ContentValidator.kt` with `samsung-tv/content.js` `problemIn` |
| Samsung install: "Invalid certificate chain" | Use a Samsung certificate profile that includes the TV's DUID |
| Samsung app lost its name and pairings | `tz install` uninstalls first. Use `sdb push` + `vd_appinstall` (what the auto-updater does) |
| Real-TV Kotlin test: "No route to host" | macOS Local Network privacy blocks the JVM → allow Android Studio / Terminal in System Settings › Privacy & Security › Local Network |
| Plain `http://` call fails on Android | Cleartext is blocked → `SamsungChannelDialer.httpRequest` uses a raw socket for the TV's local API |
| App says update failed: different signer | The installed APK was signed with another key → uninstall once, install from the latest release |
