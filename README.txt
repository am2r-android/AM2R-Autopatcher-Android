AM2R Autopatcher 1.5.5.10 — source

This archive contains the Android Java/C implementation, Python patcher and
patch generator, build definitions, tests and dependency notices. The patcher
code is MIT-licensed; bundled xdelta3 source retains its Apache 2.0 license.

Artwork, audio, fonts, prebuilt executables and generated patch payloads are excluded.
Their expected paths and checksums are recorded in asset-inputs.json. This is
a source archive, not an installable game or a ready-to-run patch distribution.

Build inputs
Restore matching files from your private source folder or private source ZIP:

  python3 restore_local_assets.py --from-local /path/to/private/patcher/source

That helper does not download content or extract it from a compiled APK.
Alternatively, provide locally licensed artwork and fonts at the same resource
paths, and generate both patch-bundle folders using make_patch_data.py with
your own Standard and Dual Screen signed game builds and original 1.1 ZIP.
Replacement artwork requires its own review; the manifest describes originals.
Do not redistribute private game builds, restored assets or signing material.

Build environment
Use JDK 17, Gradle 8.10.2, Android Gradle integration 8.7.3, Android SDK 36 and
NDK 27.0.12077973. Configure the SDK through ANDROID_HOME or local.properties;
machine-specific configuration is excluded. The project has no Gradle wrapper.
From app-android, run your Gradle installation with assembleDebug. Release
signing reads AM2R_KS, AM2R_KS_PASS, AM2R_KS_ALIAS and AM2R_KS_KEYPASS from the
environment. Use your own key; the original release key is never provided.

Run python3 -m unittest -v test_patch_editions.py from this folder. The desktop
patcher requires Python 3.8+ and a separately installed xdelta3 executable.
Real patch reconstruction additionally needs the matching private inputs.
