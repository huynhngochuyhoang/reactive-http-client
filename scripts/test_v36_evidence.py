"""Synthetic integrity failures must not mutate or extract the original evidence."""
import importlib.util
import io
from pathlib import Path
import tarfile
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('evidence', Path(__file__).with_name('verify-v36-evidence.py'))
evidence = importlib.util.module_from_spec(spec)
spec.loader.exec_module(evidence)


class EvidenceTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.bundle = self.root / 'bundle'
        self.bundle.mkdir()
        (self.bundle / 'raw.json').write_text('{"synthetic": true}\n')
        self.manifest = self.bundle / 'SHA256SUMS'
        self.manifest.write_text(evidence.sha256(self.bundle / 'raw.json') + '  raw.json\n')
        self.archive = self.root / 'bundle.tar.gz'
        self.pack()
        self.manifest_hash = evidence.sha256(self.manifest)
        self.archive_hash = evidence.sha256(self.archive)

    def pack(self):
        with tarfile.open(self.archive, 'w:gz') as tar:
            tar.add(self.bundle, arcname=self.bundle.name)

    def verify(self):
        return evidence.verify(self.bundle, self.archive, self.manifest_hash, self.archive_hash)

    def test_valid_copy_and_archive_are_read_only(self):
        before = self.archive.read_bytes(), self.manifest.read_bytes()
        self.assertEqual(self.verify()['files'], 1)
        self.assertEqual(before, (self.archive.read_bytes(), self.manifest.read_bytes()))
        self.assertEqual(set(p.name for p in self.root.iterdir()), {'bundle', 'bundle.tar.gz'})

    def test_missing_bundle_manifest_archive_or_raw_file_fails(self):
        for path in (self.bundle, self.manifest, self.archive, self.bundle / 'raw.json'):
            with self.subTest(path=path):
                moved = path.with_name(path.name + '.away')
                path.rename(moved)
                try:
                    with self.assertRaises((OSError, ValueError)):
                        self.verify()
                finally:
                    moved.rename(path)

    def test_corrupt_and_unlisted_bundle_files_fail(self):
        raw = self.bundle / 'raw.json'
        original = raw.read_bytes()
        raw.write_text('corrupt')
        with self.assertRaisesRegex(ValueError, 'bundle content'):
            self.verify()
        raw.write_bytes(original)
        (self.bundle / 'unlisted').touch()
        with self.assertRaisesRegex(ValueError, 'bundle content'):
            self.verify()

    def test_manifest_cannot_be_resealed_silently(self):
        self.manifest.write_text('')
        with self.assertRaisesRegex(ValueError, 'Seal hash'):
            self.verify()

    def test_malformed_empty_duplicate_or_unsafe_manifest_fails(self):
        line = self.manifest.read_text()
        for text in ('', 'bad\n', line + line, line.replace('raw.json', '../raw.json'),
                     line.replace('raw.json', '/raw.json'), line.replace('raw.json', 'a/../raw.json'),
                     line.replace('raw.json', 'SHA256SUMS'), line.replace('raw.json', '.')):
            with self.subTest(text=text):
                self.manifest.write_text(text)
                self.manifest_hash = evidence.sha256(self.manifest)
                with self.assertRaises(ValueError):
                    self.verify()

    def test_linked_bundle_content_fails(self):
        (self.bundle / 'link').symlink_to(self.bundle / 'raw.json')
        with self.assertRaisesRegex(ValueError, 'Non-regular'):
            self.verify()

    def test_truncated_archive_fails_even_with_its_own_hash(self):
        self.archive.write_bytes(self.archive.read_bytes()[:32])
        self.archive_hash = evidence.sha256(self.archive)
        with self.assertRaises((EOFError, tarfile.TarError)):
            self.verify()

    def test_archive_missing_or_changed_file_fails(self):
        for raw in (None, b'changed'):
            with self.subTest(raw=raw), tarfile.open(self.archive, 'w:gz') as tar:
                tar.add(self.manifest, arcname='bundle/SHA256SUMS')
                if raw is not None:
                    member = tarfile.TarInfo('bundle/raw.json')
                    member.size = len(raw)
                    tar.addfile(member, io.BytesIO(raw))
            self.archive_hash = evidence.sha256(self.archive)
            with self.assertRaisesRegex(ValueError, 'archive content'):
                self.verify()

    def test_duplicate_unsafe_extra_or_linked_archive_entry_fails(self):
        for name, kind in (('bundle/raw.json', tarfile.REGTYPE), ('../escape', tarfile.REGTYPE),
                           ('wrong/raw.json', tarfile.REGTYPE), ('bundle/extra', tarfile.REGTYPE),
                           ('bundle/link', tarfile.SYMTYPE), ('bundle/hard', tarfile.LNKTYPE)):
            with self.subTest(name=name):
                with tarfile.open(self.archive, 'w:gz') as tar:
                    tar.add(self.bundle, arcname='bundle')
                    member = tarfile.TarInfo(name)
                    member.type = kind
                    member.linkname = 'raw.json' if kind != tarfile.REGTYPE else ''
                    tar.addfile(member)
                self.archive_hash = evidence.sha256(self.archive)
                with self.assertRaises(ValueError):
                    self.verify()


if __name__ == '__main__':
    unittest.main()
