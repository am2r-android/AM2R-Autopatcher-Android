# AM2R for Android — Autopatcher

Turns **your own copy** of the original AM2R 1.1 release (`AM2R_11.zip`) into the
Standard or Dual Screen Android APK, entirely on your Android device. Nothing
playable is distributed here: the game data is rebuilt from your files, and the
result is verified byte-for-byte against this build's checksum before installation.

The port is based on **AM2R Community Updates 1.5.5** and runs natively on ARM64
Android. The autopatcher creates the game APK. **Once installed, the game launches
independently and does not require the autopatcher to remain installed.**

**1.5.5.13 Beta 2 — water crash fix.**

Fixes the error when water appears on screen in Standard and Dual Screen,
with Nano enabled or disabled.

Both editions include **Display Options → Nano** for devices that need lighter
visual effects. It reduces decorative particles, some lighting and glow effects,
and background map animation while keeping normal game speed, combat and controls. Turn it off to
restore full visuals. Other display and rendering efficiencies apply in both modes.

Dual Screen adds **Swap Displays** for the RG DS profile. First-time RG DS setup
previews the screen order and starts Nano enabled. Both choices remain adjustable
in Display Options; updates preserve saved preferences. RG DS has been tested on stock Android,
with additional community GammaOS testing. Nano improves performance.

Includes the gallery fix from 1.5.5.12 Hotfix 1 and the earlier UI improvements.

Both editions improve frame pacing, exploration and rendering efficiency while
preserving the game's artwork and gameplay. Dual Screen has a redesigned lower
HUD and consistent menu buttons with controller hints. Returning to the game
loads textures faster, and title scrolling follows the music on slower hardware.

Standard now offers **Display Options → Aspect Ratio: 4:3 / 16:9 / 3:2**.
The 3:2 view fits GBA-shaped displays without stretching the artwork. If changed
during gameplay, it takes effect at the next room transition. Existing widescreen
preferences are preserved when updating.

Standard offers draggable **Touch Control Scale** and **Control Spacing** sliders
under Control Options. The visible controls and touch targets move together;
camera-cutout clearances remain automatic. Display Options includes
**Orientation: Landscape / Portrait / Automatic**. Landscape is the default.
For a Surface Duo, select the desired orientation and use Android's unspanned
app window on the intended panel. A spanned upper/lower Surface Duo layout is
not implemented.

The 1.5.5.11 Hotfix 1 Hold-mode Weapon Select button remains above Arm when both missile
types are available. Hold Arm and tap Weapon Select to switch missile types.
Toggle mode and physical controller controls keep their existing behavior.

Discovered Genetics Laboratory entrance rooms retain their area title,
including the Hydro Station pipe and visits after a save transfer. GF Research
Site has its own discovered-area title and background. The Tower's exterior
title appears after its area scan, and the Waterfalls rooms now show their title.

Earlier features remain included: improved Standard touch timing,
shared-folder save synchronization, Mining
Facility artwork, the controller splash, refreshed icons and installation
failure reports. Normal gameplay and controller mappings remain unchanged.

## What you need

- Your copy of the original **AM2R 1.1** Windows release (`AM2R_11.zip`, the 2016
  freeware release). A Community Updates or modded copy will **not** work — the
  autopatcher checks the exact original `data.win`.
- An **ARM64 Android device running Android 10 or newer** for the autopatcher.
- For Dual Screen, an Android handheld with two displays and built-in controls.
  Included profiles are **AYN Thor**, **RG DS**, **Retroid Pocket Duo / Duo Lite**
  and **AYANEO Pocket DS**.
  - **AYN Thor:** tested and working.
  - **AYANEO Pocket DS:** tested and working, confirmed through community feedback.
  - **Retroid Pocket Duo / Duo Lite:** both tested and working through community feedback.
    Duo Lite testing also confirmed audio remains working after switching apps.
  - **RG DS:** tested on stock Android, with additional community GammaOS testing.
    Swap Displays corrects reversed screen order; Nano helps performance.
    Demanding scenes can still slow down.
  RG DS Plus is not included.

## How to use

1. Put your `AM2R_11.zip` somewhere on the device that Android's file picker can
   reach, such as Downloads or an SD card.
2. Install the **AM2R-Autopatcher-1.5.5.13-beta2.apk** from the release. Allow installation
   from your browser or file manager when Android asks.
3. Open **AM2R Patcher** and choose **Standard** for one screen, or **Dual Screen**
   for a handheld with one of the included profiles.
4. Select your original `AM2R_11.zip` and wait for patching to finish. The verified
   game APK is saved in **Downloads**. Your original ZIP stays unchanged.
5. Tap **Install Standard** or **Install Dual Screen** to open Android's installer.
   Allow AM2R Patcher to install the generated APK if Android asks.
6. If installation fails, the patcher saves a diagnostic report in Downloads.
   Use its Share action or attach the file to a GitHub issue. It is never uploaded
   automatically. The generated APK remains in Downloads.
7. Open the installed game directly. Dual Screen asks you to choose your hardware
   on first boot; the same choice remains available in **Display Options**.
   RG DS then asks you to check the screen order. Select Swap Displays until
   gameplay is on the intended panel, then Keep This Order.

