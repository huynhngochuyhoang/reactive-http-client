import copy
import importlib.util
from pathlib import Path
import unittest

SPEC = importlib.util.spec_from_file_location('optional', Path(__file__).with_name('investigate-v35-optional.py'))
P = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(P)


class OptionalInvestigationTest(unittest.TestCase):
    def test_independent_candidates_and_orders(self):
        self.assertEqual(sum(map(len, P.SELECTIONS.values())), 34)
        self.assertEqual(len(P.stages()), 18)
        self.assertEqual(34 * 3 * 2 * 2, 408)
        self.assertEqual([side for order, side, kind in P.stages() if kind == 'warm'],
                         ['baseline', 'preparation', 'reuse', 'reuse', 'preparation', 'baseline'])

    def test_commands_keep_frozen_parameters(self):
        for kind in P.SELECTIONS:
            args = P.command(Path('/fixture.jar'), Path('/output'), kind)
            self.assertEqual(args[args.index('-jvmArgs') + 1], ' '.join(P.RUN.FLAGS))
            self.assertEqual(args[args.index('-f') + 1], '2')
            self.assertNotIn('jfr', args)
        self.assertIn('metered=false,true', P.command(Path('/a.jar'), Path('/out'), 'cache'))

    @staticmethod
    def rows(kind):
        return [{'benchmark': name, 'params': dict(params), 'mode': 'avgt', 'threads': 1, 'forks': 2,
                 'warmupIterations': 5, 'measurementIterations': 5, 'warmupTime': '1 s', 'measurementTime': '1 s',
                 'jvmArgs': P.RUN.FLAGS, 'jdkVersion': '21.0.8', 'jmhVersion': '1.37',
                 'vmName': 'fixture', 'vmVersion': '21.0.8',
                 'primaryMetric': {'score': 100, 'scoreError': 1, 'scoreUnit': 'ns/op', 'rawData': [[100] * 5] * 2},
                 'secondaryMetrics': {'gc.alloc.rate.norm': {'score': 832, 'scoreError': 1, 'scoreUnit': 'B/op',
                                                            'rawData': [[832] * 5] * 2}}}
                for name, params in P.SELECTIONS[kind]]

    def test_rows_and_all_raw_samples_are_required(self):
        for kind in P.SELECTIONS:
            rows = self.rows(kind)
            self.assertEqual(len(P.validate_rows(rows, kind)), len(rows))
            for mutation in ('missing', 'duplicate', 'samples', 'flags', 'params'):
                bad = copy.deepcopy(rows)
                if mutation == 'missing': bad.pop()
                elif mutation == 'duplicate': bad.append(bad[0])
                elif mutation == 'samples': bad[0]['primaryMetric']['rawData'][0].pop()
                elif mutation == 'flags': bad[0]['jvmArgs'] = []
                else: bad[0]['params'] = {}
                with self.assertRaises(ValueError): P.validate_rows(bad, kind)

    def test_cache_dimensions_and_review_thresholds_survive_adaptation(self):
        before = self.rows('cache')
        after = copy.deepcopy(before)
        after[0]['primaryMetric']['score'] = 121
        after[0]['secondaryMetrics']['gc.alloc.rate.norm']['score'] = 900
        rows = P.compare(P.validate_rows(before, 'cache'), P.validate_rows(after, 'cache'))
        self.assertEqual({P.REVIEW.identity(row) for row in rows}, P.CACHE)
        self.assertEqual(sum(row['latencyReview'] for row in rows), 1)
        self.assertEqual(sum(row['allocationReview'] for row in rows), 1)


if __name__ == '__main__':
    unittest.main()
