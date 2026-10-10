import copy
import importlib.util
from pathlib import Path
import unittest

SPEC = importlib.util.spec_from_file_location('planning', Path(__file__).with_name('investigate-v35-planning.py'))
RUN = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(RUN)


class PlanningInvestigationTest(unittest.TestCase):
    def test_complete_selection_and_both_orders(self):
        self.assertEqual(len(RUN.DISCOVERY.WARM) + len(RUN.CONTROLS) + len(RUN.FIRST), 35)
        self.assertEqual(len(RUN.CONTROLS), 7)
        self.assertEqual(RUN.stages(), [(order, side, kind)
            for order, sides in [('forward', ('baseline', 'candidate')), ('reverse', ('candidate', 'baseline'))]
            for side in sides for kind in ('warm', 'controls', 'first')])
        self.assertEqual(35 * 2 * 2 * 2, 280)

    def test_frozen_flags_and_phase_separation(self):
        for kind in ('warm', 'controls', 'first'):
            args = RUN.command(Path('/fixture.jar'), Path('/output'), kind)
            self.assertEqual(args[args.index('-jvmArgs') + 1], ' '.join(RUN.RUN.FLAGS))
            self.assertNotIn('jfr', args)
        diagnostic = RUN.command(Path('/fixture.jar'), Path('/output'), 'warm', 'MINIMAL')
        self.assertIn('jfr', diagnostic)
        self.assertIn('scenario=GET', diagnostic)
        self.assertEqual(diagnostic[diagnostic.index('-f') + 1], '1')

    @staticmethod
    def rows(kind):
        selected = RUN.DISCOVERY.WARM if kind == 'warm' else RUN.FIRST if kind == 'first' else {(name, ()) for name in RUN.CONTROLS}
        return [{'benchmark': name, 'params': dict(params), 'mode': 'avgt', 'threads': 1, 'forks': 2,
                 'warmupIterations': 5, 'measurementIterations': 5, 'warmupTime': '1 s', 'measurementTime': '1 s',
                 'jvmArgs': RUN.RUN.FLAGS, 'jdkVersion': '21.0.8', 'jmhVersion': '1.37',
                 'vmName': 'fixture', 'vmVersion': '21.0.8',
                 'primaryMetric': {'score': 100, 'scoreError': 1, 'scoreUnit': 'ns/op', 'rawData': [[100] * 5] * 2},
                 'secondaryMetrics': {'gc.alloc.rate.norm': {'score': 832, 'scoreError': 1, 'scoreUnit': 'B/op',
                                                            'rawData': [[832] * 5] * 2}}}
                for name, params in selected]

    def test_exact_rows_settings_and_samples(self):
        for kind in ('warm', 'controls', 'first'):
            rows = self.rows(kind)
            self.assertEqual(len(RUN.validate_rows(rows, kind)), len(rows))
            for mutation in ('missing', 'duplicate', 'samples', 'flags'):
                bad = copy.deepcopy(rows)
                if mutation == 'missing': bad.pop()
                elif mutation == 'duplicate': bad.append(bad[0])
                elif mutation == 'samples': bad[0]['primaryMetric']['rawData'][0].pop()
                else: bad[0]['jvmArgs'] = []
                with self.assertRaises(ValueError): RUN.validate_rows(bad, kind)

    def test_review_triggers_remain_unchanged(self):
        baseline = self.rows('controls')
        current = copy.deepcopy(baseline)
        current[0]['primaryMetric']['score'] = 121
        current[0]['secondaryMetrics']['gc.alloc.rate.norm']['score'] = 900
        review = RUN.REVIEW.review(baseline, current, complete=False)
        self.assertEqual(sum(row['latencyReview'] for row in review), 1)
        self.assertEqual(sum(row['allocationReview'] for row in review), 1)


if __name__ == '__main__':
    unittest.main()
