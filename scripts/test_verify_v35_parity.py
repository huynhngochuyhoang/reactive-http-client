"""Safety and evidence bookkeeping, not JVM/native verification."""
import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('parity', Path(__file__).with_name('verify-v35-parity.py'))
parity = importlib.util.module_from_spec(spec)
spec.loader.exec_module(parity)


class ParityRunnerTest(unittest.TestCase):
    def test_rejects_original_checkout_ancestor_descendant_and_target(self):
        root = Path('/tmp/example-repo')
        for path in (root, root.parent, root / 'scratch', Path('/tmp/other/target/evidence')):
            with self.subTest(path=path), self.assertRaises(ValueError):
                parity.safe_work(path, root)
        self.assertEqual(parity.safe_work(Path('/tmp/separate-run'), root), Path('/tmp/separate-run'))

    def test_symlink_cannot_hide_original_checkout(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / 'repo'
            root.mkdir()
            link = Path(directory) / 'alias'
            link.symlink_to(root)
            with self.assertRaises(ValueError):
                parity.safe_work(link / 'evidence', root)

    def test_rejects_overlapping_or_cleanable_evidence_and_repository(self):
        source = Path('/tmp/run/source')
        evidence = Path('/tmp/run/evidence')
        repository = Path('/tmp/run/repository')
        parity.safe_layout(source, evidence, repository)
        for paths in ((source, source / 'target/evidence', repository),
                      (source, evidence, source / 'repository'),
                      (source, evidence, evidence), (source, source.parent, repository)):
            with self.subTest(paths=paths), self.assertRaises(ValueError):
                parity.safe_layout(*paths)

    def test_partial_and_empty_reports_cannot_pass(self):
        for total in ({}, {'starter': {'tests': 0, 'failures': 0, 'errors': 0, 'skipped': 0}},
                      {'starter': {'tests': 1, 'failures': 1, 'errors': 0, 'skipped': 0}},
                      {'starter': {'tests': 1, 'failures': 0, 'errors': 0, 'skipped': 1}}):
            with self.subTest(total=total), self.assertRaises(RuntimeError):
                parity.require_pass(total)

    def test_collects_only_fresh_reports_and_preserves_failures(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            reports = root / 'source/starter/target/surefire-reports'
            reports.mkdir(parents=True)
            (reports / 'TEST-fresh.xml').write_text('<testsuite name="fresh" tests="2" failures="1" errors="0" skipped="0"/>')
            totals = parity.collect_reports(root / 'source', root / 'evidence', 0, ['starter'])
            self.assertEqual(totals['starter']['failures'], 1)
            self.assertTrue((root / 'evidence/starter/surefire-reports/TEST-fresh.xml').exists())
            totals = parity.collect_reports(root / 'source', root / 'later', float('inf'), ['starter'])
            self.assertEqual(totals['starter']['tests'], 0)


if __name__ == '__main__':
    unittest.main()
