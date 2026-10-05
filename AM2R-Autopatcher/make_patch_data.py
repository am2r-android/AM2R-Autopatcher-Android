#!/usr/bin/env python3
"""Disassemble the signed canonical Android APK into distributable patch data.

Run on the build machine once per release:

    make_patch_data.py <canonical.apk> <am2r_11_dir_or_zip> <out_dir>

Produces in <out_dir>:
    wrapper.bin     canonical APK with protected byte ranges removed
    droid.xdelta    xdelta3 delta: AM2R 1.1 data.win -> assets/game.droid payload
    assembly.json   ordered splice plan with per-segment sha256

Protected (cut) ranges:
  - the assets/game.droid entry payload (always; delivered via droid.xdelta)
  - any STORED entry payload byte-identical to a file shipped inside the
    user's AM2R 1.1 copy (delivered as copy-from-zip instructions)

The script finishes by reassembling the APK from its own outputs plus the
1.1 reference and asserting the result hashes to the canonical APK —
patch data is never emitted unverified.
"""

import hashlib
import json
import struct
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

DATAWIN_SHA256 = "36e4a251d7b687f2d742a8e911cb1e1185aea99e36529fcf32cd18d445a355e3"


def sha256_file(path, chunk=1 << 20):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        while True:
            b = f.read(chunk)
            if not b:
                break
            h.update(b)
    return h.hexdigest()


def sha256_bytes(b):
    return hashlib.sha256(b).hexdigest()


def local_payload_range(apk_path, info):
    """Return (start, end) of the raw payload bytes for a zip entry."""
    with open(apk_path, "rb") as f:
        f.seek(info.header_offset)
        hdr = f.read(30)
        if hdr[:4] != b"PK\x03\x04":
            raise RuntimeError(f"bad local header for {info.filename}")
        name_len, extra_len = struct.unpack("<HH", hdr[26:30])
        start = info.header_offset + 30 + name_len + extra_len
        return start, start + info.compress_size


def index_reference(ref):
    """Map size -> [(sha256, relpath)] for every file in the 1.1 reference."""
    files = {}
    ref = Path(ref)
    if ref.is_file():  # a zip
        with zipfile.ZipFile(ref) as z:
            for zi in z.infolist():
                if zi.is_dir():
                    continue
                data = z.read(zi)
                files.setdefault(len(data), []).append((sha256_bytes(data), zi.filename))
    else:
        for p in sorted(ref.rglob("*")):
            if p.is_file():
                files.setdefault(p.stat().st_size, []).append((sha256_file(p), str(p.relative_to(ref))))
    return files


