"""Evidence input/schedule checks; no performance assertions or scored execution."""
import importlib.util
from pathlib import Path
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location('acceptance', Path(__file__).with_name('verify-v35-acceptance.py'))
acceptance = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(acceptance)


class AcceptanceRunnerTest(unittest.TestCase):
    def test_approved_followup_is_bounded_to_three_profiles_and_thirty_six_forks(self):
        followup = acceptance.load('investigate-v35-final-profiles')
        stages = followup.schedule()
        self.assertEqual(len(stages), 12)
        self.assertEqual(sum(row[3] for row in stages), 36)
        self.assertEqual({row[2] for row in stages}, {'AUTO_NO_REGISTRY', 'AUTO_REGISTRY', 'HOOK'})
        self.assertEqual([row[1] for row in stages], ['baseline', 'current'] * 6)
        self.assertEqual([row[3] for row in stages], [4, 4, 2, 2] * 3)

    def test_complete_selection_still_has_sixty_rows(self):
        self.assertEqual(len(acceptance.inputs.expected_rows()) + len(acceptance.reviewer.HELPERS), 60)

    def test_unflagged_primary_still_confirms_historical_row(self):
        rows = acceptance.confirmation_selection([])
        self.assertEqual(len(rows), 1)
        self.assertEqual(rows[0]['params'], {'profile': 'RESILIENCE_ENABLED_ONLY', 'scenario': 'GET'})

    def test_confirmation_unions_both_triggers_without_duplicates(self):
        historical = acceptance.confirmation_selection([])[0]
        rows = [dict(historical, latencyReview=True, allocationReview=True),
                {'benchmark': 'helper', 'params': {}, 'latencyReview': True, 'allocationReview': False},
                {'benchmark': 'other', 'params': {}, 'latencyReview': False, 'allocationReview': True},
                {'benchmark': 'ignored', 'params': {}, 'latencyReview': False, 'allocationReview': False}]
        selected = acceptance.confirmation_selection(rows)
        self.assertEqual(len(selected), 3)
        self.assertNotIn('ignored', [row['benchmark'] for row in selected])

    def test_third_party_seed_never_copies_project_coordinates(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            seed = root / 'seed'
            project = seed / acceptance.GROUP / 'reactive-http-client-starter/4.4.2'
            third_party = seed / 'io/github/example/tool/1.0'
            for directory in (project, third_party):
                directory.mkdir(parents=True)
                (directory / 'artifact.jar').write_bytes(b'fixture')
            target = root / 'repository'
            acceptance.seed_third_party(seed, target)
            self.assertFalse((target / acceptance.GROUP).exists())
            self.assertEqual((target / third_party.relative_to(seed) / 'artifact.jar').read_bytes(), b'fixture')
            self.assertTrue((project / 'artifact.jar').exists())
            with self.assertRaises(FileExistsError):
                acceptance.seed_third_party(seed, target)

    def test_retains_compared_artifact_before_later_build_replaces_it(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            module = root / 'source/starter'
            (module / 'target').mkdir(parents=True)
            (module / 'pom.xml').write_text('<project/>')
            artifact = module / 'target/starter.jar'
            artifact.write_bytes(b'compared bytes')
            acceptance.retain_artifacts(root / 'source', root / 'evidence', ['starter'])
            artifact.write_bytes(b'later Boot packaging')
            self.assertEqual((root / 'evidence/starter/starter.jar').read_bytes(), b'compared bytes')
            self.assertTrue((root / 'evidence/starter/pom.xml').exists())


if __name__ == '__main__':
    unittest.main()
