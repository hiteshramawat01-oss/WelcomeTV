# Google Play release checklist

Two apps, two Play Console entries: **Guest Experience TV** (`com.guestexperience.tv`) and
**Guest Experience Host** (`com.guestexperience.host`). Do the TV app first. The Host app is only useful
once the TV app is live.

## 0. Before you start

- [ ] Google Play developer account (one-time fee; organisation accounts need a D-U-N-S number).
- [ ] Upload key created and backed up (see README › Upload key). **Losing it means a key reset request.**
- [ ] Privacy policy published at a public URL, e.g. GitHub Pages or your website, from `PRIVACY_POLICY.md`.
      Fill in `[YOUR NAME OR COMPANY]` and `[YOUR CONTACT EMAIL]` first.
- [ ] Check application IDs: `com.guestexperience.*` cannot be changed after the first upload.
      Change them in `tv/build.gradle.kts` / `host/build.gradle.kts` now if you want your own domain.
- [ ] `./gradlew lint test bundlePlayRelease` passes with the upload key configured.
- [ ] Manual test plan (`TESTING.md`) done on the Android TV API 31 2 GB emulator, Google TV API 34 and one real TV.

## 1. Create the app (TV)

- [ ] Play Console › Create app › name "Guest Experience TV", App, Free, accept declarations.
- [ ] **Play App Signing**: keep "Google-generated key" (default). Your local key is the upload key.

## 2. Store listing (Grow › Store presence › Main store listing)

- [ ] Title, short and full description from `store-listing/listing.md`.
- [ ] App icon 512×512: `store-listing/icon-512.png`.
- [ ] Feature graphic 1024×500: `store-listing/feature-graphic-1024x500.png`.
- [ ] **Android TV › TV banner 1280×720**: `store-listing/tv-banner-1280x720.png`.
- [ ] **Android TV screenshots** (1920×1080, at least 1): `store-listing/screenshots-tv/01…05`.
      Retake on a real TV/emulator if you change the design (`ScreenshotTest` regenerates them).
- [ ] Phone screenshots: Play requires them for the default form factor. Take 2+ landscape
      shots of the TV app on a phone, or, if you restrict phones (step 6), follow Console guidance.
- [ ] Category: Travel & Local. Contact email. Privacy policy URL.

## 3. App content (Policy › App content)

- [ ] Privacy policy URL.
- [ ] Ads: **No ads**.
- [ ] App access: **All functionality available without special access**. Add a note for reviewers:
      "On first launch the app asks you to create a 4–6 digit PIN. Any PIN works. Host Mode: hold OK
      for 3 seconds on the welcome screen, or choose the gear tile."
- [ ] Content rating questionnaire: Utility / Productivity / Other. No violence, no user-generated
      content shared with others, no purchases → expected rating: Everyone / PEGI 3 / 3+.
- [ ] Target audience: 18+ (hosts). Not designed for children.
- [ ] **Data safety**: answers from `DATA_SAFETY.md`.
- [ ] Government apps / financial features / health: No.

## 4. Opt in to Android TV (Test and release › Advanced settings › Form factors)

- [ ] Add form factor **Android TV**. Agree to the TV requirements.
- [ ] Confirm the TV checklist, which this build already meets:
  - `LEANBACK_LAUNCHER` activity, 320×180 `android:banner` with the app name (`res/drawable-xhdpi/banner.png`)
  - `android.software.leanback` and `android.hardware.touchscreen` declared `required="false"`
  - No permissions that imply unsupported hardware (only INTERNET, ACCESS_NETWORK_STATE)
  - Full D-pad navigation, visible focus, no dead ends (see `TESTING.md` §2–3)
  - Back from the main screen exits the app. No self-updating, no APK downloads
  - `targetSdk` 35 (Play's TV floor is 34)
- [ ] Choose whether TV releases go out on the same track as phone releases. For a TV-first app,
      the default "shared track" is fine.

## 5. Internal testing

- [ ] Test and release › Testing › Internal testing › Create release.
- [ ] Upload `tv/build/outputs/bundle/playRelease/tv-play-release.aab` (signed with the upload key).
- [ ] Release notes: "First release."
- [ ] Add testers (email list), open the opt-in link on a Google account that is signed in on a
      Google TV / Android TV, install from Play on the TV, run `TESTING.md` §1–3 again.
- [ ] Check Console › Android vitals and pre-launch report for crashes or accessibility warnings.

## 6. Device catalogue (optional)

- [ ] Phones: the TV app also installs on phones (useful for testing). To sell it as TV-only,
      exclude phones/tablets in Release › Device catalogue, or keep them and say so in the listing.

## 7. TV review and production

- [ ] Promote the tested release to **Closed testing**. New personal developer accounts must run
      a closed test with at least 12 testers for 14 days before production access.
- [ ] Promote to **Production**. The Android TV form-factor review runs separately and usually takes
      a few days. Watch Inbox / Publishing overview for "Android TV: not approved" and the reasons
      (most common: missing banner, D-pad dead ends, phone-only features, wrong screenshots).
- [ ] Once approved, the app appears in the Play Store on Google TV / Android TV.

## 8. Host phone app

Repeat steps 1–5 for **Guest Experience Host** with `host/build/outputs/bundle/playRelease/host-play-release.aab`,
**without** step 4 (it is a phone app). Use phone screenshots of the Stay, Wi-Fi and pairing screens.
`targetSdk` is 36, as Play requires for phone apps from August 2026.

## 9. Every update

- [ ] Bump `versionCode` (and `versionName`) in the module's `build.gradle.kts`.
- [ ] `git tag v1.0.1 && git push --tags` → GitHub Actions builds both signed AABs (Actions › Artifacts).
- [ ] Upload to internal testing → test on a TV → promote.
- [ ] If the content JSON changes, only **add** fields with defaults; bump `schemaVersion` only with a migration.
