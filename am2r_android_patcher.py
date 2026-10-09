#!/usr/bin/env python3
"""AM2R for Android — Autopatcher.

Turns YOUR copy of the original AM2R 1.1 release into the Android port APK.
Nothing playable is distributed with this tool: the game data is rebuilt
locally from your own files, and the result is verified byte-for-byte
against the official release checksum.

GUI:  double-click / run with no arguments
CLI:  am2r_android_patcher.py --zip /path/to/AM2R_11.zip [--out DIR] [--no-gui]
"""

import argparse
import hashlib
import json
import os
import platform
import shutil
import subprocess
import sys
import tempfile
import threading
import zipfile
from pathlib import Path


# ----------------------------------------------------------------------------
# Core

class PatchError(Exception):
    pass


def base_dir():
    # Patch data sits next to this script / frozen executable.
    if getattr(sys, "frozen", False):
        return Path(sys.executable).resolve().parent
    return Path(__file__).resolve().parent


def find_xdelta3():
    # Prefer the bundled decoder for this platform, then fall back to a decoder on the executable path.
    exe = "xdelta3.exe" if os.name == "nt" else "xdelta3"
    plat = {"Windows": "windows-x64", "Linux": "linux-x64", "Darwin": "macos"}.get(platform.system())
    if plat:
        vendored = base_dir() / "vendor" / "xdelta3" / plat / exe
        if vendored.is_file():
            return str(vendored)
    found = shutil.which("xdelta3")
    if found:
        return found
    raise PatchError(
        "xdelta3 not found. Install it (Linux: your package manager; "
        "Windows/macOS: it should have shipped in this download's vendor/ folder).")


def sha256_file(path, chunk=1 << 20):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        while True:
            b = f.read(chunk)
            if not b:
                break
            h.update(b)
    return h.hexdigest()


def load_manifest(edition="standard", patch_root=None):
    if edition not in ("standard", "dual"):
        raise PatchError("Choose the Standard or Dual Screen edition.")
    root = Path(patch_root) if patch_root else base_dir() / "patch-bundle"
    p = root / edition / "assembly.json"
    # Older single-edition downloads remain usable; never substitute them for Dual Screen.
    if patch_root is None and not root.exists() and edition == "standard":
        p = base_dir() / "patch-data" / "assembly.json"
    if not p.is_file():
        raise PatchError(f"patch data missing: {p}")
    return json.loads(p.read_text()), p.parent


def locate_datawin(source, expected_sha, progress):
    """Find data.win inside the user's zip (any nesting) or folder.
    Returns a temp file path holding the verified data.win."""
    progress(0, 1, "Checking your AM2R 1.1 copy…")
    source = Path(source)
    tmp = tempfile.NamedTemporaryFile(delete=False, suffix=".data.win")
    tmp_path = Path(tmp.name)
    try:
        # Search extracted copies for matching game data before copying a verified candidate into temporary storage.
        if source.is_dir():
            for cand in sorted(source.rglob("*")):
                if cand.is_file() and cand.name.lower() == "data.win":
                    if sha256_file(cand) == expected_sha:
                        tmp.close()
                        shutil.copyfile(cand, tmp_path)
                        return tmp_path
            raise PatchError(
                "No matching data.win found in that folder.\n"
                "Make sure it is the original AM2R 1.1 release.")
        if not zipfile.is_zipfile(source):
            raise PatchError(f"{source.name} is not a zip file.")
        with zipfile.ZipFile(source) as z:
            names = [n for n in z.namelist() if n.lower().endswith("data.win")]
            if not names:
                raise PatchError(
                    "That zip has no data.win inside.\n"
                    "You need the original AM2R 1.1 release (AM2R_11.zip).")
            # Hash each ZIP candidate before retaining its bytes as the delta source.
            for name in names:
                h = hashlib.sha256()
                with z.open(name) as f:
                    while True:
                        b = f.read(1 << 20)
                        if not b:
                            break
                        h.update(b)
                if h.hexdigest() == expected_sha:
                    with z.open(name) as f:
                        shutil.copyfileobj(f, tmp)
                    tmp.close()
                    return tmp_path
        raise PatchError(
            "The data.win inside that zip is not AM2R 1.1.\n"
            "This patcher needs the original, unmodified 1.1 release —\n"
            "a modded or Community-Updates copy will not work.")
    # Remove the temporary source if no acceptable copy could be returned to the patch operation.
    except Exception:
        tmp.close()
        tmp_path.unlink(missing_ok=True)
        raise


def read_zip_member(source, member):
    source = Path(source)
    if source.is_dir():
        p = source / member
        if not p.is_file():
            raise PatchError(f"missing file in your 1.1 copy: {member}")
        return p.read_bytes()
    # Allow an original ZIP to wrap its member paths in a containing directory.
    with zipfile.ZipFile(source) as z:
        for n in z.namelist():
            if n == member or n.endswith("/" + member):
                return z.read(n)
    raise PatchError(f"missing file in your 1.1 zip: {member}")


