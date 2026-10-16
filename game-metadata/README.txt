AM2R game metadata and artwork

The shared game.json supplies both editions' Android identity. Keep the released
package names and release signing certificate: replacing either prevents an
ordinary update from retaining the same installed application's private saves.
The game version code is 1005007 for this release, above the previous 1005006.
The patcher application's own package and version are separate.

Artwork files retain the supplied originals. The hero is 1920x1200 (16:10);
portrait and box images retain their smaller original dimensions. Box images
are static artwork. The Android banner fits the hero without stretching it.
The launcher icon includes legacy images and an adaptive foreground/background.
The controller illustration is used only by the Dual Screen edition.

Export independently:
  python3 export_game_metadata.py AM2R-Game-Metadata.zip

For a game build, with the pinned GameMaker runtime in a private copy:
  python3 prepare_game_metadata.py standard --project /path/to/standard --runtime-copy /path/to/runtime
  python3 prepare_game_metadata.py dual --project /path/to/dual --runtime-copy /path/to/other-runtime

Use separate runtime copies for the two version names. The generator keeps
GameMaker's numeric project version unchanged and applies Android's version
code/name in the runtime Gradle template. Rebuild and sign each game, then
regenerate both patch bundles. The patcher reconstructs those signed APK bytes;
it does not rename, alter or sign the game during patching.

Cocoon, checked 2026-10-14
Both APKs declare Android's game category and retain the older isGame flag.
This makes the category available to frontends; automatic Cocoon classification
from this property has not been confirmed. No custom JSON-reading or package
mapping interface was found in Cocoon's public documentation or repository.
This export is a reusable local collection, not a supported one-click Cocoon
import format. The APK does not contain a pretend frontend integration.

Documented manual setup:
1. Select the installed app in Cocoon's App Drawer and press Y.
2. Use Treat as game if needed. Open Edit to set its title and description.
3. Use Edit Game Artwork to choose the icon, hero and logo from artwork/.
   Other images are available for supported artwork fields as static images.
4. Paste description.txt without changing its paragraphs. Set Android as the
   platform, 2016 as the original release year, and the desired launch display.

Cocoon documents LaunchBox and IGDB scraping and editable search titles. Search
AM2R: Return of Samus if using those services. Existing records do not prove an
automatic association with these package IDs. Protect manually entered fields
from later scraping if that option is enabled. ES-DE metadata import is also
documented, but association with native Android packages remains unverified.
A documented package-to-record mapping or metadata import interface would need
Cocoon developer support. No private database modification is required here.

References:
https://cocoon-shell.com/wiki/game-info-and-editing/
https://cocoon-shell.com/wiki/app-drawer-and-dock/
https://cocoon-shell.com/wiki/scraping/
https://cocoon-shell.com/wiki/es-de-migration/
https://github.com/inssekt/CocoonFE
https://developer.android.com/guide/topics/manifest/application-element
https://developer.android.com/studio/publish/versioning
https://developer.android.com/build/configure-app-module

Distribution
The source archive includes game.json and the helper scripts, without image
payloads. Artwork supplied for use in the app is not automatically licensed
for a separate public artwork collection. Keep the full artwork export local
unless permission covers that distribution. Existing authorship and notices
in the supplied images are preserved.
