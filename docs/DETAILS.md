# Clean TV: a fast, YouTube-style theme for Android TV

A flat dark (or light) launcher, native Settings, a clock and 7-day weather screensaver, and a movie-server browser, drawn in plain Java with no libraries. Built and tested on a Xiaomi Mi TV 4 (MiTV-MSSP1, Android 9), but the launcher should work on any Android TV with Android 8+ (min SDK 26).

![Home](docs/home.png) ![Settings](docs/settings.png) ![Screensaver](docs/screensaver.png)

## Quick start (any Android TV)
1. On the TV: Settings > Device Preferences > About, press OK on "Build" 7 times; then Developer options > turn on USB debugging and Network debugging. Note the TV's IP address.
2. On your computer you need Python 3 (`pip install adb-shell`), JDK 17 and the Android SDK (platform android-34, build-tools 34.0.0). Put them in `toolchain/` (see `build.sh`) or set `ANDROID_SDK_ROOT` and `JAVA_HOME`.
3. `export TV_HOST=<tv-ip>` then `./install.sh` (builds, installs, sets it as Home). Accept the debugging prompt on the TV the first time.
4. Optional: edit `launcher/app/src/main/res/values/config.xml` first (dock apps, hidden apps, your own movie servers). Servers can also be added on the TV in Settings > Servers.
5. When done, turn USB debugging and Network debugging off.

**Only the launcher is portable.** The debloat lists in `mitv4/backup/` (`disabled.txt`, `restore.sh`) are for the Mi TV 4 only. Do not disable packages on another model without checking them. Everything is reversible with `pm enable`. The weather location is hard-coded to Dhaka in `ClockDream.java`; change the latitude and longitude there. The default movie servers in `config.xml` are Bangladeshi public FTP indexes; replace them with your own. Use at your own risk; no warranty.

## Details (Mi TV 4 build)
Nothing was uninstalled; every change is reversible.

## Layout
- `mitv4/backup/` – package lists, getprop, settings, `restore.sh` (re-enables everything that was disabled).
- `launcher/` – the native Java launcher (`app.cleanyourtv.launcher`). It is one Canvas-drawn view, with no libraries (not even RecyclerView).
- `build.sh` / `install.sh` – build the APK, install it and set it as Home.
- `tools/` – `tv.py` (ADB over Wi-Fi), `disable.py`, `install.py`. Set `TV_HOST` for a different IP.
- `shots/` – screenshots taken from the TV at each step.

## Re-enable an app
One app: `adb shell pm enable <package>`
Everything: `mitv4/backup/restore.sh`
The list of disabled apps is in `mitv4/backup/disabled.txt`. To get the stock launcher back, run `pm enable com.google.android.tvlauncher`, then `pm disable-user --user 0 app.cleanyourtv.launcher` (or just clear the home default in Settings).

## Change the dock, columns or hidden apps
Edit `launcher/app/src/main/res/values/config.xml`:
- `columns` (4–7) sets both the dock slots and the grid columns.
- `dock_apps` is a list of package names, in order. Anything missing is skipped.
- `hidden_apps` are never shown in the grid.
Then run `./install.sh`.

## Change the wallpaper
The background is pure black on purpose. The coloured glow is sampled from the focused tile. To use an image, draw it at the start of `Board.onDraw` in `LauncherActivity.java`.

## Private DNS ad blocking (optional, not applied)
Turn on: `settings put global private_dns_mode hostname` and `settings put global private_dns_specifier dns.adguard-dns.com`.
Undo: `settings put global private_dns_mode opportunistic`.
It does not block YouTube ads. Skip it if your router already filters DNS.

## Movies from your FTP servers (VLC)

The first two dock tiles open a built-in browser for the h5ai index pages in `config.xml` (`ftp_servers`, format `Title|URL`). Up/Down move, Centre opens a folder or plays a video in VLC, Back goes up a folder. To add a server add another `<item>` and set `dock_apps` entry `ftp:N` (N = position in the list), then run `./install.sh`. VLC itself is installed from `toolchain/vlc.apk` (`adb install`).

## Speed tweaks

System animations are set to 0.5x and about 25 more vendor packages are disabled (all listed in `mitv4/backup/disabled.txt`). `mitv4/backup/restore.sh` undoes everything. Three helper packages (`leanbacklauncher`, its recommendations, `tvrecommendations`) get re-enabled by the system at boot; they are harmless and the launcher ignores them.

