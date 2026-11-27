# Extending and scaling

Golden rule: **the TV owns the truth.** The phone edits, the TV validates and stores. Any rule lives
in `shared/` first, then is mirrored in `samsung-tv/` JavaScript.

## Add a content field

Example: add "breakfast time".

| Step | File |
|---|---|
| 1. Add the field **with a default** | `shared/.../model/PropertyContent.kt` |
| 2. Add its limit and check | `shared/.../validation/Validators.kt`, `ContentValidator.kt` |
| 3. Mirror the check (same field name, same order) | `samsung-tv/content.js` › `problemIn` |
| 4. Let the host edit it | `host/.../ui/HostScreens.kt` (the right `Tab`) |
| 5. Optionally edit it on the TV too | `tv/.../ui/host/HostScreen.kt` (a `HostSection`) |
| 6. Show it | `tv/.../ui/guest/GuestScreen.kt` and `samsung-tv/index.html` + `app.js` › `render()` |
| 7. Tests | `shared/src/test/.../model/ContentJsonTest.kt`, `validation/ValidatorsTest.kt`, `tv/src/test/.../ui/guest/GuestScreenTest.kt`, `ScreenshotTest.kt` |
| 8. If it is personal data | Update `PRIVACY_POLICY.md` and `DATA_SAFETY.md` |

No protocol change is needed: `put_content` carries the whole document, and older apps ignore the new field.

## Change the welcome design

1. Mock it first (the Design canvas artifact used for Welcome v2, or screenshots from `ScreenshotTest`).
2. Android TV: `tv/.../ui/guest/GuestScreen.kt` (layout), `ui/theme/Theme.kt` (colours, type),
   `ui/components/` (`UdaipurScene`, `QrCode`, `Clock`).
3. Samsung: `samsung-tv/index.html`, `style.css` (same palette tokens: ink `#0A0C0F`, paper `#F6F1E8`,
   gold `#D9B97F`), `scene.js` for the background art.
4. Keep both at 1920×1080 with a 5% overscan margin, and every focusable item reachable by D-pad.
5. Check: `ScreenshotTest` images vs `samsung-tv/index.html?demo` in a browser.

New background scene: add an id to `BuiltInScene` (`shared/.../model/PropertyContent.kt`), draw it in
`tv/.../ui/components/` and `samsung-tv/scene.js`, add a chip in `host/.../ui/HostScreens.kt`.

## Add a TV platform

Example: LG webOS, Fire TV, or a browser-based screen.

1. **Android-based (Fire TV, other Android TVs):** nothing to do; ship the `tv` APK.
2. **Can open a TCP socket:** implement the TV side of [PROTOCOL.md](PROTOCOL.md) and announce `_guesttv._tcp`.
   The phone works unchanged.
3. **Can't listen (web-runtime TVs, like Samsung):** find the platform's relay (Samsung: multiscreen channel).
   Then on the phone:
   - add a value to `TvKind` (`shared/.../sync/HostSyncClient.kt`),
   - write a `SyncDialer` like `SamsungChannelDialer.kt`,
   - teach `host/.../data/TvDiscovery.kt` to find it,
   - map it in `HostContainer.dialer()` (`host/.../HostApp.kt`).
   On the TV, port `samsung-tv/` (its `sync.js`, `content.js`, `qr.js` are plain JS and reusable).
4. Add an update path for it (see Samsung's `tools/samsung-autoupdate.sh` as a template).

## Scaling roadmap

In rough order of what to do first when this goes beyond one property.

| Need | What to do | Where to start |
|---|---|---|
| **Real signing key** (do first) | Create a release keystore, put it in the `GE_SIDELOAD_*` secrets, reinstall every device once. The debug key is shared with every Android SDK install and is not safe for distribution | `host/build.gradle.kts`, `tv/build.gradle.kts` (`signingConfigs`), `.github/workflows/sideload-release.yml` |
| Many TVs per property | Let the phone remember several TVs: `HostStore` holds one `PairedTv` today → make it a list, add a TV picker, and "send to all" | `host/.../data/HostStore.kt`, `HostViewModel.kt` |
| Many properties | Group TVs by property on the phone; content is already per TV | Same as above |
| Remote editing / cloud dashboard | Add a small backend that stores the same `PropertyContent` JSON per TV. TVs poll or subscribe for it; keep the local pairing for offline use. `updatedAt` is there to resolve conflicts | New module; reuse `ContentJson`, `ContentValidator` |
| Samsung without a developer Mac | Publish to the Samsung Seller Office (TV app store), or build `.wgt` in CI and install with a self-hosted runner on the property LAN | `samsung-tv/config.xml`, `tools/samsung-autoupdate.sh` |
| Google Play listing | Already prepared: `play` flavour, store assets, privacy docs | [PLAY_RELEASE.md](../PLAY_RELEASE.md), `store-listing/` |
| Languages | Strings are hard-coded English. Move to `strings.xml` (Android) and a JS dictionary (Samsung); add a `language` field to the content | `tv/src/main/res/`, `samsung-tv/content.js` |
| Booking integration (auto guest name) | A cloud step that writes `guestName` / `checkOut` from the booking system into the same JSON | After the cloud dashboard |

## Known limitations and tech debt

- Samsung keeps the Wi-Fi password unencrypted in `localStorage` (no keystore for Tizen web apps).
- Samsung validation in `content.js` is a hand-kept copy of `ContentValidator.kt`, and deliberately looser
  (property name and Wi-Fi optional). Any rule change must touch both.
- The phone is paired with one TV at a time.
- Samsung TVs must stay in Developer mode and depend on this Mac for updates.
- Sideload updates always need a tap on Android's Install dialog; silent updates need Play or device-owner mode.
- The pairing code could in theory be brute-forced by an active attacker on the LAN during its 5-minute window.
