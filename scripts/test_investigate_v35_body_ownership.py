import copy
import importlib.util
from pathlib import Path
import unittest

SPEC = importlib.util.spec_from_file_location('body', Path(__file__).with_name('investigate-v35-body-ownership.py'))
RUN = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(RUN)


class BodyOwnershipInvestigationTest(unittest.TestCase):
    def test_schedule_keeps_all_384_forks_and_both_orders(self):
        self.assertEqual(RUN.stages(), [(order, side, kind)
            for order, sides in [('forward', ('baseline', 'candidate')), ('reverse', ('candidate', 'baseline'))]
            for side in sides for kind in ('warm', 'loopback')])
        self.assertEqual(len(RUN.DISCOVERY.WARM), 24)
        self.assertEqual(len(RUN.LOOPBACK), 24)
        self.assertEqual(len(RUN.stages()) * 24 * 2, 384)

    def test_non_null_controls_and_frozen_flags_are_not_dropped(self):
        args = RUN.command(Path('/fixture.jar'), Path('/output'), 'loopback')
        self.assertIn('scenario=GET,TARGET,STRING,JSON,ENTITY,EMPTY,ERROR4,ERROR5', args)
        self.assertEqual(args[args.index('-jvmArgs') + 1], ' '.join(RUN.RUN.FLAGS))
        self.assertNotIn('jfr', args)
        diagnostic = RUN.command(Path('/fixture.jar'), Path('/output'), 'warm', ('AUTO_REGISTRY', 'Subscription'))
        self.assertIn('jfr', diagnostic)
        self.assertEqual(diagnostic[diagnostic.index('-f') + 1], '1')

    @staticmethod
    def rows(kind):
        return [{'benchmark': name, 'params': dict(params), 'mode': 'avgt', 'threads': 1, 'forks': 2,
                 'warmupIterations': 5, 'measurementIterations': 5, 'warmupTime': '1 s', 'measurementTime': '1 s',
                 'jvmArgs': RUN.RUN.FLAGS, 'jdkVersion': '21.0.8', 'jmhVersion': '1.37',
                 'vmName': 'fixture', 'vmVersion': '21.0.8',
                 'primaryMetric': {'score': 100, 'scoreError': 1, 'scoreUnit': 'ns/op', 'rawData': [[100] * 5] * 2},
                 'secondaryMetrics': {'gc.alloc.rate.norm': {'score': 832, 'scoreError': 1, 'scoreUnit': 'B/op',
                                                            'rawData': [[832] * 5] * 2}}}
                for name, params in (RUN.DISCOVERY.WARM if kind == 'warm' else RUN.LOOPBACK)]

    def test_requires_exact_rows_settings_and_samples(self):
        for kind in ('warm', 'loopback'):
            rows = self.rows(kind)
            self.assertEqual(len(RUN.validate_rows(rows, kind)), 24)
            for mutation in ('missing', 'duplicate', 'samples', 'flags'):
                bad = copy.deepcopy(rows)
                if mutation == 'missing': bad.pop()
                elif mutation == 'duplicate': bad.append(bad[0])
                elif mutation == 'samples': bad[0]['primaryMetric']['rawData'][0].pop()
                else: bad[0]['jvmArgs'] = []
                with self.assertRaises(ValueError): RUN.validate_rows(bad, kind)

    def test_review_triggers_remain_unchanged(self):
        baseline = self.rows('loopback')
        current = copy.deepcopy(baseline)
        current[0]['primaryMetric']['score'] = 121
        current[0]['secondaryMetrics']['gc.alloc.rate.norm']['score'] = 900
        review = RUN.REVIEW.review(baseline, current, complete=False)
        self.assertEqual(sum(row['latencyReview'] for row in review), 1)
        self.assertEqual(sum(row['allocationReview'] for row in review), 1)


if __name__ == '__main__':
    unittest.main()
