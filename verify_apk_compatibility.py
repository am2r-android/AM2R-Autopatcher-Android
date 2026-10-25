#!/usr/bin/env python3
"""Check a release APK's signing, SDK requirements and ARM64 page alignment.

Requires Android SDK build-tools and Python 3. This is a static gate, not a
substitute for installing and playing on native ARM64 devices.
"""
import argparse
import hashlib
import json
import re
import struct
import subprocess
import zipfile
from pathlib import Path


def inspect_elf(data):
    if len(data) < 64 or data[:6] != b'\x7fELF\x02\x01':
        raise ValueError('Expected a complete little-endian ELF64 header')
    if struct.unpack_from('<H', data, 18)[0] != 183:
        raise ValueError('Expected an ARM64 library')
    table = struct.unpack_from('<Q', data, 32)[0]
    stride, count = struct.unpack_from('<HH', data, 54)
    if stride < 56 or table + stride * count > len(data):
        raise ValueError('Truncated ELF program headers')
    loads, segments, relro = [], [], []
    for index in range(count):
        kind, flags, offset, address, _, size, memory, alignment = struct.unpack_from('<IIQQQQQQ', data, table + stride * index)
        if kind == 1:
            loads.append(alignment >= 16384 and (address - offset) % 16384 == 0)
            segments.append((address, address + memory, flags))
        elif kind == 0x6474e552:
            relro.append((address, address + memory))
    # Android rounds RELRO protection to page boundaries. A raw end need not
    # be aligned if the rounded range covers only RELRO bytes and padding.
    # Reject ranges that would protect mutable data or executable bytes.
    # https://android.googlesource.com/platform/bionic/+/android16-release/linker/linker_phdr.cpp
    protection_safe = True
    for start, end in relro:
        if start > end or not any(a <= start <= end <= b and flags & 2 for a, b, flags in segments):
            protection_safe = False
        rounded_start, rounded_end = start // 16384 * 16384, (end + 16383) // 16384 * 16384
        for a, b, flags in segments:
            low, high = max(a, rounded_start), min(b, rounded_end)
            if low >= high: continue
            if flags & 1 or (flags & 2 and not any(x <= low <= high <= y for x, y in relro)):
                protection_safe = False
    return {'loadAligned16KB': bool(loads) and all(loads),
            'relroAligned16KB': all(end % 16384 == 0 for _, end in relro),
            'relroProtectionSafe16KB': protection_safe,
            'compatible16KB': bool(loads) and all(loads) and protection_safe}


def check(apk, build_tools, previous=None, metadata=None):
    if previous and not Path(previous).is_file():
        raise FileNotFoundError("Previous APK is not available: " + str(previous))
    def run(name, *arguments):
        result = subprocess.run([str(build_tools / name), *map(str, arguments)],
                                text=True, capture_output=True)
        return result.returncode, result.stdout + result.stderr

    problems = []
    code, badging = run('aapt', 'dump', 'badging', apk)
    if code: raise ValueError('Android could not read the APK manifest')
    package = re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging)
    if not package: raise ValueError('APK package information is missing')
    minimum = re.search(r"sdkVersion:'(\d+)'", badging)
    target = re.search(r"targetSdkVersion:'(\d+)'", badging)
    if not minimum or not target: problems.append('SDK requirements are missing')
    if 'maxSdkVersion:' in badging: problems.append('A maximum Android version restricts installation')
    if 'application-debuggable' in badging: problems.append('Debuggable release APK')
    _, manifest = run('aapt', 'dump', 'xmltree', apk, 'AndroidManifest.xml')
    if re.search(r'android:(?:debuggable|testOnly).*0xffffffff', manifest):
        problems.append('Debuggable or test-only release APK')
    signature_code, signature = run('apksigner', 'verify', '--verbose', '--print-certs', apk)
    if signature_code: problems.append('APK signature verification failed')
    cert_pattern = r'Signer #1 certificate SHA-256 digest: ([0-9a-f]+)'
    certificate = re.search(cert_pattern, signature)
    if previous:
        old_code, old_signature = run('apksigner', 'verify', '--print-certs', previous)
        old_certificate = re.search(cert_pattern, old_signature)
        if old_code or not certificate or not old_certificate or certificate[1] != old_certificate[1]:
            problems.append('Signing identity differs from the previous APK')
    zip_code, _ = run('zipalign', '-c', '-P', '16', '4', apk)
    if zip_code: problems.append('APK ZIP alignment verification failed')
    libraries = []
    with zipfile.ZipFile(apk) as archive, apk.open('rb') as raw:
        for entry in archive.infolist():
            if not entry.filename.startswith('lib/') or not entry.filename.endswith('.so'): continue
            if not entry.filename.startswith('lib/arm64-v8a/'):
                problems.append('Unexpected native ABI: ' + entry.filename)
                continue
            raw.seek(entry.header_offset + 26)
            name_size, extra_size = struct.unpack('<HH', raw.read(4))
            offset = entry.header_offset + 30 + name_size + extra_size
            info = inspect_elf(archive.read(entry))
            info.update(library=entry.filename,
                        zipAligned16KB=entry.compress_type != zipfile.ZIP_STORED or offset % 16384 == 0)
            libraries.append(info)
            if not info['compatible16KB'] or not info['zipAligned16KB']:
                problems.append('Native 16 KB checks failed: ' + entry.filename)
    if not libraries: problems.append('No ARM64 native libraries found')
    if metadata is None:
        metadata = Path(__file__).parent / 'game-metadata/game.json'
    if package[1] in ('com.community.am2r', 'com.community.am2rdual'):
        identities = json.loads(Path(metadata).read_text())['editions'].values()
        expected = next((entry for entry in identities if entry['package_name'] == package[1]), None)
        if expected is None or int(package[2]) != expected['version_code'] or package[3] != expected['version_name']:
            problems.append('Game version differs from the shared metadata')
    if previous:
        old_status, old_badging = run('aapt', 'dump', 'badging', previous)
        old_package = re.search(r"package: name='([^']+)' versionCode='([^']+)'", old_badging)
        if old_status or not old_package or old_package[1] != package[1]:
            problems.append('Package identity differs from the previous APK')
        elif int(package[2]) < int(old_package[2]):
            problems.append('Version code is lower than the previous APK')
    with apk.open('rb') as stream: sha = hashlib.file_digest(stream, 'sha256').hexdigest()
    return {'apk': apk.name, 'sha256': sha, 'package': package[1], 'versionCode': package[2],
            'versionName': package[3], 'minSdk': int(minimum[1]) if minimum else None,
            'targetSdk': int(target[1]) if target else None,
            'signatureVerified': signature_code == 0, 'zipAligned16KB': zip_code == 0,
            'libraries': libraries, 'problems': problems, 'passed': not problems}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', type=Path)
    parser.add_argument('--build-tools', required=True, type=Path)
    parser.add_argument('--previous', type=Path, help='Previous APK whose signing identity must match')
    parser.add_argument('--output', type=Path)
    parser.add_argument('--metadata', type=Path, help='Shared game.json identity definition')
    args = parser.parse_args()
    report = check(args.apk, args.build_tools, args.previous, args.metadata)
    text = json.dumps(report, indent=2) + '\n'
    if args.output: args.output.write_text(text)
    print(text, end='')
    return 0 if report['passed'] else 1


if __name__ == '__main__': raise SystemExit(main())
