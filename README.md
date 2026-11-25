# Guest Experience TV

A welcome screen for holiday rentals and small hotels, for Android TV / Google TV, plus a phone app
for the host.

| Module | What it is | Package | min / target SDK |
|---|---|---|---|
| `tv` | **Guest Experience TV**: the app on the TV. Guest welcome screen, Wi-Fi QR code, check-out, house notes, screensaver, PIN-protected Host Mode | `com.guestexperience.tv` | 26 / 35 |
| `host` | **Guest Experience Host**: phone app that edits the TV over the same Wi-Fi | `com.guestexperience.host` | 26 / 36 |
| `shared` | Pure Kotlin (no Android): content model, Wi-Fi QR payload, QR encoder, validation, PIN hashing and lockout, pairing and sync protocol | – | – |

Everything stays inside the property: no accounts, no analytics, no ads, no cloud. The phone and TV
talk only over the local network, encrypted after a one-time pairing code.

## Requirements

- Android Studio (bundles a suitable JDK), or JDK 17+.
- Android SDK with **platform 37.2** (current AndroidX releases require compiling against 37.1+),
  platforms 35/36, build-tools 36, platform-tools.

```bash
sdkmanager "platform-tools" "platforms;android-37.2" "build-tools;36.0.0"
```

Create `local.properties` if Android Studio has not:

```properties
sdk.dir=/Users/<you>/Library/Android/sdk
```

## Build and test

```bash
./gradlew lint test bundleRelease
```

- `lint`: Android lint for both apps (fails on errors).
- `test`: shared unit tests (QR payload escaping, QR encode→decode, validators, PIN hashing/lockout,
  JSON forward-compatibility, pairing and sync over real sockets) plus the TV's Compose UI tests
  (Robolectric, 1920×1080, D-pad focus).
- `bundleRelease`: `tv/build/outputs/bundle/release/tv-release.aab` and
  `host/build/outputs/bundle/release/host-release.aab` (signed if an upload key is configured, see below).

TV screenshots at 1920×1080 are rendered by `ScreenshotTest` into `tv/build/screenshots/`
(copies for the Play listing are in `store-listing/screenshots-tv/`). The banner, store icon and
listing graphics are drawn by `java tools/RenderBrandAssets.java` (run from the repo root).

## Run on an emulator

Create the two TV emulators from the brief (Android Studio › Device Manager works too):

```bash
sdkmanager "emulator" "system-images;android-31;android-tv;arm64-v8a" "system-images;android-34;google-tv;arm64-v8a"
```

```bash
avdmanager create avd -n tv31_2gb -k "system-images;android-31;android-tv;arm64-v8a" -d tv_1080p
```

Then set `hw.ramSize=2048` in `~/.android/avd/tv31_2gb.avd/config.ini`.

```bash
avdmanager create avd -n googletv34 -k "system-images;android-34;google-tv;arm64-v8a" -d tv_1080p
```

```bash
emulator -avd tv31_2gb
```

```bash
./gradlew :tv:installDebug
```

Use the emulator's D-pad (arrow keys and Enter on your keyboard). Measure cold start with:

```bash
adb shell am start -W -S com.guestexperience.tv/.ui.MainActivity
```

## Run on your phone

Both apps run on a phone. On the phone, enable **Developer options › USB debugging**, connect it,
and run either app from Android Studio, or:

```bash
./gradlew :host:installDebug :tv:installDebug
```

On a phone the TV app opens in landscape. Long-press anywhere to open Host Mode (on a TV: hold OK
for 3 seconds, or choose the ⚙ tile). Test remote-control focus on a TV emulator or a real TV.

To try pairing, run the TV app on an emulator or TV and the Host app on your phone, **on the same
Wi-Fi network**. On the TV: Host Mode › Pair a phone. In the Host app: pick the TV and enter the
code. Guest or "isolated" Wi-Fi networks that block devices from seeing each other will stop this
from working. Use the property's main network.