def main(apk_path, ref_path, out_dir):
    apk_path, out_dir = Path(apk_path), Path(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    apk_size = apk_path.stat().st_size
    apk_sha = sha256_file(apk_path)

    ref_index = index_reference(ref_path)

    # Locate data.win in the reference for the xdelta source.
    datawin = None
    ref = Path(ref_path)
    if ref.is_dir():
        cand = ref / "data.win"
        if cand.is_file() and sha256_file(cand) == DATAWIN_SHA256:
            datawin = cand
    else:
        with zipfile.ZipFile(ref) as z, tempfile.NamedTemporaryFile(delete=False, suffix=".data.win") as tf:
            for zi in z.infolist():
                if zi.filename.endswith("data.win"):
                    data = z.read(zi)
                    if sha256_bytes(data) == DATAWIN_SHA256:
                        tf.write(data)
                        datawin = Path(tf.name)
                        break
    if datawin is None:
        sys.exit("ERROR: reference does not contain a data.win with the expected sha256")

    cuts = []  # (start, end, descriptor)
    with zipfile.ZipFile(apk_path) as z:
        droid = z.getinfo("assets/game.droid")
        if droid.compress_type != zipfile.ZIP_STORED:
            sys.exit("ERROR: assets/game.droid must be STORED in the canonical APK")
        d_start, d_end = local_payload_range(apk_path, droid)
        with open(apk_path, "rb") as f:
            f.seek(d_start)
            droid_bytes = f.read(d_end - d_start)
        cuts.append((d_start, d_end, {"source": "droid"}))

        for info in z.infolist():
            if info.filename == "assets/game.droid" or info.is_dir():
                continue
            if info.compress_type != zipfile.ZIP_STORED or info.compress_size == 0:
                continue
            matches = ref_index.get(info.compress_size)
            if not matches:
                continue
            s, e = local_payload_range(apk_path, info)
            with open(apk_path, "rb") as f:
                f.seek(s)
                payload_sha = sha256_bytes(f.read(e - s))
            for ref_sha, ref_rel in matches:
                if ref_sha == payload_sha:
                    cuts.append((s, e, {"source": "zip", "path": ref_rel,
                                        "sha256": payload_sha, "length": e - s,
                                        "apk_entry": info.filename}))
                    break

    cuts.sort(key=lambda c: c[0])

    # Emit droid.xdelta
    droid_tmp = out_dir / ".game.droid.tmp"
    droid_tmp.write_bytes(droid_bytes)
    xdelta_path = out_dir / "droid.xdelta"
    subprocess.run(["xdelta3", "-e", "-9", "-S", "djw", "-f",
                    "-s", str(datawin), str(droid_tmp), str(xdelta_path)], check=True)
    droid_sha = sha256_bytes(droid_bytes)
    droid_tmp.unlink()

    # Emit wrapper.bin + segment plan
    segments = []
    wrapper_path = out_dir / "wrapper.bin"
    with open(apk_path, "rb") as src, open(wrapper_path, "wb") as dst:
        pos = 0
        w_off = 0
        for start, end, desc in cuts:
            if start > pos:
                src.seek(pos)
                chunk = src.read(start - pos)
                dst.write(chunk)
                segments.append({"source": "wrapper", "offset": w_off,
                                 "length": len(chunk), "sha256": sha256_bytes(chunk)})
                w_off += len(chunk)
            segments.append(desc)
            pos = end
        if pos < apk_size:
            src.seek(pos)
            chunk = src.read()
            dst.write(chunk)
            segments.append({"source": "wrapper", "offset": w_off,
                             "length": len(chunk), "sha256": sha256_bytes(chunk)})

    version = apk_path.name.split("-")[1] if "-" in apk_path.name else "unknown"
    manifest = {
        "version": version,
        "apk_name": apk_path.name,
        "final_sha256": apk_sha,
        "final_size": apk_size,
        "datawin_sha256": DATAWIN_SHA256,
        "droid": {"size": len(droid_bytes), "sha256": droid_sha, "xdelta": "droid.xdelta"},
        "segments": segments,
    }
    (out_dir / "assembly.json").write_text(json.dumps(manifest, indent=1))

    # ---- self-check: reassemble and compare ----
    rebuilt = hashlib.sha256()
    total = 0
    with open(wrapper_path, "rb") as w:
        for seg in segments:
            if seg["source"] == "wrapper":
                w.seek(seg["offset"])
                chunk = w.read(seg["length"])
            elif seg["source"] == "droid":
                out = out_dir / ".rebuild.droid"
                subprocess.run(["xdelta3", "-d", "-f", "-s", str(datawin),
                                str(xdelta_path), str(out)], check=True)
                chunk = out.read_bytes()
                out.unlink()
                assert sha256_bytes(chunk) == droid_sha, "droid xdelta round-trip mismatch"
            elif seg["source"] == "zip":
                if Path(ref_path).is_dir():
                    chunk = (Path(ref_path) / seg["path"]).read_bytes()
                else:
                    with zipfile.ZipFile(ref_path) as z:
                        chunk = z.read(seg["path"])
                assert sha256_bytes(chunk) == seg["sha256"], f"zip segment mismatch {seg['path']}"
            rebuilt.update(chunk)
            total += len(chunk)
    assert total == apk_size, f"size mismatch {total} != {apk_size}"
    assert rebuilt.hexdigest() == apk_sha, "SELF-CHECK FAILED: reassembly != canonical"

    cut_bytes = sum(e - s for s, e, _ in cuts)
    print(f"OK  canonical: {apk_sha} ({apk_size:,} bytes)")
    print(f"    wrapper.bin: {wrapper_path.stat().st_size:,} bytes")
    print(f"    droid.xdelta: {xdelta_path.stat().st_size:,} bytes")
    print(f"    cut ranges: {len(cuts)} ({cut_bytes:,} bytes removed)")
    for _, _, d in cuts:
        if d["source"] == "zip":
            print(f"      copy-from-zip: {d['apk_entry']} <- {d['path']}")
    print("    SELF-CHECK PASSED — reassembly is byte-identical to canonical")


if __name__ == "__main__":
    if len(sys.argv) != 4:
        sys.exit(__doc__)
    main(*sys.argv[1:])
