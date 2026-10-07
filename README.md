# AM2R for Android — Autopatcher

Turns **your own copy** of the original AM2R 1.1 release (`AM2R_11.zip`) into the
Standard or Dual Screen Android APK, entirely on your Android device. Nothing
playable is distributed here: the game data is rebuilt from your files, and the
result is verified byte-for-byte against this build's checksum before installation.

The port is based on **AM2R Community Updates 1.5.5** and runs natively on ARM64
Android. The autopatcher creates the game APK. **Once installed, the game launches
independently and does not require the autopatcher to remain installed.**

**1.5.5.9.** AYN Thor has been tested on physical hardware and confirmed working.
The RG DS, Retroid Pocket Duo / Duo Lite and AYANEO Pocket DS profiles are
included, but physical compatibility remains unconfirmed. When reporting an
issue, include the device, Android version, profile and steps to reproduce it.
Photos or a short video of both displays are useful.

## What you need

- Your copy of the original **AM2R 1.1** Windows release (`AM2R_11.zip`, the 2016
  freeware release). A Community Updates or modded copy will **not** work — the
  autopatcher checks the exact original `data.win`.
- An **ARM64 Android device running Android 10 or newer** for the autopatcher.
- For Dual Screen, an Android handheld with two displays and built-in controls.
  Included profiles are **AYN Thor**, **RG DS**, **Retroid Pocket Duo / Duo Lite**
  and **AYANEO Pocket DS**. AYN Thor is physically tested; the other profiles
  await physical validation. RG DS Plus is not included.

## How to use

1. Put your `AM2R_11.zip` somewhere on the device that Android's file picker can
   reach, such as Downloads or an SD card.
2. Download **AM2R-Autopatcher-1.5.5.9.apk** from the
   [release page](https://github.com/am2r-android/AM2R-Autopatcher-Android/releases)
   and install it. Allow installation
   from your browser or file manager when Android asks.
3. Open **AM2R Patcher** and choose **Standard** for one screen, or **Dual Screen**
   for a handheld with one of the included profiles.
4. Select your original `AM2R_11.zip` and wait for patching to finish. The verified
   game APK is saved in **Downloads**. Your original ZIP stays unchanged.
5. Tap **Install Standard** or **Install Dual Screen** to open Android's installer.
   Allow AM2R Patcher to install the generated APK if Android asks.
6. Open the installed game directly. Dual Screen asks you to choose your hardware
   on first boot; the same choice remains available in **Display Options**.

**Updates install over the existing edition — saves are kept.** Back up your saves
before updating. Uninstalling the game deletes its app-private saves. Standard and
Dual Screen install as separate applications and have separate save storage.

This package contains the Android autopatcher and separate code-only source
archives for the autopatcher and both game editions. A standalone
Windows patcher executable and ready-to-run desktop patch bundle are not included.

## Standard and Dual Screen

- **Standard** keeps gameplay and menus on one screen, with touch and controller
  support. Display scaling is automatic.
- **Dual Screen** puts gameplay above and a live map, health and items below.
  Touch can pan and zoom the map while gameplay continues. Fit Map frames the
  revealed map; Follow returns to the last manual zoom and tracks the player's
  map cell. Pausing freezes and dims the
  upper screen while the lower map, inventory, logs and settings remain usable.

The gameplay aspect-ratio setting affects only the upper screen. RG DS locks that
screen to 4:3. Retroid Pocket Duo and Duo Lite share a profile. Dual Screen uses
the handheld's controller for Samus and the lower touch interface for the map and
menus; it does not use the Standard edition's on-screen gameplay controls.

## Save backup and restore

Both editions use **Options → Data → Backup / Restore**. Backups can transfer
progress between Standard, Dual Screen and **PC Community Updates 1.5.5**.
Choose the incoming save and destination slot before confirming a restore.
Settings and controller bindings stay specific to each installation.

For Android to PC, copy the files inside the timestamped backup folder into the
PC save directory while the game is closed. Preserve a copy of existing PC saves.
Windows uses `%LOCALAPPDATA%\AM2R`; the tested Linux build uses `~/.config/AM2R`.
For PC to Android, select a folder containing the PC saves in Restore, then choose
the incoming and destination slots. Records and unlocks are a separate choice.
The original 1.1 ZIP is the patching input; compatibility with 1.1 or modded save
files is not implied.

## How it works

The autopatcher bundles separate patch data for each edition: `wrapper.bin`
(the port's engine, code and community content with the original-game byte ranges
cut out), `droid.xdelta` (a binary delta that reconstructs the game data **from
your 1.1 copy**), and `assembly.json` (the splice plan, with a checksum for every
segment).

It verifies your `data.win`, rebuilds the game data with xdelta3, splices everything
back together in order, and accepts the result only if the final hash matches the
expected build. Because reconstruction is byte-exact, the signature carries over:
every successfully patched APK for an edition is identical to that edition's
signed build. A complete playable game APK is not included in this package.

## Checksums and source

The release has five attachments:

- `AM2R-Autopatcher-1.5.5.9.apk`
- `AM2R-Autopatcher-1.5.5.9-source.zip`
- `AM2R-1.5.5.9-standard-source.zip`
- `AM2R-1.5.5.9-dual-source.zip`
- `SHA256SUMS`

Download them into the same folder. On Linux, run:

```sh
sha256sum -c SHA256SUMS
```

Both game editions have been reconstructed byte-for-byte from the patch data
inside this autopatcher. The checksums cover the APK and all three source archives.

The autopatcher source contains its Android Java/C implementation, Python tools,
build definitions, tests and license notices. The two game sources contain GML,
Android extensions, shader code and GameMaker project/resource definitions.
Their media exclusions follow the
[Community Updates source publication](https://github.com/AM2R-Community-Developers/AM2R-Community-Updates).

Images, audio, font payloads, datafiles, compiled binaries, generated patch payloads,
signing material and revision history are excluded from these source archives.
Each README and asset manifest explains the additional local inputs needed to
build. GameMaker Beta IDE 2026.100.0.1083 and runtime 2026.100.0.1046 are pinned.
The older upstream GMX asset-import instructions are not a verified importer for
this converted YYP project. A rebuild from only the original 1.1 ZIP remains
unverified. Code retains its included license conditions and required attribution.

## Credits & licenses

- **AM2R** was created by DoctorM64 and team; **Community Updates** by the AM2R
  Community Developers. Game content retains its original rights. This is an
  unofficial fan project, not affiliated with or endorsed by Nintendo.
- Distribution model and inspiration: the AM2R Community Developers'
  [autopatcher](https://github.com/AM2R-Community-Developers/AM2R-Autopatcher-Windows).
- The palette system adapts PixelatedPope's
  [Retro Palette Swapper](https://github.com/PixelatedPope/RetroPaletteSwapper)
  under its included MIT license.
- xdelta3 by Josh MacDonald retains its Apache 2.0 license. Its notice is included
  in the autopatcher source at `app-android/app/src/main/jni/xdelta3/LICENSE`.
- Autopatcher code retains its MIT notice. This does not relicense inherited
  game content or third-party components.
