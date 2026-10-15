#!/usr/bin/env python3
"""Export the shared game details and original artwork without rebuilding an APK."""

import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import zipfile


def export_metadata(source, destination):
    source, destination = Path(source), Path(destination)
    data = json.loads((source / "game.json").read_text(encoding="utf-8"))
    members = {"game.json": (source / "game.json").read_bytes()}
    members["description.txt"] = (data["game"]["description"] + "\n").encode("utf-8")
    for entry in data["artwork"].values():
        relative = PurePosixPath(entry["path"])
        if relative.is_absolute() or ".." in relative.parts or "\\" in entry["path"]:
            raise ValueError("Artwork paths must stay inside the metadata directory")
        content = (source / relative).read_bytes()
        if hashlib.sha256(content).hexdigest() != entry["sha256"]:
            raise ValueError("Artwork has changed: " + entry["path"])
        members[entry["path"]] = content
    for name, edition in data["editions"].items():
        resolved = dict(data["game"], **edition, release=data["release"], artwork=data["artwork"])
        members[name + ".json"] = (json.dumps(resolved, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
    if (source / "README.txt").exists():
        members["README.txt"] = (source / "README.txt").read_bytes()
    members["SHA256SUMS"] = "".join(
        hashlib.sha256(content).hexdigest() + "  " + name + "\n"
        for name, content in sorted(members.items())
    ).encode("utf-8")
    # Keep previous exports intact and omit local timestamps and machine paths.
    with zipfile.ZipFile(destination, "x") as archive:
        for name, content in sorted(members.items()):
            info = zipfile.ZipInfo("AM2R-Game-Metadata/" + name, (2000, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            archive.writestr(info, content)
    return hashlib.sha256(destination.read_bytes()).hexdigest()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path)
    parser.add_argument("--source", type=Path, default=Path(__file__).parent / "game-metadata")
    args = parser.parse_args()
    print(export_metadata(args.source, args.output), args.output.name)
