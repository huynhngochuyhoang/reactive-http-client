import copy
import importlib.util
from pathlib import Path
import unittest

SPEC = importlib.util.spec_from_file_location("review", Path(__file__).with_name("review-v34-benchmark-results.py"))
REVIEW = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(REVIEW)


class BenchmarkReviewTest(unittest.TestCase):
    def rows(self):
        identities = [(name, {"profile": profile, **({"scenario": scenario} if scenario else {})})
                      for name, profile, scenario in REVIEW.INPUTS.expected_rows()]
        identities += [(name, {}) for name in REVIEW.HELPERS]
        return [dict(benchmark=name, params=params, mode="avgt", threads=1, forks=2,
                     warmupIterations=5, measurementIterations=5, warmupTime="1 s", measurementTime="1 s",
                     jvmArgs=REVIEW.FLAGS.copy(), jdkVersion="21.0.8", jmhVersion="1.37", vmName="VM", vmVersion="21",
                     primaryMetric=dict(score=100, scoreError=1, scoreUnit="ns/op", rawData=[[100] * 5] * 2),
                     secondaryMetrics={"gc.alloc.rate.norm": dict(score=100, scoreError=1, scoreUnit="B/op", rawData=[[100] * 5] * 2)})
                for name, params in identities]

    def test_all_sixty_rows_are_required(self):
        rows = self.rows()
        self.assertEqual(len(REVIEW.review(rows, rows)), 60)
        with self.assertRaisesRegex(ValueError, "Missing helper"):
            REVIEW.review(rows[:-1], rows[:-1])

    def test_exact_frozen_boundaries_are_not_flags(self):
        old = self.rows()
        new = copy.deepcopy(old)
        for row in new:
            row["primaryMetric"]["score"] = 120
            row["secondaryMetrics"]["gc.alloc.rate.norm"]["score"] = 132
        self.assertTrue(all(not row["latencyReview"] and not row["allocationReview"] for row in REVIEW.review(old, new)))

    def test_crossing_either_threshold_is_a_review(self):
        old = self.rows()
        new = copy.deepcopy(old)
        for row in new:
            row["primaryMetric"]["score"] = 121
            row["secondaryMetrics"]["gc.alloc.rate.norm"]["score"] = 133
        self.assertTrue(all(row["latencyReview"] and row["allocationReview"] for row in REVIEW.review(old, new)))

    def test_percentage_bound_wins_for_large_allocations(self):
        old = self.rows()
        new = copy.deepcopy(old)
        for before, after in zip(old, new):
            before["secondaryMetrics"]["gc.alloc.rate.norm"]["score"] = 1000
            after["secondaryMetrics"]["gc.alloc.rate.norm"]["score"] = 1050
        self.assertTrue(all(not row["allocationReview"] for row in REVIEW.review(old, new)))

    def test_rejects_missing_allocations_bad_flags_and_smoke(self):
        for field, value in (("secondaryMetrics", {}), ("jvmArgs", []), ("forks", 1)):
            rows = self.rows()
            rows[0][field] = value
            with self.assertRaises(ValueError):
                REVIEW.review(rows, rows)

    def test_confirmation_keeps_exact_parameters_and_rejects_mismatches(self):
        rows = self.rows()
        self.assertEqual(len(REVIEW.review(rows[:1], rows[:1], False)), 1)
        with self.assertRaisesRegex(ValueError, "Empty scored results"):
            REVIEW.review([], [], False)
        with self.assertRaisesRegex(ValueError, "Unmatched rows"):
            REVIEW.review(rows[:1], rows[1:2], False)
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            REVIEW.review(rows[:1] * 2, rows[:1] * 2, False)


if __name__ == "__main__":
    unittest.main()
