# AM2R-Autopatcher

# AM2R for Android — Autopatcher

A native 64-bit Android port of **AM2R — Another Metroid 2 Remake (Community Updates 1.5.5)**,
distributed the way the AM2R community distributes everything: **you bring your own copy of the
original AM2R 1.1 release**, and this app patches it into the game on your device. Nothing playable
is hosted here — this repository contains only the patcher and the binary *difference* between your
copy of the game and the port, which is useless on its own.

The port runs natively on modern Android (no emulator, no streaming) and aims for 1:1 parity with
the PC release, with touch controls, controller support, and haptics.

## Get the game

You need your own copy of the original **AM2R 1.1** release (`AM2R_11.zip`, the 2016 freeware
release). A modified or Community-Updates copy will not work — the patcher verifies the exact
original game data.

1. Put your `AM2R_11.zip` somewhere on your phone (Downloads, SD card, a cloud app — anywhere the
   file picker can reach).
2. From the [latest release](../../releases/latest), download and install `AM2R-Patcher-<version>.apk`.
   Android will ask you to allow installs from your browser/file manager ("Install unknown apps") —
   allow it.
3. Open **AM2R Patcher**, tap **Choose AM2R_11.zip…**, pick your zip, and wait about a minute.
4. Tap **Install AM2R**. The patcher shows the finished APK's checksum so you can confirm it matches
   the release notes.

**Updates** install right over the existing app — **saves are kept**. Uninstalling deletes saves.

## How it works

The patcher app bundles its patch data: `wrapper.bin` (the port's engine, code, and community
content with the original-game byte ranges removed), `droid.xdelta` (a binary delta that rebuilds the
game data **from your 1.1 copy**), and `assembly.json` (an ordered splice plan with a checksum for
every segment). It verifies your `data.win`, rebuilds the game data with a bundled native build of
xdelta3, splices everything back in order, and accepts the result only if the final hash equals the
official release hash. Because the reconstruction is byte-exact, the official signature carries over —
every patched APK is identical to the release build.

## Repository layout

| Path | What it is |
|------|-----------|
| `app-android/` | The patcher app (Java + a native xdelta3 build via JNI) |
| `make_patch_data.py` | Maintainer tool: turns a signed release APK into the app's patch data |

The patch data itself (`patch-data/`, and the release APK) are **release artifacts**, not committed
here — see below.

## Building from source (maintainers)

The patch data is generated from the signed canonical release APK and is not stored in the repo.

```sh
# 1. Generate patch data from the signed release APK + a reference AM2R 1.1 copy.
#    Output goes to patch-data/ at the repo root, which app/build.gradle bundles as assets.
python3 make_patch_data.py <canonical-release.apk> <AM2R_11.zip-or-folder> patch-data/

# 2. Build the app (JDK 17 required by Android Gradle Plugin 8.7), signed with the release key:
cd app-android
AM2R_KS=/path/to/release.keystore AM2R_KS_PASS=… AM2R_KS_ALIAS=… AM2R_KS_KEYPASS=… \
  gradle assembleRelease
```

## Credits & licenses

- **AM2R** was created by DoctorM64 and team; **Community Updates** by the AM2R Community Developers.
  All game content belongs to its original creators. This is an unofficial fan project, not affiliated
  with or endorsed by Nintendo, and it complies with takedown requests from rights holders.
- Distribution model and inspiration: the AM2R Community Developers'
  [autopatcher](https://github.com/AM2R-Community-Developers/AM2R-Autopatcher-Windows).
- The palette system is PixelatedPope's
  [Retro Palette Swapper](https://github.com/PixelatedPope/RetroPaletteSwapper) (MIT).
- xdelta3 by Josh MacDonald (Apache 2.0), built from source under `app-android/app/src/main/jni/`.
- The patcher tooling in this repository is released under the MIT License (see `LICENSE`).
