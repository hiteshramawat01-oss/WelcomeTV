# Guest Welcome for Samsung TV (Tizen)

A minimal Samsung Smart TV app: one full screen with the guest's name and a welcome message.
It's a plain Tizen web app (HTML, CSS, JS). It doesn't use Gradle and doesn't share code with the Android apps.

```
samsung-tv/
  config.xml   Tizen app manifest (profile tv-samsung, 1920×1080)
  index.html   welcome screen + setup screen
  style.css    look (same colours and fonts as the Android TV app)
  app.js       remote-control handling and saving
  icon.png     launcher icon
  fonts/       Cormorant Garamond and Inter (OFL, see ../licenses)
```

## How it works on the TV

- **First launch** shows a setup screen. Type the guest name and a message with the remote
  (OK on a field opens the TV keyboard), then pick **Save**.
- After that the TV shows only the welcome screen. The app turns off the TV's screensaver while it's open.
- **To change the name or message:** hold **OK** on the remote for 3 seconds.
- **Back** closes the app.

Everything is saved on the TV itself (the app's local storage). The app sends nothing over the
network, and it doesn't sync with the Host phone app. That sync uses raw TCP sockets, which Tizen web
apps can't open.

## Try it in a browser

```bash
cd samsung-tv && python3 -m http.server 8765
```

Open http://localhost:8765 at a 1920×1080 window. Enter acts as the remote's OK button, arrows move focus.

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
cd samsung-tv && zip -qr -X /tmp/GuestWelcome-unsigned.wgt config.xml index.html style.css app.js icon.png fonts
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

```bash
sdb -s <tv-ip>:26101 shell 0 was_execute GuestExpTV.GuestWelcome
```

Publishing to the Samsung Apps store goes through Samsung Seller Office and is a separate step.