## Notes
- Three launcher helper packages (`leanbacklauncher`, its recommendations and `tvrecommendations`) get re-enabled by the system at boot. They do nothing without the stock launcher.
- The Wi-Fi pill shows "Wi-Fi" only, because Android 9 hides the network name unless Location is on.
- Remember: **turn USB debugging and Network debugging off** when you're done (see below).


## Servers (set on the TV, no coding)

Press **Servers** in the top bar of Home (the pill after Settings). Pick a server to
**Hide from Home / Show on Home**, **Edit name or address**, **Move up / down** or **Delete**.
Choose **+ Add a server** to type a new name and address with the on-screen keyboard
(e.g. when you move to a new area with a different FTP). Shown servers appear first on Home,
in the order of the list. The list is stored on the TV; the entries in `config.xml`
(`ftp_servers`) are only the first-run defaults. To reset to those defaults:
`adb shell pm clear app.cleanyourtv.launcher` (also clears nothing else important).

## Tiles

Every Home tile is a 16:9 card with its name underneath. Apps use their own TV banner
(like the stock YouTube row); servers get a coloured play card.

## Play Store

Hidden from Home (listed in `hidden_apps`) but not disabled. To show it again, remove
`com.android.vending` from `hidden_apps` in `config.xml` and run `./install.sh`.

## Look and feel (YouTube TV style)
The launcher, FTP browser and Servers screen copy the YouTube TV app: flat #0F0F0F background, system Roboto font, a left sidebar (icons, expands to labels when you press Left; selected item is a white pill: Home, Inputs, Wi-Fi, Servers, Settings), section rows of 16:9 rounded cards, and a white border plus slight zoom on the focused card with its title underneath.

Settings: the sidebar Settings item opens our own YouTube-style Settings list. Each row opens that exact system page directly (Sound, Date & time, Language and so on), so the Android TV panel that slides in from the right only appears for the final page. Dialogs (server actions, add/edit) use a matching dark rounded theme.

## Settings in the new style

Every Settings entry is a native page drawn by the launcher (`PageActivity`): Network & Wi-Fi, Bluetooth & remotes (pair/unpair, search), Servers, Display & HDMI (screensaver, sleep, CEC, ARC), Sound (volume, surround, ARC), Date & time (with a Time zone city list), Language (30 languages), Keyboard (choose/enable keyboards), Accessibility, Apps (with details dialog), Storage (incl. USB drives), Privacy, Developer options and About. On long lists, Left/Right jump 8 rows.

`install.sh` grants WRITE_SECURE_SETTINGS, CHANGE_CONFIGURATION and WRITE_SETTINGS so these pages can change settings. Android only lists nearby Wi-Fi networks while Location is on; the Wi-Fi page offers to turn it on (it asks first).

Limits: Xiaomi's picture/sound-mode panel is a private service, so Display & HDMI has one labelled row that opens it. Manually setting the clock and Wi-Fi static IP/proxy need system-only permissions and are not offered.

## Theme (Settings > Theme)
Press OK on the row to cycle Auto, Dark, Light. Auto is light from 07:00 to 18:00 and dark otherwise. It applies to the launcher and our own Settings, Servers and page screens, and also sets Android's night mode for apps that follow it. YouTube, YouTube Music and the video players are dark only and ignore it.

## Screensaver: clock and weather
`ClockDream` is the screensaver ("Clock and weather"): a big clock, the date, current weather, and a 7-day forecast with icons. Data comes from Open-Meteo (no key), with the location hard-coded to Dhaka (latitude 23.81, longitude 90.41) in `ClockDream.java`. It refreshes every 30 minutes and drifts about 8 px for burn-in protection.
Revert to Google's: `settings put secure screensaver_components com.google.android.backdrop/com.google.android.backdrop.Backdrop`
Test quickly: set `settings put system screen_off_timeout 15000`, go Home and wait about 30 s; then put it back to 300000.

## Voice search
A custom voice screen is not possible: the Mic button launches Google's Assistant (Katniss), which draws its own overlay. `mitv4/restore-voice.sh` puts Google's setup back (already applied). The unused Voice* classes in the launcher can be ignored.

## Home tiles
YouTube and YouTube Music show icon-only tiles. Tile art is cached. `install.sh` also force-stops stale launcher tasks before starting.

## Final lockdown
When finished: Developer options > turn off USB debugging and Network debugging, revoke USB debugging authorisations, reboot. Check that `adb connect <tv-ip>:5555` says "Connection refused".
