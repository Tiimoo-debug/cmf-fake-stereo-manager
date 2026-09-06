# cmf-fake-stereo-manager

A small root app for controlling the
[cmf-fake-stereo](https://github.com/Tiimoo-debug/cmf-fake-stereo) Magisk
module without a terminal.

The module works perfectly well on its own — this is the optional friendly
front end, in the same spirit as
[PerfMTK](https://github.com/JUANIMAN/PerfMTK) and its separate
[PerfMTK-Manager](https://github.com/JUANIMAN/PerfMTK-Manager).

## What it does

- Arm / disarm the earpiece output
- Earpiece gain slider, clamped to 31 (the hardware ceiling — above that the
  5-bit register field wraps and gets *quieter*)
- Mode: apply only while audio is playing, or always
- **Solo** — speaker off, earpiece only, for auditioning
- **Doctor** — diagnose why the earpiece is silent
- Log, probe and report, with output shown in the app

Everything it does is a call to the module's own `stereoctl`, or an edit to
`/data/adb/cmf-stereo/*.conf`. Nothing is duplicated — the module stays the
source of truth, and the terminal keeps working exactly as before.

## Requirements

- The `cmf-fake-stereo` module, installed and rebooted
- Root (Magisk / KernelSU / APatch) and superuser permission for this app
- Android 8.0+

## Not an Xposed module

There is nothing to hook. The settings are text files a shell daemon reads,
so this is an ordinary root app. It uses `su -c` directly rather than a root
library, to keep the dependency list to Compose alone.

## Build

CI builds it on every push (`.github/workflows/build.yml`) and uploads the APK
as an artifact. Locally:

```sh
./gradlew assembleRelease
```

The release build is **debug-signed** so CI can produce an installable APK
without a secret key. Swap in a real signing config before publishing
anything you intend to update in place.
