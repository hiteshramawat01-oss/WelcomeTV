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

1. Install **Tizen Studio** with the **TV Extensions** (Package Manager › Extension SDK).
2. In Tizen Studio's **Certificate Manager**, create a **Samsung** certificate (signed in with a
   Samsung account), with the TV's DUID added to the distributor certificate.
3. On the TV: open **Apps**, press **1 2 3 4 5** on the remote, turn **Developer mode** on, and
   enter your Mac's IP address. Restart the TV.
4. In Tizen Studio: **File › Import › Tizen › Tizen Project**, pick this `samsung-tv` folder
   (or create a TV web project and copy these files in).
5. Connect to the TV in **Device Manager** (Remote Device Manager › add the TV's IP), then
   right-click the project › **Run As › Tizen Web Application**.

From the command line, once Tizen Studio is installed and a certificate profile exists:

```bash
tizen package -t wgt -s <your-profile> -- samsung-tv
```

```bash
sdb connect <tv-ip>
```

```bash
tizen install -n samsung-tv/GuestWelcome.wgt -t <tv-name>
```

Publishing to the Samsung Apps store goes through Samsung Seller Office and is a separate step.
