#!/usr/bin/env python3
"""Verify a sealed local bundle and its archive without extracting or changing either."""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import tarfile


def sha256(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def relative_name(name):
    path = PurePosixPath(name)
    if not path.parts or path.is_absolute() or '..' in path.parts or str(path) != name:
        raise ValueError(f'Unsafe or noncanonical evidence path: {name!r}')
    return name


def verify(bundle, archive, manifest_sha256, archive_sha256):
    bundle, archive = Path(bundle), Path(archive)
    manifest = bundle / 'SHA256SUMS'
    if bundle.is_symlink() or not bundle.is_dir():
        raise ValueError('Bundle must be a real directory')
    if sha256(manifest) != manifest_sha256 or sha256(archive) != archive_sha256:
        raise ValueError('Seal hash mismatch')
    entries = {}
    for line in manifest.read_text().splitlines():
        match = re.fullmatch(r'([0-9a-f]{64})  (.+)', line)
        if not match:
            raise ValueError('Malformed manifest')
        digest, name = match.groups()
        relative_name(name)
        if name in entries or name == 'SHA256SUMS':
            raise ValueError('Duplicate or self-referencing manifest entry')
        entries[name] = digest
    if not entries:
        raise ValueError('Empty manifest')
    expected = entries | {'SHA256SUMS': manifest_sha256}
    actual = {}
    for path in bundle.rglob('*'):
        if path.is_symlink() or not (path.is_file() or path.is_dir()):
            raise ValueError('Non-regular bundle entry')
        if path.is_file():
            actual[path.relative_to(bundle).as_posix()] = sha256(path)
    if actual != expected:
        raise ValueError('Missing, extra or corrupt bundle content')
    archived, seen = {}, set()
    with tarfile.open(archive, 'r:*') as tar:
        for member in tar:
            name = relative_name(member.name)
            if name in seen:
                raise ValueError('Duplicate archive entry')
            seen.add(name)
            path = PurePosixPath(name)
            if path.parts[0] != bundle.name or not (member.isfile() or member.isdir()):
                raise ValueError('Unexpected archive root or entry type')
            if member.isfile():
                relative = path.relative_to(bundle.name).as_posix()
                if relative not in expected:
                    raise ValueError('Unexpected archive file')
                with tar.extractfile(member) as stream:
                    archived[relative] = hashlib.file_digest(stream, 'sha256').hexdigest()
    if archived != expected:
        raise ValueError('Missing or corrupt archive content')
    return dict(files=len(entries), manifestSha256=manifest_sha256, archiveSha256=archive_sha256)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('bundle', type=Path)
    parser.add_argument('archive', type=Path)
    parser.add_argument('--manifest-sha256', required=True)
    parser.add_argument('--archive-sha256', required=True)
    args = parser.parse_args()
    try:
        result = verify(args.bundle, args.archive, args.manifest_sha256, args.archive_sha256)
    except (OSError, ValueError, tarfile.TarError, EOFError) as error:
        parser.exit(1, f'Evidence rejected: {error}\n')
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
