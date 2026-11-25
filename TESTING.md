# Manual test plan: D-pad and real devices

Run on: **Android TV API 31 emulator with 2 GB RAM**, **Google TV API 34 emulator**, and at least one
real TV or streaming stick before release. Use only the remote (or the emulator's D-pad): no mouse,
no touch. Tick each line.

Automated coverage already in `./gradlew test`: rendering, initial focus, D-pad focus between
Wi-Fi card ⇄ settings tile with no dead ends, OK → large QR → OK closes it, settings tile → Host Mode,
3-second OK hold → Host Mode. The manual plan covers what those tests can't.

## 1. First run (target: under 2 minutes)

- [ ] Fresh install → launch from the TV home row (banner shows the app name).
- [ ] "Start" is focused. OK → Create PIN.
- [ ] PIN pad: arrows move between keys, OK enters a digit. Remote number keys also type. Delete works.
- [ ] 3-digit PIN → "Next" shows an error. 4–6 digits accepted.
- [ ] Confirm with a different PIN → error, confirm field cleared. Same PIN → Property step.
- [ ] Property name: OK opens the on-screen keyboard. Done/Back/Up/Down closes it and focus stays on the field.
- [ ] Empty name → error. Valid name → Wi-Fi step.
- [ ] Wi-Fi: 7-character WPA password → error. "Open" hides the password field.
- [ ] Preview shows the real welcome screen at half size and is not focusable. Finish → guest screen.
- [ ] Time the whole flow with a stopwatch: **under 2 minutes**.

## 2. Guest screen

- [ ] Greeting matches the time of day. Name, property and city are shown. The clock updates on the minute.
- [ ] Wi-Fi card is focused on open (gold outline, slight lift), clearly visible from ~3 m.
- [ ] Scan the QR with an iPhone camera and an Android camera: both offer to join the network.
- [ ] Repeat with an SSID/password containing `; , : " \` and a Hindi network name.
- [ ] OK on Wi-Fi card → large QR. OK or Back → closes, focus returns to the card.
- [ ] Up → ⚙ tile. Every direction from every focused item keeps focus visible (no dead ends).
- [ ] Hold OK for 3 s → PIN screen. A short press does not.
- [ ] Back from the guest screen leaves the app. Home leaves the app. Relaunching shows the guest screen.
- [ ] Nothing is cut off on a TV with overscan (check on a real TV with "screen fit" off).
- [ ] With house notes, places and a host contact set, the bottom line rotates every ~12 s.

## 3. Host Mode

- [ ] Wrong PIN ×5 → "Try again in 30 s" countdown. Keypad disabled. After the wait, the correct PIN works.
- [ ] Relaunch the app during a lockout: the lockout is still in force.
- [ ] Back on the PIN screen → guest screen.
- [ ] Left menu: every section reachable with Up/Down, Right enters the section, Left returns.
- [ ] This stay: guest name, check-out date/time with − / +, message. Save → guest screen updates.
- [ ] "Clear for next guest" empties name, message and check-out.
- [ ] Wi-Fi: change the password, Save, rescan the QR → new password works.
- [ ] House notes / Nearby places / Host contact save and appear in the rotating line.
- [ ] Background: Dusk / Night / Plain switch. "Use a photo" opens a picker where the TV has one;
      otherwise the "no file picker" note is shown. A 12 MP photo loads without lag (2 GB emulator).
- [ ] Clock: 12-hour / 24-hour switch applies to clock and check-out.
- [ ] Change PIN: mismatched confirmation is refused. The new PIN works after leaving Host Mode.
- [ ] Back or "Exit to guest view" returns to the guest screen.

## 4. Host phone app (same Wi-Fi)

- [ ] TV: Host Mode › Pair a phone shows a 6-digit code and a countdown. The code changes after 5 minutes.
- [ ] Phone: the TV appears under "TVs on this Wi-Fi network" within a few seconds.
- [ ] Wrong code → clear error. 5 wrong codes → TV withdraws the code ("Too many wrong codes").
- [ ] Right code → "Connected", phone shows current TV content. TV lists the phone under Paired phones.
- [ ] Edit guest name + Wi-Fi on the phone → Send to TV → guest screen updates within ~1 s.
- [ ] Invalid Wi-Fi password on the phone → refused before sending.
- [ ] Leave the TV app (Home) → phone shows "not found on this Wi-Fi". Reopen → it reconnects.
- [ ] Remove the phone on the TV → next send from the phone asks to pair again.
- [ ] Phone on mobile data / a different Wi-Fi → helpful "same Wi-Fi" message, no crash.

## 5. Screensaver

- [ ] Settings › Screensaver (or the adb command in README) → "Guest welcome screen" → Start now.
- [ ] Shows a dim scene, large clock, property and Wi-Fi name. The content shifts slightly each minute.
- [ ] Any button exits the screensaver.

## 6. Low-end performance (Android TV API 31, 2 GB)

- [ ] Cold start: `adb shell am start -W -S com.guestexperience.tv/.ui.MainActivity` → `TotalTime` < 2000 ms (release build).
- [ ] Moving focus around the guest screen and Host Mode shows no visible stutter.
- [ ] `adb shell dumpsys meminfo com.guestexperience.tv` with a photo background: total PSS stays reasonable (< 150 MB).

## 7. Phone sanity (the TV app on a phone)

- [ ] Installs and opens in landscape. Long-press opens Host Mode. Text fields open the phone keyboard.