def patch(source_zip, out_dir, progress, edition="standard", patch_root=None):
    """Build the APK. Returns (apk_path, sha256)."""
    # Keep the selected edition's manifest, wrapper, and delta together for the entire operation.
    manifest, data_dir = load_manifest(edition, patch_root)
    xdelta = find_xdelta3()
    wrapper = data_dir / "wrapper.bin"
    if not wrapper.is_file():
        raise PatchError(f"patch data missing: {wrapper}")

    datawin = locate_datawin(source_zip, manifest["datawin_sha256"], progress)
    with tempfile.NamedTemporaryFile(delete=False, suffix=".droid") as temporary_droid:
        droid_tmp = Path(temporary_droid.name)
    try:
        progress(0, 1, "Rebuilding game data from your copy…")
        # Rebuild game data in temporary storage and check its hash before assembling the APK.
        r = subprocess.run(
            [xdelta, "-d", "-f", "-s", str(datawin), str(data_dir / manifest["droid"]["xdelta"]), str(droid_tmp)],
            capture_output=True, text=True)
        if r.returncode != 0:
            raise PatchError(f"xdelta3 failed:\n{r.stderr.strip()}")
        if sha256_file(droid_tmp) != manifest["droid"]["sha256"]:
            raise PatchError("Rebuilt game data failed verification (corrupt download?).")

        out_dir = Path(out_dir)
        out_dir.mkdir(parents=True, exist_ok=True)
        name = manifest["apk_name"]
        if Path(name).name != name or not name.endswith(".apk"):
            raise PatchError("Invalid output filename in patch data.")
        apk_path = out_dir / name
        total = manifest["final_size"]
        done = 0
        final = hashlib.sha256()

        # Assemble beside the destination under a temporary name until the full output passes verification.
        temporary = tempfile.NamedTemporaryFile(prefix=".patch-", suffix=".tmp", dir=out_dir, delete=False)
        pending = Path(temporary.name)
        try:
            with temporary as out, open(wrapper, "rb") as w, open(droid_tmp, "rb") as d:
                assemble(manifest, source_zip, out, w, d, final, progress)
            digest = final.hexdigest()
            done = pending.stat().st_size
            if done != total or digest != manifest["final_sha256"]:
                raise PatchError("Final APK failed verification — patching aborted, nothing was kept.")
            # Keep an existing matching APK, but refuse to replace a different file with the same output name.
            if apk_path.exists():
                if sha256_file(apk_path) != digest:
                    raise PatchError("A different file already uses the output name. Choose another output folder.")
            else:
                os.replace(pending, apk_path)
        # Remove the temporary APK after either a successful move or a failed verification.
        finally:
            pending.unlink(missing_ok=True)
        progress(total, total, "Done")
        return apk_path, digest
    # Release the verified source copy and rebuilt game data when this operation ends.
    finally:
        datawin.unlink(missing_ok=True)
        droid_tmp.unlink(missing_ok=True)


def assemble(manifest, source_zip, out, w, d, final, progress):
    total = manifest["final_size"]
    done = 0
    # Reconstruct the original APK byte order from wrapper ranges, rebuilt game data, and source ZIP members.
    for seg in manifest["segments"]:
        if seg["source"] == "wrapper":
            w.seek(seg["offset"])
            remaining = seg["length"]
            seg_h = hashlib.sha256()
            while remaining:
                chunk = w.read(min(1 << 20, remaining))
                if not chunk:
                    raise PatchError("wrapper.bin truncated (corrupt download?)")
                seg_h.update(chunk)
                final.update(chunk)
                out.write(chunk)
                remaining -= len(chunk)
                done += len(chunk)
                progress(done, total, "Assembling APK…")
            # Check each wrapper range as well as the complete APK hash checked by the caller.
            if seg_h.hexdigest() != seg["sha256"]:
                raise PatchError("wrapper.bin failed verification (corrupt download?)")
        elif seg["source"] == "droid":
            d.seek(0)
            while True:
                chunk = d.read(1 << 20)
                if not chunk:
                    break
                final.update(chunk)
                out.write(chunk)
                done += len(chunk)
                progress(done, total, "Assembling APK…")
        # Verify each original member before adding it to the temporary output.
        elif seg["source"] == "zip":
            chunk = read_zip_member(source_zip, seg["path"])
            if hashlib.sha256(chunk).hexdigest() != seg["sha256"]:
                raise PatchError(f"file from your 1.1 copy failed verification: {seg['path']}")
            final.update(chunk)
            out.write(chunk)
            done += len(chunk)
            progress(done, total, "Assembling APK…")
        else:
            raise PatchError(f"unknown segment source {seg['source']!r}")


# ----------------------------------------------------------------------------
# CLI

