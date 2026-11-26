# Guest Welcome for Samsung TV (Tizen)

A minimal Samsung Smart TV app: one full screen with the guest's name and a welcome message.
It's a plain Tizen web app (HTML, CSS, JS). It doesn't use Gradle and doesn't share code with the Android apps.

```
samsung-tv/
  config.xml   Tizen app manifest (profile tv-samsung, 1920×1080)
  index.html   welcome, setup and pair-a-phone screens
  style.css    look (same colours and fonts as the Android TV app)
  app.js       screens, remote-control handling and saving
  content.js   content rules (same as ContentValidator) and clock/date/check-out text
  scene.js     the Udaipur dusk/night background (same vector scene as the Android app)
  qr.js        QR encoder for the Wi-Fi code (checked against zxing: QrJsCompatTest)
  sync.js      updates from the Host phone app (pairing + encrypted messages)
  icon.png     launcher icon
  fonts/       Cormorant Garamond and Inter (OFL, see ../licenses)
```

## How it works on the TV

- **First launch** shows a setup screen. Type the guest name and a message with the remote
  (OK on a field opens the TV keyboard), then pick **Save**.
- After that the TV shows only the welcome screen. The app turns off the TV's screensaver while it's open.
- The welcome screen is "Welcome v2", the same design as the Android TV app: scene, clock and date,
  greeting, check-out, a rotating line of notes/places/contact, and the Wi-Fi QR card.
- **OK** on the Wi-Fi card (selected at start) enlarges the QR; OK or Back closes it.
- **To open the settings:** press **Up** to reach ⚙ at the top right, then **OK**.
- **Back** closes the app.

Everything is saved on the TV itself (the app's local storage). Reinstalling the app clears it.

## Updating it from the Host phone app

The Guest Experience Host app (1.1.0+) can change the guest name and message over the Wi-Fi.

1. On the TV: open the settings, choose **Pair a phone**. A 6-digit code shows for 5 minutes.
2. In the Host app: pick the Samsung TV ("Samsung · Guest Welcome"). If it isn't listed, type the
   TV's IP address (TV: Settings › General › Network › Network Status › IP Settings) and tap **Add**.
3. Enter the code and tap **Pair**. From then on, **Send to TV** updates the welcome screen.
   If Guest Welcome is closed, the phone opens it on the TV.

**Remove all phones** on the pair screen un-pairs every phone.

How it works: Tizen web apps can't listen on a socket, so the phone can't connect to this app the
way it connects to the Android TV app. Instead both join a channel (`com.guestexperience.welcome`) on
the TV's own Samsung multiscreen service (port 8001, local network only). The app joins as the
channel's host and the phone joins as a client. Over that channel they run the **same protocol** as
the Android TV app ([SyncProtocol.kt](../shared/src/main/kotlin/com/guestexperience/shared/sync/SyncProtocol.kt)):
ECDH P-256 pairing confirmed by the code (5 attempts, 5 minutes, single use), then AES-256-GCM
messages with a strictly increasing counter. Other devices on the Wi-Fi can join the channel, but
without the pairing key they can't change anything. The phone side is
[SamsungChannelDialer.kt](../shared/src/main/kotlin/com/guestexperience/shared/sync/SamsungChannelDialer.kt);
its WebSocket client sends no extensions, because the TV silently ignores clients that offer
permessage-deflate.

The pair screen's "Phone link ·" line shows the app's connection to that service, which helps when a
phone can't connect.

## Try it in a browser

```bash
cd samsung-tv && python3 -m http.server 8765
```

Open http://localhost:8765 at a 1920×1080 window (add `?demo`, `?demo=night` or `?demo=plain` for sample content). Enter acts as the remote's OK button, arrows move focus.

## Install on a Samsung TV

Tizen Studio is deprecated. Use the **Visual Studio Code Extension for Tizen**, which installs the
Baseline SDK (Package Manager, Certificate Manager, Device Manager, CLI, `sdb`) on first launch.

1. In VS Code, install **Tizen** (Visual Studio Code Extension for Tizen) from the Marketplace and
   let it install the Baseline SDK. Then add the **TV extension** and **Samsung certificate
   extension** with its Package Manager.
2. On the TV: open **Apps**, press **1 2 3 4 5** on the remote, turn **Developer mode** on, and
   enter your Mac's IP address. Restart the TV fully.
3. Open **Device Manager**, add the TV by its IP address, and connect.
4. Open **Certificate Manager** and create a **Samsung** TV certificate (signed in with a Samsung
   account), with the TV's DUID added. The TV must be connected so its DUID can be picked up.
5. Open this `samsung-tv` folder in VS Code, then use the extension's build and run commands
   (Command Palette › "Tizen") to package it and run it on the connected TV.

From the command line, once the Baseline SDK is installed and a certificate profile exists
(`sdb` ships with the TV extension, e.g. `~/tizentv-tools/sdb/sdb`):

```bash
TZ=~/.tizen-extension-platform/server/sdktools/data/tools/tizen-core/tz
```

The certificate must be a **Samsung** profile with the TV's DUID. A plain Tizen certificate fails
with "Invalid certificate chain".

```bash
cd samsung-tv && zip -qr -X /tmp/GuestWelcome-unsigned.wgt config.xml index.html style.css *.js icon.png fonts
```

```bash
$TZ pack -t wgt -s "<your Samsung profile>" -p ~/.tizen-extension-platform/server/sdktools/sdk-data/profile/profiles.xml -b /tmp/GuestWelcome-unsigned.wgt -o /tmp/GuestWelcome.wgt
```

```bash
sdb connect <tv-ip>:26101
```

```bash
$TZ install -p /tmp/GuestWelcome.wgt -e <tv-ip>:26101
```

`tz install` removes the old version first, which clears the saved name, message and paired
phones. After a reinstall, pair the phone again.

```bash
sdb -s <tv-ip>:26101 shell 0 was_execute GuestExpTV.GuestWelcome
```

Publishing to the Samsung Apps store goes through Samsung Seller Office and is a separate step.

## Automatic updates from GitHub

This Mac can upgrade the TV app by itself whenever `main` on GitHub has new commits in
`samsung-tv/`. The Mac must be on and on the same Wi-Fi as the TV, with Developer mode on.

It installs as an upgrade (`vd_appinstall`), not with `tz install`, so the saved guest name,
message and paired phones stay on the TV.

1. Settings are in `~/.config/guestexperience/samsung.env` (`TV_IP`, `PROFILE`). The first run
   creates a commented example if the file is missing.
2. Try it once:

```bash
tools/samsung-autoupdate.sh --once --verbose
```

3. Turn on the check every 5 minutes:

```bash
tools/install-samsung-autoupdate.sh install
```

Check on it with `tools/install-samsung-autoupdate.sh status`, turn it off with
`tools/install-samsung-autoupdate.sh uninstall`. Re-run `install` after editing the script.

The updater keeps its own copy of the repo in `~/Library/Application Support/GuestExperience`
and logs to `~/Library/Logs/GuestExperience/samsung-update.log`. Each install gets version
`1.2.<number of samsung-tv commits>`. If the TV is off, it tries again on the next check.