**Updates install over the existing edition — saves are kept.** Back up your saves
before updating. Uninstalling the game deletes its app-private saves. Standard and
Dual Screen install as separate applications and have separate save storage.

Install the new Autopatcher and repatch your original ZIP to update either game.
Updating the Autopatcher alone does not replace an already installed game.

## Standard and Dual Screen

- **Standard** keeps gameplay and menus on one screen, with touch and controller
  support. Display scaling is automatic.
- **Dual Screen** puts gameplay above and a live map below. **Display Options →
  HUD Screen Position** keeps health and ammunition below or restores the
  original HUD above. One consistent lower-screen style is used for the map,
  menus, timer and completion percentage. Moving health and ammunition above
  removes their unused space below. The lower HUD uses blue energy bars,
  yellow health digits, grouped ammunition and Metroid counters. Unacquired
  Super Missile and Power Bomb panels stay hidden until acquired. Long area
  names fit within their title panel.
  A single bottom toolbar shows
  map controls during play and Marker / Menu / Unpause on the paused map.
  Touch can pan and zoom the map while gameplay continues. Fit Map frames the
  revealed map; Follow returns to the last manual zoom and tracks the player's
  map cell. Pausing freezes and dims the
  upper screen while the lower map, inventory, logs and settings remain usable. Area backgrounds change with the current subarea and its progress after its original environment scan has revealed it. The map
  stays visible during normal room transitions; reactor flashes affect both screens.
  The reactor temperature warning stays on the upper screen with either HUD choice.
  With Nano disabled, the lower display receives every game draw. Nano reuses
  an unchanged map image between updates; player, HUD and input changes refresh it.

In Dual Screen, the gameplay aspect-ratio setting affects only the upper screen. RG DS locks that
screen to 4:3. Retroid Pocket Duo and Duo Lite share a profile. Dual Screen uses
the handheld's controller for Samus and the lower touch interface for the map and
menus; it does not use the Standard edition's on-screen gameplay controls.

Both games retain their released package IDs and signing certificate. Android
labels are **AM2R: Return of Samus** and **AM2R: Return of Samus DS**.
Both game packages use version code **1005012**; Android version names remain
`1.5.5 arm64` and `1.5.5 arm64 DS`. The Autopatcher version is **1.5.5.13-beta2**,
version code **16**. The private beta applications remain separate from the release editions.

Game-category declarations are included for frontends. Cocoon documents manual
artwork and description editing, but automatic import of our metadata is not
confirmed. The source includes a central metadata definition and export helpers.
Both final game APKs pass static 16 KB ZIP and ELF alignment checks. Installation
and gameplay on 16 KB-page Android devices still need physical validation.

## Saves, backup and synchronization

Both editions use **Options → Data → Backup / Restore**. Backups can transfer
progress between Standard, Dual Screen and **PC Community Updates 1.5.5**.
Choose the incoming save and destination slot before confirming a restore.
Settings and controller bindings stay specific to each installation.

**Options → Data → Sync Folder** can connect either edition to a folder copied
by a service such as Syncthing. This is optional and disabled until you choose a
folder. The game checks for incoming progress before file selection and exports
after saving; it does not replace progress during active gameplay. Conflicting
changes are held for review, and recovery copies are retained. There is no built-in
network service: configure your preferred file-sync app separately. Read
**SAVE-SYNC.txt** for setup and recovery details. Real Syncthing transfers passed
between two isolated Android emulators and a Linux PC, including conflict review
and loading transferred progress. Physical-device sync, Windows/macOS and other
services remain unverified. Unmodified desktop builds do not read the sync
transport directly; use manual Backup / Restore for those transfers.

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

For a native game rebuild, apply both `prepare_game_metadata.py` and
`prepare_runtime_performance.py --runtime-copy /path/to/isolated/runtime` to the
pinned runtime copy. Build each edition using Android ARM64 YYC. The second
helper preserves the shared method cache; skipping it loses that optimization.
Keep the complete local projects and media separately from source exports.

## Release files

- `AM2R-Autopatcher-1.5.5.13-beta2.apk`
- `AM2R-Autopatcher-1.5.5.13-beta2-source.zip`
- `AM2R-1.5.5.13-beta2-standard-source.zip`
- `AM2R-1.5.5.13-beta2-dual-source.zip`
- `SHA256SUMS`

Run `sha256sum -c SHA256SUMS` from the download folder to verify all four files.
Source archives retain code, resource definitions and required license notices;
they omit artwork, audio, fonts, compiled games, generated patch payloads and
signing material. Rebuilding requires matching local assets and the documented
GameMaker runtime. Complete playable game APKs are not provided.

## Known limitations

Known issue — Dual Screen launcher compatibility

Some Cocoon setups may reduce performance because the launcher continues running
behind the lower display, particularly at low clock settings. This release keeps
the display behavior that restored audio on the Retroid Pocket Duo Lite.

The lower display can still take a moment to resume, especially at low clocks.
Performance varies with the device, power preset and other running applications.
Testing and community feedback do not guarantee identical performance on every
handheld or in every room. Both game APKs pass static 16 KB alignment checks; installation and gameplay on
16 KB-page devices still need physical validation. Surface Duo orientation is supported through an unspanned
Android app window; a foldable upper/lower split is not implemented.