def run_cli(args):
    last = [-1]

    # Refresh terminal progress only when its displayed percentage changes.
    def progress(done, total, msg):
        pct = 100 * done // total if total > 1 else 0
        if pct == last[0]:
            return
        last[0] = pct
        sys.stdout.write(f"\r{msg} {pct:3d}%" if total > 1 else f"\r{msg}")
        sys.stdout.flush()

    try:
        apk, digest = patch(args.zip, args.out or Path(args.zip).resolve().parent, progress,
                            edition=args.edition, patch_root=args.patch_data)
    except PatchError as e:
        print(f"\nERROR: {e}", file=sys.stderr)
        return 1
    print(f"\n\nSuccess: {apk}")
    print(f"SHA-256: {digest}")
    print("This matches the official release checksum — the APK is genuine.")
    print("Copy it to your phone and install it (allow installs from your file manager).")
    return 0


# ----------------------------------------------------------------------------
# GUI

def run_gui():
    import tkinter as tk
    from tkinter import filedialog, messagebox, ttk

    root = tk.Tk()
    root.title("AM2R for Android — Autopatcher")
    root.geometry("560x350")
    root.resizable(False, False)

    frame = ttk.Frame(root, padding=20)
    frame.pack(fill="both", expand=True)

    ttk.Label(frame, text="AM2R for Android", font=("", 16, "bold")).pack()
    ttk.Label(frame, wraplength=500, justify="center", text=(
        "Select your copy of the original AM2R 1.1 release (AM2R_11.zip).\n"
        "It will be patched into the Android port — your file is not modified.")).pack(pady=(8, 14))

    edition = ttk.Combobox(frame, values=("Standard — single screen", "Dual Screen — supported handhelds"), state="readonly", width=40)
    edition.current(0)
    edition.pack(pady=4)
    status = tk.StringVar(value="Waiting for your AM2R_11.zip…")
    bar = ttk.Progressbar(frame, length=500, mode="determinate", maximum=1000)
    bar.pack(pady=4)
    ttk.Label(frame, textvariable=status, wraplength=500, justify="center").pack(pady=4)

    button = ttk.Button(frame, text="Choose AM2R_11.zip…")
    button.pack(pady=8)

    # Queue progress widget changes on the GUI thread while the worker continues patching.
    def progress(done, total, msg):
        root.after(0, lambda: (bar.configure(value=1000 * done / max(total, 1)),
                               status.set(msg)))

    # Return failures and verified results to the GUI through callbacks that also unlock the controls.
    def work(path, selected):
        try:
            apk, digest = patch(path, Path(path).resolve().parent, progress, edition=selected)
        except PatchError as e:
            root.after(0, lambda error=str(e): (status.set("Failed."), button.configure(state="normal"), edition.configure(state="readonly"),
                                   messagebox.showerror("Patching failed", error)))
            return
        except Exception as e:  # unexpected
            root.after(0, lambda error=repr(e): (status.set("Failed."), button.configure(state="normal"), edition.configure(state="readonly"),
                                   messagebox.showerror("Unexpected error", error)))
            return
        root.after(0, lambda: (
            status.set(f"Done!  {apk.name} is next to your zip.\nChecksum verified against the official release."),
            button.configure(state="normal"),
            edition.configure(state="readonly"),
            messagebox.showinfo("Success", f"Created:\n{apk}\n\nSHA-256:\n{digest}\n\n"
                                           "Checksum matches the official release. Copy the APK to your "
                                           "phone and install it.")))

    def choose():
        path = filedialog.askopenfilename(title="Select AM2R_11.zip",
                                          filetypes=[("Zip files", "*.zip"), ("All files", "*.*")])
        if not path:
            return
        button.configure(state="disabled")
        # Capture the edition before starting the worker so later UI state cannot redirect this operation.
        selected = ("standard", "dual")[edition.current()]
        edition.configure(state="disabled")
        threading.Thread(target=work, args=(path, selected), daemon=True).start()

    button.configure(command=choose)
    root.mainloop()
    return 0


# ----------------------------------------------------------------------------

def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--zip", help="path to AM2R_11.zip (or an extracted 1.1 folder)")
    ap.add_argument("--out", help="output directory (default: next to the zip)")
    ap.add_argument("--edition", choices=("standard", "dual"), default="standard", help="Android edition to build")
    ap.add_argument("--patch-data", type=Path, help="directory containing the standard and dual patch folders")
    ap.add_argument("--no-gui", action="store_true", help="force CLI mode")
    args = ap.parse_args()

    # An input path selects the command-line flow; otherwise launch the GUI unless it was explicitly disabled.
    if args.zip:
        return run_cli(args)
    if args.no_gui:
        ap.error("--no-gui requires --zip")
    try:
        return run_gui()
    except Exception:
        ap.error("GUI unavailable (no display / tkinter). Use: --zip /path/to/AM2R_11.zip")


if __name__ == "__main__":
    sys.exit(main())
