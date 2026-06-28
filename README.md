# SynoCam — Synology Surveillance Station on Fire TV

A small, sideloadable Fire TV (Android TV) app that shows your **Synology
Surveillance Station** cameras as a live **grid wall**, with click-to-fullscreen.
You log into the NAS once; the app auto-discovers every camera via the Surveillance
Station Web API — no per-camera URLs to paste.

- **Camera wall** (grid) as the home screen, D-pad to move focus, **OK** to go fullscreen.
- **Fullscreen** single camera; **◀ / ▶** switch cameras, **Back** returns to the wall.
- Plays both **H.264 and H.265/HEVC** RTSP via libVLC (ExoPlayer's RTSP can't do HEVC).
- **LAN-only.** The app talks only to your NAS on the local network; nothing is exposed
  to the internet.

> Status: MVP. See [Limitations](#limitations) for what's intentionally not here yet.

---

## Download

Grab the prebuilt **universal** APK from the latest release — it carries every CPU ABI,
so it installs on any Fire TV (Stick Lite / 3rd gen / 4K / 4K Max / Cube), including the
many sticks that run a 32-bit Fire OS and reject an arm64-only build:

**[⬇ Download SynoCam v0.2.2 APK (universal)](https://github.com/1whoknocks/syntv/releases/download/v0.2.2/synocam-universal-v0.2.2.apk)**

In the Fire TV **Downloader** app, enter that URL (or a short link to it) to install —
see [Sideload onto the Fire TV](#3-sideload-onto-the-fire-tv). To build it yourself
instead, see [Build the APK](#2-build-the-apk). (Per-ABI splits are also attached to the
release if you prefer a smaller download for a known device.)

---

## 1. Prepare the NAS (one-time)

Create a **dedicated DSM user** for the app:

1. DSM → **Control Panel → User & Group → Create**, e.g. `firetv`.
2. Give it permission to **Surveillance Station** only (no admin).
3. In **Surveillance Station → Settings → Privilege**, grant that user **live view** on
   the cameras you want.
4. **Do not enable 2-factor (OTP)** for this user — the app's login flow doesn't do OTP.

Note your NAS address and DSM port (default **5000** for HTTP, **5001** for HTTPS).

---

## 2. Build the APK

Requires the Android SDK and JDK 17+. From this directory:

```bash
export ANDROID_HOME=/path/to/android-sdk   # must contain platform 34 + build-tools 34
./gradlew assembleDebug
```

Output (one small APK per ABI):

```
app/build/outputs/apk/debug/app-arm64-v8a-debug.apk     # modern Fire TVs (use this one)
app/build/outputs/apk/debug/app-armeabi-v7a-debug.apk   # older 32-bit Fire TV Sticks
app/build/outputs/apk/debug/app-x86_64-debug.apk        # Intel/Windows emulator only
```

**Which one?** Use **arm64-v8a** for any current device (Fire TV Stick 4K / 4K Max,
Fire TV Cube, Stick 3rd gen). Use **armeabi-v7a** only for old 32-bit sticks. If unsure,
arm64-v8a covers everything sold in the last several years.

---

## 3. Sideload onto the Fire TV

On the Fire TV: **Settings → My Fire TV → Developer options → Apps from Unknown
Sources → ON** (and **ADB debugging → ON** if using `adb`).

**Option A — adb (from a computer):**

```bash
adb connect <fire-tv-ip>:5555
adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

**Option B — no computer (Downloader app):** open the APK URL directly in the Fire TV
**Downloader** app:

```
https://github.com/1whoknocks/syntv/releases/download/v0.2.2/synocam-universal-v0.2.2.apk
```

(or build your own and host it on any reachable URL / GitHub Release).

The app appears on the Fire TV home row as **SynoCam**.

---

## Testing without a Fire TV

You can test everything — setup, login, the camera wall, fullscreen, live playback, and
PiP — without ever touching a Fire TV. Easiest options first:

### A. Android TV emulator (recommended — closest to a Fire TV)

1. Install **Android Studio** (free) and open the repo root as a project.
2. **Device Manager → Create Device → TV** (e.g. *Television (1080p)*), pick a recent
   system image — **arm64** image on an Apple-Silicon Mac, **x86_64** image on
   Intel/Windows/Linux — and finish.
3. Press **Run ▶**. The app installs and launches in the emulated TV; Android Studio
   picks the matching ABI automatically.
4. Navigate with the emulator's on-screen **D-pad** controls. The emulator shares your
   computer's network, so it reaches the NAS at `<your-nas-ip>` — enter it in Setup to
   get real streams.
5. **PiP test:** open a camera, press the emulator's **Home** button → it should shrink
   to a corner; open another app to confirm it keeps playing.

> The emulator is AOSP **Android TV, not Fire OS** — perfect for UI, navigation, login,
> playback, and PiP behavior. The only thing it can't confirm is whether a *specific*
> Fire TV model supports PiP; that final check needs the real device.

### B. An Android phone (fast, and PiP always works there)

Enable **Developer Options → USB debugging**, plug in, then `./gradlew installDebug`
(or copy `app-arm64-v8a-debug.apk` and open it). It runs as a normal app; navigation is
touch instead of D-pad. The phone must be on the same Wi-Fi as the NAS.

### C. Logic tests only (no device at all)

```bash
./gradlew testDebugUnitTest   # runs the Surveillance Station API parsing tests
```

## 4. Use it

1. First launch shows the **setup** screen. Enter NAS address, port, account, password,
   pick a grid size, and press **Connect**. Credentials are saved (encrypted) so it
   reconnects automatically next time.
2. The **wall** appears with all enabled cameras live. Move focus with the D-pad,
   press **OK** to open one fullscreen.
3. In **fullscreen**, press **◀ / ▶** to switch cameras, **OK** to mute/unmute, and
   **Back** to return. Audio is **off by default** (the wall is always silent); pressing
   OK in fullscreen unmutes that camera. Cameras without an audio track stay silent even
   when unmuted (a 🔇/🔊 indicator shows the current state).
4. To change NAS/credentials/grid size later, press **Settings** on the wall.

### Picture-in-Picture (watch a camera over another app)

Open a camera fullscreen, pick the one you want with **◀ / ▶**, then either press
**Menu** (☰) or just press **Home** — the camera shrinks into a corner window. Open
Netflix or any other app and it keeps playing in the corner. Select the PiP window to
expand it back.

> **Device support:** picture-in-picture only works on Fire TVs that support it — the
> **Fire TV Cube** and many **Fire TV Edition smart TVs**. Basic **Fire TV Sticks
> usually do not** support it; on those the app shows a "doesn't support
> picture-in-picture" message (Amazon blocks the alternative overlay method on Fire OS 8+).

---

## Performance / grid size

A Fire TV Stick has a limited number of hardware video decoders. More tiles = more
simultaneous decodes, especially with HEVC. Guidance:

- **Fire TV Cube / 4K Max:** 3×3 is usually fine.
- **Older / cheaper Sticks:** keep it at **2×2** (or fewer cameras per row).

If tiles stutter or fail to start on a weak device, lower the grid size in **Settings**.
For best results, also set those cameras' **live-view stream** to a lower resolution in
Surveillance Station — that stream is what the app plays.

---

## How it works

- **API** (`api/SurveillanceClient.kt`): `SYNO.API.Auth Login` (session
  `SurveillanceStation`) → `SYNO.SurveillanceStation.Camera List` →
  `GetLiveViewPath` (returns each camera's `rtspPath`). The session id is cached and
  re-acquired automatically if it expires.
- **Player** (`player/`): one shared `LibVLC` instance, one `MediaPlayer` per visible
  tile, RTSP-over-TCP with a small latency buffer.
- **Storage** (`data/Settings.kt`): NAS credentials in `EncryptedSharedPreferences`.

### HTTPS note

Synology boxes ship self-signed certificates. When **HTTPS** is enabled the app trusts
the certificate of the host you entered (it only ever talks to your NAS on the LAN). For
the simplest setup, use **HTTP on port 5000** on your home network.

---

## Limitations

Intentionally out of scope for this MVP:

- Recorded-footage playback / timeline scrubbing
- PTZ controls
- Motion-event / automatic alerts (manual PiP only — auto-PiP-on-motion isn't possible
  for sideloaded apps on current Fire OS; see commit history / plan for the why)
- Multiple simultaneous PiP windows (Android allows only one PiP window at a time)
- Alexa voice control (would require bridging cameras into Alexa, e.g. via Scrypted)
- Remote (off-LAN) access via QuickConnect / DDNS / VPN
- Always-on / boot / screensaver wall mode
- Audio (the wall is silent by design)

---

## License

The SynoCam application code is released under the **MIT License** — see
[`LICENSE`](LICENSE).

This app bundles and links against **libVLC**, the VLC media-player engine by the
**VideoLAN** project, via `org.videolan.android:libvlc-all`. libVLC is licensed under
the **GNU LGPL v2.1 or later**. SynoCam's MIT license covers only its own source; libVLC
remains under its own terms. See <https://www.videolan.org/> and the third-party notice
in [`LICENSE`](LICENSE).
