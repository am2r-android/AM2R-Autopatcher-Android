#!/usr/bin/env python3
"""Apply the shared Android game identity and artwork to an isolated build copy."""

import argparse
import hashlib
import json
from pathlib import Path
import re

from PIL import Image


def read_metadata(directory, edition):
    directory = Path(directory)
    data = json.loads((directory / "game.json").read_text(encoding="utf-8"))
    identity = data["editions"][edition]
    expected = {"standard": "com.community.am2r", "dual": "com.community.am2rdual"}
    if identity["package_name"] != expected[edition]:
        raise ValueError("Keep the released package ID to preserve installed saves")
    if type(identity["version_code"]) is not int or identity["version_code"] <= 1005005:
        raise ValueError("The game version code must exceed the released 1005005")
    return data, identity


def replace_option(text, name, value):
    replacement = json.dumps(name) + ":" + json.dumps(value, ensure_ascii=False)
    text, count = re.subn(r'"' + re.escape(name) + r'":(?:"(?:\\.|[^"\\])*"|true|false|\d+)',
                          lambda match: replacement, text)
    if count != 1:
        raise ValueError("Expected one Android option: " + name)
    return text


def verified_image(directory, entry):
    path = Path(directory) / entry["path"]
    if hashlib.sha256(path.read_bytes()).hexdigest() != entry["sha256"]:
        raise ValueError("Artwork does not match the recorded original: " + entry["path"])
    with Image.open(path) as image:
        return image.convert("RGBA")


def prepare_project(directory, edition, project):
    data, identity = read_metadata(directory, edition)
    android = Path(project) / "options/android"
    options = android / "options_android.yy"
    text = options.read_text()
    current = json.loads(re.sub(r",\s*([}\]])", r"\1", text))
    package = ".".join(current["option_android_package_" + part] for part in ("domain", "company", "product"))
    if package != identity["package_name"]:
        raise ValueError("Refusing to rename a different project's package")
    injection = current["option_android_application_tag_inject"]
    if injection not in ("", 'android:appCategory="game"'):
        raise ValueError("Review the existing application attributes before adding the game category")
    text = replace_option(text, "option_android_display_name", identity["application_label"])
    text = replace_option(text, "option_android_application_tag_inject", 'android:appCategory="game"')
    text = replace_option(text, "option_android_tv_isgame", True)
    text = replace_option(text, "option_android_tv_banner", "tv_banner.png")
    options.write_text(text)

    # Keep the complete icon subject within the adaptive mask, with a separate opaque background.
    original = verified_image(directory, data["artwork"]["icon"])
    for density, scale in [("ldpi", .75), ("mdpi", 1), ("hdpi", 1.5), ("xhdpi", 2), ("xxhdpi", 3), ("xxxhdpi", 4)]:
        legacy_size, adaptive_size = round(48 * scale), round(108 * scale)
        foreground_size = round(80 * scale)
        foreground = Image.new("RGBA", (adaptive_size, adaptive_size))
        foreground.alpha_composite(original.resize((foreground_size, foreground_size), Image.Resampling.LANCZOS),
                                   ((adaptive_size - foreground_size) // 2,) * 2)
        images = {
            "icons": original.resize((legacy_size, legacy_size), Image.Resampling.LANCZOS),
            "icons_adaptive": foreground,
            "icons_adaptivebg": Image.new("RGBA", (adaptive_size, adaptive_size), (0, 0, 0, 255)),
        }
        for folder, image in images.items():
            image.save(android / folder / (density + ".png"))
    # Fit the original 16:10 artwork into the Android TV banner without changing its proportions.
    hero = verified_image(directory, data["artwork"]["hero"])
    hero.thumbnail((320, 180), Image.Resampling.LANCZOS)
    banner = Image.new("RGBA", (320, 180), (0, 0, 0, 255))
    banner.alpha_composite(hero, ((320 - hero.width) // 2, (180 - hero.height) // 2))
    banner.save(android / "tv_banner.png")


def prepare_runtime(directory, edition, runtime):
    _, identity = read_metadata(directory, edition)
    gradle = Path(runtime) / "android/runner/ProjectFiles/build.gradle"
    text = gradle.read_text()
    marker = '        applicationId "${YYAndroidPackageName}"'
    if text.count(marker) != 1:
        raise ValueError("Unexpected runtime template; use the pinned runtime's isolated copy")
    # Gradle overrides the engine's numeric manifest version without changing GameMaker's game ID.
    text = re.sub(r"\n        version(?:Code|Name) [^\n]*", "", text)
    text = text.replace(marker, marker + "\n        versionCode " + str(identity["version_code"])
                        + "\n        versionName " + json.dumps(identity["version_name"]))
    gradle.write_text(text)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("edition", choices=["standard", "dual"])
    parser.add_argument("--metadata", type=Path, default=Path(__file__).parent / "game-metadata")
    parser.add_argument("--project", type=Path)
    parser.add_argument("--runtime-copy", type=Path)
    args = parser.parse_args()
    if not args.project and not args.runtime_copy:
        parser.error("Provide an isolated --project and/or --runtime-copy")
    if args.project:
        prepare_project(args.metadata, args.edition, args.project)
    if args.runtime_copy:
        prepare_runtime(args.metadata, args.edition, args.runtime_copy)
