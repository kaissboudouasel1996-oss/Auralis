# Auralis

A premium toolkit for Android TV, made for the Xiaomi TV 65" QLED. Built for the remote: every button works with the D-pad and OK.

## What it does

- **Home**: model, Android version, resolution, free memory and storage, IP and Wi-Fi link, plus the ADB connection.
- **Boost**: closes background apps and (with ADB) clears every app's cache. Shows how much memory it freed.
- **Apps**: list everything installed, then **Disable** or **Uninstall** what you never use. System-critical packages are hidden. Everything is reversible from **Restore**.
- **Pro tools** (need ADB, see below):
  - system animations: off, 0.5x or normal (feels much faster)
  - HDMI-CEC on or off
  - Private DNS: AdGuard (blocks ads and trackers system-wide), Cloudflare, Google, automatic, off
  - Aurora screensaver (set or reset)
  - sleep timer: 15, 30, 60 or 90 minutes, with cancel
  - screenshot to the Pictures folder
  - restart the TV
  - shortcuts to Developer options, Wi-Fi, Display, Storage, Apps, Accessibility, Date and time
- **Launcher**: a full home screen in the same style. Big clock and greeting, a Favorites row with each app's TV banner (hold OK on any app to pin, unpin or reorder it), and a grid of all your apps. Make it your home screen from **Pro tools > Launcher > Make it my home screen** (needs ADB), and put the original back with **Restore original home**.
- **Network**: speed test (download Mbps and latency) with a plain verdict on whether 4K streaming will be smooth.
- **Ambient clock**: full-screen aurora with a large clock. It drifts a few pixels every minute so a QLED panel never shows a static image. The same aurora is available as a system screensaver.

## Unlock the Pro tools (one time)

1. Settings > Device Preferences > About > press OK on **Build** 7 times.
2. Settings > Device Preferences > Developer options > turn on **USB debugging** (and network / ADB debugging if you see it).
3. Open Auralis, go to **Home** and press **Connect**. On the TV prompt choose **Always allow**.

The app talks only to the TV's own ADB (127.0.0.1). Nothing leaves your TV except the speed test.

## Install

GitHub builds the APK for you.

1. Open the **Actions** tab and wait for **Build Android APK** to go green (or press **Run workflow**).
2. Download `Auralis.apk` from the **Releases** page (tag `latest`).
3. Put it on the TV (Downloader app with the release link, a USB stick, or `adb install Auralis.apk`).

## Folders

- `android/`: Kotlin + Jetpack Compose app
- `.github/workflows/`: APK build
