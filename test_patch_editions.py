"""Exercise edition isolation and failed-output preservation with real deltas."""
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
import zipfile

import am2r_android_patcher as patcher


def digest(data):
    return hashlib.sha256(data).hexdigest()


class EditionTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.bundle = self.root / "patch-bundle"
        self.source = self.root / "original.zip"
        self.base = b"original game data\n" * 512
        self.extra = b"original audio"
        with zipfile.ZipFile(self.source, "w") as z:
            z.writestr("original/data.win", self.base)
            z.writestr("original/audio.ogg", self.extra)
        (self.root / "base").write_bytes(self.base)
        self.expected = {}
        # Give both editions different real delta outputs so a bundle mix-up cannot pass by coincidence.
        for edition in ("standard", "dual"):
            directory = self.bundle / edition
            directory.mkdir(parents=True)
            game = self.base + edition.encode()
            wrapper = b"APK header: " + edition.encode()
            (directory / "game").write_bytes(game)
            (directory / "wrapper.bin").write_bytes(wrapper)
            subprocess.run(["xdelta3", "-e", "-s", str(self.root / "base"),
                            str(directory / "game"), str(directory / "droid.xdelta")], check=True)
            result = wrapper + game + self.extra
            self.expected[edition] = result
            manifest = {"apk_name": edition + ".apk", "version": "test",
                        "final_sha256": digest(result), "final_size": len(result),
                        "datawin_sha256": digest(self.base),
                        "droid": {"xdelta": "droid.xdelta", "sha256": digest(game), "size": len(game)},
                        "segments": [{"source": "wrapper", "offset": 0, "length": len(wrapper), "sha256": digest(wrapper)},
                                     {"source": "droid"},
                                     {"source": "zip", "path": "audio.ogg", "length": len(self.extra), "sha256": digest(self.extra)}]}
            (directory / "assembly.json").write_text(json.dumps(manifest))

    def run_patch(self, edition="standard"):
        return patcher.patch(self.source, self.root / "out", lambda *_: None,
                             edition=edition, patch_root=self.bundle)

    def test_each_edition_reconstructs_its_own_verified_output(self):
        source_hash = digest(self.source.read_bytes())
        for edition in ("standard", "dual"):
            output, checksum = self.run_patch(edition)
            self.assertEqual(output.read_bytes(), self.expected[edition])
            self.assertEqual(checksum, digest(self.expected[edition]))
        self.assertEqual(source_hash, digest(self.source.read_bytes()))

    # Exercise failures with an existing destination to ensure rejected inputs leave prior output intact.
    def test_bad_wrapper_preserves_existing_output(self):
        output = self.root / "out" / "dual.apk"
        output.parent.mkdir(); output.write_bytes(b"previous valid output")
        (self.bundle / "dual/wrapper.bin").write_bytes(b"corrupt")
        with self.assertRaises(patcher.PatchError): self.run_patch("dual")
        self.assertEqual(output.read_bytes(), b"previous valid output")
        self.assertEqual(list(output.parent.iterdir()), [output])

    def test_wrong_input_produces_no_output(self):
        with zipfile.ZipFile(self.source, "w") as z: z.writestr("data.win", b"wrong release")
        with self.assertRaises(patcher.PatchError): self.run_patch()
        self.assertFalse((self.root / "out").exists())

    # Reject a complete but mismatched stream and check that its pending output is removed.
    def test_failed_final_hash_keeps_no_partial_apk(self):
        path = self.bundle / "dual/assembly.json"
        manifest = json.loads(path.read_text())
        manifest["final_sha256"] = "0" * 64
        path.write_text(json.dumps(manifest))
        with self.assertRaises(patcher.PatchError): self.run_patch("dual")
        self.assertEqual(list((self.root / "out").iterdir()), [])

    def test_valid_patch_does_not_replace_a_different_existing_file(self):
        output = self.root / "out/standard.apk"
        output.parent.mkdir(); output.write_bytes(b"keep this file")
        with self.assertRaises(patcher.PatchError): self.run_patch()
        self.assertEqual(output.read_bytes(), b"keep this file")
        self.assertEqual(list(output.parent.iterdir()), [output])

    def test_missing_dual_never_falls_back_to_standard(self):
        (self.bundle / "dual/assembly.json").unlink()
        with self.assertRaises(patcher.PatchError): self.run_patch("dual")

    def test_unknown_edition_is_rejected(self):
        with self.assertRaises(patcher.PatchError): self.run_patch("../standard")


if __name__ == "__main__":
    unittest.main()
