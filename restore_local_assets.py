#!/usr/bin/env python3
"""Restore excluded build inputs from a private matching source folder or ZIP.

This utility downloads nothing and never replaces an existing file.
"""
import argparse
from contextlib import contextmanager
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import tempfile
import zipfile


def relative_name(value):
    if not isinstance(value, str) or not value or '\\' in value or ':' in value:
        raise ValueError('Invalid relative file name')
    parts = value.split('/')
    if any(p in ('', '.', '..') for p in parts) or PurePosixPath(value).is_absolute():
        raise ValueError('Invalid relative file name: ' + value)
    return value


def local_path(root, name):
    path = root
    for part in relative_name(name).split('/'):
        path = path / part
        if path.is_symlink():
            raise ValueError('Symbolic links are not supported: ' + name)
    return path


def digest(stream):
    h = hashlib.sha256()
    size = 0
    for block in iter(lambda: stream.read(1024 * 1024), b''):
        h.update(block)
        size += len(block)
    return size, h.hexdigest()


def restore(project, donor):
    project, donor = Path(project).resolve(), Path(donor).resolve()
    manifest = json.loads((project / 'asset-inputs.json').read_text(encoding='utf-8'))
    if manifest.get('schema') != 1 or not isinstance(manifest.get('files'), list):
        raise ValueError('Unsupported asset manifest')
    rows, seen = [], set()
    for row in manifest['files']:
        name = relative_name(row['path'])
        if name in seen or type(row['size']) is not int or row['size'] < 0 or not re.fullmatch('[0-9a-f]{64}', row['sha256']):
            raise ValueError('Invalid asset record: ' + name)
        seen.add(name)
        rows.append((name, (row['size'], row['sha256']), local_path(project, name)))

    @contextmanager
    def sources():
        if donor.is_dir():
            yield lambda name: local_path(donor, name).open('rb')
        else:
            prefix = relative_name(manifest['donorArchiveRoot'])
            with zipfile.ZipFile(donor) as archive:
                if len(archive.namelist()) != len(set(archive.namelist())):
                    raise ValueError('Duplicate archive member')
                yield lambda name: archive.open(prefix + '/' + name)

    pending = []
    with sources() as open_source:
        for name, expected, target in rows:
            if target.exists():
                if not target.is_file():
                    raise FileExistsError('Existing non-file: ' + name)
                with target.open('rb') as stream:
                    if digest(stream) != expected:
                        raise FileExistsError('Existing file differs; preserved: ' + name)
                continue
            try:
                with open_source(name) as stream:
                    actual = digest(stream)
            except (FileNotFoundError, KeyError) as error:
                raise ValueError('Missing local input: ' + name) from error
            if actual != expected:
                raise ValueError('Local input does not match: ' + name)
            pending.append((name, expected, target))

        # All inputs and existing files are checked before any asset is written.
        for name, expected, target in pending:
            target.parent.mkdir(parents=True, exist_ok=True)
            local_path(project, name)
            with tempfile.TemporaryDirectory(prefix='.asset-', dir=target.parent) as directory:
                temporary = Path(directory) / 'input'
                with open_source(name) as source, temporary.open('xb') as output:
                    shutil.copyfileobj(source, output, 1024 * 1024)
                with temporary.open('rb') as stream:
                    if digest(stream) != expected:
                        raise ValueError('Local input changed while reading: ' + name)
                os.link(temporary, target)  # Atomic creation, fails if target now exists.
    return len(pending)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--project', type=Path, default=Path(__file__).resolve().parent)
    parser.add_argument('--from-local', type=Path, required=True, dest='donor')
    args = parser.parse_args()
    try:
        print('Restored', restore(args.project, args.donor), 'verified local inputs.')
    except (OSError, ValueError, KeyError, zipfile.BadZipFile) as error:
        parser.exit(1, str(error) + '\n')