> The Android emulator sits behind its own NAT, so a phone on your Wi-Fi cannot reach an emulated
> TV directly. Test pairing with two emulators on the same computer, or with a real TV.

## Upload key and signing

Google Play signs the app you ship (Play App Signing). You sign uploads with an **upload key**. Create one
once and keep it safe (a password manager plus an offline backup):

```bash
keytool -genkeypair -v -keystore upload-keystore.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000
```

Local release builds read `keystore.properties` in the repo root (git-ignored):

```properties
storeFile=upload-keystore.jks
storePassword=…
keyAlias=upload
keyPassword=…
```

CI reads the same values from secrets (see `.github/workflows/release.yml`):
`GE_UPLOAD_KEYSTORE_BASE64` (output of `base64 -i upload-keystore.jks`), `GE_UPLOAD_STORE_PASSWORD`,
`GE_UPLOAD_KEY_ALIAS`, `GE_UPLOAD_KEY_PASSWORD`. Pushing a tag like `v1.0.0` builds both signed AABs.

Without a key, `bundleRelease` still produces unsigned bundles (fine for checking, not for upload).

## How it works

- **Content** is one versioned JSON document (`PropertyContent`, `schemaVersion = 1`). Unknown fields
  and enum values are ignored, so older apps can read newer documents. A future cloud dashboard can
  sync the same JSON.
- **On the TV** it is stored in DataStore. The Wi-Fi password and pairing keys are encrypted with an
  AES-256-GCM key held in the Android Keystore. The Host PIN is stored only as a salted PBKDF2-SHA256 hash.
  5 wrong PINs → 30 s wait, doubling up to 15 min. Backups are disabled.
- **Sync** (see `shared/.../sync/SyncProtocol.kt`): the TV announces `_guesttv._tcp` on the Wi-Fi
  only while the app is open. Pairing uses an ephemeral ECDH (P-256) exchange bound to the 6-digit
  code shown on the TV (5-minute expiry, 5 attempts, single use). After pairing, each request is
  AES-GCM encrypted with a strictly increasing counter (no replay). Limitation: someone actively
  attacking the property network during the 5-minute pairing window could in theory guess the
  code. Passive listeners learn nothing.
- **Screensaver**: `GuestDreamService`. Many recent Google TV devices no longer let users pick
  third-party screensavers in Settings. There, a host can set it once with:

  ```bash
  adb shell settings put secure screensaver_components com.guestexperience.tv/.dream.GuestDreamService
  ```

- **No self-updates**: no install-packages permission, no downloaders. Updates come only from Google Play.

## Project layout

```
shared/src/main/kotlin/com/guestexperience/shared/
  model/        PropertyContent, ContentJson (versioned, forward-compatible)
  wifi/         WifiQrPayload (WIFI:T:…;S:…;P:…;H:…;;)
  qr/           QrEncoder (ZXing core)
  validation/   Validators, ContentValidator
  security/     PinHasher, PinLockoutPolicy
  display/      Greeting
  sync/         SyncProtocol, SyncCrypto, FrameIO, PairingCodeManager, TvSyncServer, HostSyncClient
tv/src/main/java/com/guestexperience/tv/
  data/         TvRepository (DataStore), KeystoreCipher, ImageStore, SyncController (NSD)
  di/           AppContainer (manual DI)
  ui/guest      GuestScreen
  ui/host       PinScreen, HostScreen (+ Pair a phone)
  ui/onboarding OnboardingScreen
  ui/components Controls (TV text field, buttons), QrCode, Clock, UdaipurScene, GuestBackground, PinPad
  dream/        GuestDreamService
host/src/main/java/com/guestexperience/host/
  data/         HostStore, TvDiscovery (NSD), KeystoreCipher
  ui/           HostViewModel, HostScreens (pair, stay, Wi-Fi, property, TV)
```

Fonts: Cormorant Garamond and Inter, both under the SIL Open Font License (`licenses/`).
