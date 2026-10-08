import copy
import importlib.util
from pathlib import Path
import unittest

SPEC = importlib.util.spec_from_file_location('discovery', Path(__file__).with_name('investigate-v35-discovery.py'))
RUN = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(RUN)


class DiscoveryInvestigationTest(unittest.TestCase):
    def test_schedule_preserves_both_orders_and_224_scored_forks(self):
        self.assertEqual(RUN.stages(), [(order, side, kind)
            for order, sides in [('forward', ('baseline', 'candidate')), ('reverse', ('candidate', 'baseline'))]
            for side in sides for kind in ('warm', 'composition')])
        self.assertEqual(sum((24 if kind == 'warm' else 4) * 2 for _, _, kind in RUN.stages()), 224)

    def test_commands_keep_diagnostic_profiler_out_of_scores(self):
        for kind in ('warm', 'composition', 'attribution'):
            args = RUN.command(Path('/fixture.jar'), Path('/output'), kind, 'HOOK' if kind == 'attribution' else None)
            self.assertEqual(args[args.index('-jvmArgs') + 1], ' '.join(RUN.RUN.FLAGS))
            self.assertEqual('jfr' in args, kind == 'attribution')
            self.assertEqual(args[args.index('-f') + 1], '1' if kind == 'attribution' else '2')
            self.assertEqual(args[args.index('-bm') + 1], 'avgt')

    @staticmethod
    def rows():
        rows = []
        for name in RUN.COMPOSITION:
            rows.append({'benchmark': name, 'params': {}, 'mode': 'avgt', 'threads': 1, 'forks': 2,
                         'warmupIterations': 5, 'measurementIterations': 5, 'warmupTime': '1 s', 'measurementTime': '1 s',
                         'jvmArgs': RUN.RUN.FLAGS, 'jdkVersion': '21.0.8', 'jmhVersion': '1.37',
                         'vmName': 'fixture', 'vmVersion': '21.0.8',
                         'primaryMetric': {'score': 100, 'scoreError': 1, 'scoreUnit': 'ns/op', 'rawData': [[100] * 5] * 2},
                         'secondaryMetrics': {'gc.alloc.rate.norm': {'score': 832, 'scoreError': 1, 'scoreUnit': 'B/op',
                                                                    'rawData': [[832] * 5] * 2}}})
        return rows

    def test_composition_requires_exact_rows_and_samples(self):
        rows = self.rows()
        self.assertEqual(len(RUN.validate_rows(rows, 'composition')), 4)
        for mutation in ('missing-row', 'duplicate', 'missing-sample', 'unexpected'):
            bad = copy.deepcopy(rows)
            if mutation == 'missing-row':
                bad.pop()
            elif mutation == 'duplicate':
                bad.append(bad[0])
            elif mutation == 'missing-sample':
                bad[0]['primaryMetric']['rawData'][0].pop()
            else:
                bad[0]['benchmark'] = 'unexpected'
            with self.assertRaises(ValueError):
                RUN.validate_rows(bad, 'composition')

    def test_existing_thresholds_still_flag_composition_regressions(self):
        baseline = self.rows()
        RUN.validate_rows(baseline, 'composition')
        current = copy.deepcopy(baseline)
        current[0]['primaryMetric']['score'] = 121
        current[0]['secondaryMetrics']['gc.alloc.rate.norm']['score'] = 900
        report = RUN.REVIEW.review(baseline, current, complete=False)
        self.assertEqual(sum(row['latencyReview'] for row in report), 1)
        self.assertEqual(sum(row['allocationReview'] for row in report), 1)


if __name__ == '__main__':
    unittest.main()
