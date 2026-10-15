import importlib.util
from pathlib import Path
import struct
import unittest

MODULE = Path(__file__).resolve().parents[1] / 'verify_apk_compatibility.py'
spec = importlib.util.spec_from_file_location('compatibility', MODULE)
compatibility = importlib.util.module_from_spec(spec)
spec.loader.exec_module(compatibility)


def elf(load_align=16384, relro_end=16384, machine=183):
    data = bytearray(64 + 2 * 56)
    data[:7] = b'\x7fELF\x02\x01\x01'
    struct.pack_into('<HHIQQQIHHHHHH', data, 16, 3, machine, 1, 0, 64, 0, 0, 64, 56, 2, 0, 0, 0)
    struct.pack_into('<IIQQQQQQ', data, 64, 1, 5, 0, 0, 0, 176, 176, load_align)
    struct.pack_into('<IIQQQQQQ', data, 120, 0x6474e552, 4, 0, 0x2000, 0, 128, relro_end - 0x2000, 1)
    return bytes(data)


class AlignmentChecks(unittest.TestCase):
    def test_aligned_arm64(self):
        self.assertTrue(compatibility.inspect_elf(elf())['compatible16KB'])

    def test_four_k_load_is_rejected(self):
        self.assertFalse(compatibility.inspect_elf(elf(load_align=4096))['compatible16KB'])

    def test_relro_end_is_checked_separately(self):
        result = compatibility.inspect_elf(elf(relro_end=12288))
        self.assertTrue(result['loadAligned16KB'])
        self.assertFalse(result['relroAligned16KB'])

    def test_truncated_header_is_rejected(self):
        with self.assertRaises(ValueError): compatibility.inspect_elf(elf()[:70])

    def test_non_arm64_is_rejected(self):
        with self.assertRaises(ValueError): compatibility.inspect_elf(elf(machine=62))

    def test_segment_congruence_is_checked(self):
        data = bytearray(elf())
        struct.pack_into('<Q', data, 80, 4096)
        self.assertFalse(compatibility.inspect_elf(data)['loadAligned16KB'])


if __name__ == '__main__': unittest.main()
