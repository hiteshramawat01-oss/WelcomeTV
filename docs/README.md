# Guest Experience TV: documentation

**What it is:** a welcome screen for holiday rentals and small hotels. The TV in the room greets the
guest by name, shows the host's message, the time, check-out, the Wi-Fi (with a QR code to join),
house notes and nearby places. The host edits all of it from a phone app on the same Wi-Fi.

**Why it exists:** guests ask the same questions every stay (Wi-Fi, check-out, where to eat). A TV
that answers them makes the stay feel personal and saves the host messages. It works with no
accounts and no cloud: everything stays inside the property.

## The pieces

| Piece | Runs on | Folder | Tech |
|---|---|---|---|
| **Guest Experience TV** | Android TV / Google TV (also phones, landscape) | `tv/` | Kotlin, Jetpack Compose |
| **Guest Welcome** | Samsung TVs (Tizen 5.5+) | `samsung-tv/` | Plain HTML/CSS/JS (ES5) |
| **Guest Experience Host** | The host's Android phone | `host/` | Kotlin, Jetpack Compose |
| Shared core | Used by `tv` and `host` | `shared/` | Pure Kotlin (no Android) |
| Updater | Sideload builds of `tv` and `host` | `updater/` | Android library |
| Release and update tooling | GitHub Actions, this Mac | `.github/workflows/`, `tools/` | YAML, shell |

## Reading order (about 10 minutes)

1. [ARCHITECTURE.md](ARCHITECTURE.md): how the pieces fit, where data lives, and why it's built this way.
2. [FLOWS.md](FLOWS.md): step-by-step diagrams of setup, pairing, editing and updates.
3. [INFORMATION_ARCHITECTURE.md](INFORMATION_ARCHITECTURE.md): every screen, and the content fields.
4. [PROTOCOL.md](PROTOCOL.md): the phone↔TV contract (read it before changing sync).
5. [DEVELOPING.md](DEVELOPING.md): set up, build, run, release, troubleshoot.
6. [EXTENDING.md](EXTENDING.md): recipes for common changes, and how to scale.

## I want to…

| …do this | Start here |
|---|---|
| Change what the welcome screen looks like | [EXTENDING.md › Change the design](EXTENDING.md#change-the-welcome-design) |
| Add a new thing the host can set (a field) | [EXTENDING.md › Add a content field](EXTENDING.md#add-a-content-field) |
| Support another TV brand | [EXTENDING.md › Add a TV platform](EXTENDING.md#add-a-tv-platform) |
| Ship an update | [DEVELOPING.md › Releasing](DEVELOPING.md#releasing) |
| Fix "phone can't find the TV" | [DEVELOPING.md › Troubleshooting](DEVELOPING.md#troubleshooting) |
| Run many properties, or add a cloud dashboard | [EXTENDING.md › Scaling](EXTENDING.md#scaling-roadmap) |

Other documents: [TESTING.md](../TESTING.md) (manual test checklist), [PLAY_RELEASE.md](../PLAY_RELEASE.md)
(Google Play), [PRIVACY_POLICY.md](../PRIVACY_POLICY.md), [DATA_SAFETY.md](../DATA_SAFETY.md),
[samsung-tv/README.md](../samsung-tv/README.md) (Samsung install details).
