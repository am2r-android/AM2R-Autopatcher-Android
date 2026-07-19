# AM2R for Android — Autopatcher

Turns **your own copy** of the original AM2R 1.1 release (`AM2R_11.zip`) into the
Android port APK, entirely on your computer. Nothing playable is distributed here:
the game data is rebuilt from your files, and the result is verified byte-for-byte
against the official release checksum before it's written.

## What you need

- Your copy of the original **AM2R 1.1** release (`AM2R_11.zip`, the 2016 freeware
  release). A Community Updates or modded copy will **not** work — the patcher
  checks the exact original `data.win`.
- **Windows:** just run `AM2R-Android-Patcher.exe`.
- **Linux/macOS:** Python 3.8+ and `xdelta3` (Linux: `pacman -S xdelta3` /
  `apt install xdelta3`; the Windows build ships its own).

## How to use

1. Download the latest autopatcher release and extract it anywhere.
2. Run the patcher (double-click the exe, or `python3 am2r_android_patcher.py`).
3. Pick your `AM2R_11.zip`. Wait ~a minute.
4. `AM2R-<version>-android-arm64.apk` appears next to your zip, with its checksum
   shown — it matches the SHA-256 published in the release notes, so you can
   independently verify you built the genuine thing.
5. Copy the APK to your phone and install it (Android will ask you to allow
   installs from your file manager). **Updates install right over the old
   version — saves are kept.**

CLI: `am2r_android_patcher.py --zip /path/to/AM2R_11.zip [--out DIR]`

## How it works

The release hosts three things: `wrapper.bin` (the port's engine, code and
community content with the original-game byte ranges cut out), `droid.xdelta`
(a binary delta that reconstructs the game data **from your 1.1 copy**), and
`assembly.json` (the splice plan, with a checksum for every segment). The
patcher verifies your `data.win`, rebuilds the game data with xdelta3, splices
everything back together in order, and accepts the result only if the final
hash equals the official release hash. Because the reconstruction is
byte-exact, the official signature carries over — every patched APK is
identical to the release build, signed with the same key.

## Credits

Model and inspiration: the AM2R Community Developers'
[autopatcher](https://github.com/AM2R-Community-Developers/AM2R-Autopatcher-Windows).
xdelta3 by Josh MacDonald (Apache 2.0; see `vendor/xdelta3/LICENSE`).
