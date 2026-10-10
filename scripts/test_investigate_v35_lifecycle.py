import importlib.util
from pathlib import Path
import unittest

SPEC = importlib.util.spec_from_file_location('lifecycle', Path(__file__).with_name('investigate-v35-lifecycle.py'))
L = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(L)


class LifecycleInvestigationTest(unittest.TestCase):
    def test_eight_diagnostics_are_distinct_from_reused_scores(self):
        self.assertEqual(len(set(L.stages())), 8)
        for phase, profile in L.stages():
            args = L.command(Path('/fixture.jar'), Path('/output'), phase, profile)
            self.assertEqual(args[args.index('-f') + 1], '1')
            self.assertIn('jfr', args)
            self.assertEqual('scenario=GET' in args, phase == 'WarmSubscription')
            self.assertIn('profile=' + profile, args)
            self.assertEqual(args[args.index('-jvmArgs') + 1],
                             ' '.join(L.RUN.FLAGS + ['-XX:FlightRecorderOptions=stackdepth=128']))

    def test_attribution_keeps_framework_and_fixture_separate_from_starter(self):
        prefix = 'io/github/huynhngochuyhoang/httpstarter/'
        def event(frames):
            return dict(type='jdk.ObjectAllocationSample', values=dict(objectClass={'name': 'java/lang/Object'},
                weight=128, stackTrace={'frames': [dict(method=dict(type={'name': name}, name='call')) for name in frames]}))
        rows = L.summarize([event(['org/springframework/Context', prefix + 'core/MethodMetadataCache']),
                            event([prefix + 'benchmarks/V34WorkloadFixture']), event([])])
        self.assertEqual(sum(r['samples'] for r in rows), 3)
        self.assertEqual(sum(r['sampledWeight'] for r in rows), 384)
        self.assertEqual(sum(r['samples'] for r in rows if r['nearestStarterFrame']), 1)
        self.assertTrue(any(r['frames'] and r['nearestStarterFrame'] is None for r in rows))
        with self.assertRaises(ValueError): L.classify(dict(type='jdk.SystemProperty'))


if __name__ == '__main__':
    unittest.main()
