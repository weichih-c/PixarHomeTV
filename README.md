# PixarHomeTV · Door Hall

**English** · [繁體中文](README.zh-TW.md)

A home screen for Android TV / Google TV where every app is a door.

![Opening a door](docs/door-open.gif)

Doors hang from a ceiling rail like the door vault in *Monsters, Inc.* Slide along the rail with the remote,
and the door you land on rolls under a spotlight, rattles, and lights up its lamp. Press OK and the door swings
open, warm light spills across the floor, a little slime monster hops in through the doorway, and the camera
follows it inside — and you are in the app.

![The door hall](docs/hall.png)

## Where the idea came from

This started from [cleanYourTV](https://github.com/Gathin23/cleanyourtv) by Gathin: a project that debloats
Android TV over Wi-Fi and replaces the ad-filled home screen with a clean, Apple TV–style launcher. I used it to
clean up our family TV first, then wanted a home screen with more personality.

The twist is my own: the factory floor from Pixar's *Monsters, Inc.*, where every closet door leads to a
different child's bedroom. Here every door leads to a different app instead. The doors, the hall and the monster
are all original designs drawn in code — this is a fan-made tribute, not affiliated with Disney or Pixar, and it
contains no artwork from the films.

## Features

- **A door per app** — each door has its own colour and style (panelled, planked, porthole, arched, striped,
  polka-dot), with the app's real TV banner on a brass nameplate.
- **A conveyor of doors** — the focused door sits in the middle under a dusty spotlight; the others turn towards
  it in perspective and fade into the dark.
- **Opening a door** — the leaf swings in on its hinge in 3D, the light inside takes on the app's colour, a slime
  monster hops in with squash-and-stretch and a curly tail, and the camera pushes through the doorway. Press OK
  again to skip.
- **Everything drawn at runtime** — no images or videos to download; the APK is under 30 KB.
- **Takes over the Home button without breaking the volume keys** — on firmware where the stock launcher can't
  be replaced, `HomeRedirectService` watches for the stock home screen and brings the hall back. It never filters
  key events, so holding the volume button still ramps the volume (a key-filtering accessibility service breaks
  that on several TVs).
- **Remote button redirects** — send an app button you don't use to an app you do. Ours turns the Prime Video
  button into an HBO Max button.
- **Settings and "all apps" doors** for everything that doesn't have its own door.

## Requirements

- An Android TV / Google TV set on Android 8.0 (API 26) or newer, with USB debugging and network debugging turned on.
- A computer on the same network with JDK 17 and the Android SDK (platform 34, build-tools 34.0.0).
- A checkout of [cleanYourTV](https://github.com/Gathin23/cleanyourtv) next to this repository, with its Python
  venv set up (`python -m venv tools\venv` and `tools\venv\Scripts\pip install -r tools\requirements.txt`).
  The install script uses its ADB client (`tools/tv.py`) — no `adb` binary needed.

Tested on a Panasonic 4KTV-3MUP (MOKA `R4_GTV`, Realtek, Google TV 14).

## Install

```powershell
tools\install.ps1 -TvHost <tv ip>
```

This builds the APK, installs it, makes it the preferred home, and turns on `HomeRedirectService` as the only
accessibility service. Accept the "Allow USB debugging?" prompt on the TV the first time.

When you are done, switch USB debugging and network debugging off again: the ADB port is unencrypted.

## Configure the doors

Edit [`app/src/main/assets/doors.json`](app/src/main/assets/doors.json) and run the install script again.

```json
{
  "doors": [
    {"target": "com.google.android.youtube.tv", "color": "#C9473C", "style": "panels"},
    {"target": "com.netflix.ninja", "color": "#2E3A55", "style": "porthole"},
    {"target": "@settings", "color": "#6E7480", "style": "porthole"},
    {"target": "@apps", "color": "#9A6A44", "style": "planks"}
  ],
  "app_redirects": {
    "com.amazon.amazonvideo.livingroom": "com.wbd.stream"
  }
}
```

- `target` — an app's package name, `@settings` for the TV's Settings, or `@apps` for a list of every app.
  Apps that aren't installed get no door.
- `color` — the door's colour.
- `style` — `panels`, `planks`, `porthole`, `arched`, `stripes` or `dots`.
- `app_redirects` — when the first app is opened (by a remote button, say), open the second one instead. Back
  from it returns to the hall.

## Remove it

Uninstall **Door Hall** in Settings → Apps, and turn off **Door Hall Home** in Settings → Accessibility. The
stock home screen takes over again.

## License

[Apache License 2.0](LICENSE). Parts of `HomeRedirectService` are adapted from cleanYourTV's `HomeKeyService`,
which is MIT-licensed; see [NOTICE](NOTICE).

*Monsters, Inc.* is a trademark of Disney/Pixar. This project is not affiliated with or endorsed by them.
