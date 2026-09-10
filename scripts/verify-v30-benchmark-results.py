#!/usr/bin/env python3
"""Require exact V30 benchmark/mode/parameter coverage before reviewing measurements."""

import copy
import json
import math
from pathlib import Path
import sys
import unittest

CORE = "io.github.huynhngochuyhoang.httpstarter.core."
INVOCATION = "io.github.huynhngochuyhoang.httpstarter.benchmarks.StarterInvocationBenchmark."
BASELINE = {
    INVOCATION + "cacheDisabledProxyInvocationCreatesPublisher",
    INVOCATION + "cacheDisabledProxyInvocationSubscription",
    *[CORE + "V29WeightedCachePerformanceBenchmark.cacheV29NoNetwork" + suffix for suffix in (
        "UnweightedPublisherCreation", "UnweightedSubscription",
        "WeightedMetricsDisabledPublisherCreation", "WeightedMetricsDisabledSubscription",
        "WeightedHit", "MeteredAccountingPublication")],
}
NEW = {CORE + "V30CacheWorkPerformanceBenchmark.cacheV30" + suffix for suffix in (
    "NoNetworkPublisherCreation", "NoNetworkHit", "NoNetworkMiss", "NoNetworkWeightedPublication",
    "NoNetworkCallerRejection", "NoNetworkLoadRejection", "NoNetworkSingleFlightJoin",
    "LoopbackSingleFlightJoin", "NoNetworkReleaseAndReuse", "NoNetworkRefreshStart",
    "NoNetworkRefreshCapacitySkip")}
CONTENTION = CORE + "V30CacheWorkAdmissionBenchmark.cacheV30NoNetworkContendedReservationRejection"


def expected(current):
    rows = {(name, mode, ()) for name in BASELINE for mode in ("avgt", "thrpt")}
    if current:
        rows |= {(name, mode, (("metered", value),))
                 for name in NEW for mode in ("avgt", "thrpt") for value in ("false", "true")}
        rows |= {(CONTENTION, mode, ()) for mode in ("avgt", "thrpt")}
    return rows


def validate(rows, current, smoke=False):
    assert isinstance(rows, list) and rows, "missing JMH array"
    seen = set()
    for row in rows:
        identity = (row["benchmark"], row["mode"], tuple(sorted(row.get("params", {}).items())))
        assert identity not in seen, f"duplicate {identity}"
        seen.add(identity)
        assert row["threads"] == (4 if row["benchmark"] == CONTENTION else 1), identity
        metric = row["primaryMetric"]
        assert metric["scoreUnit"] == ("ops/us" if row["mode"] == "thrpt" else "us/op"), identity
        assert isinstance(metric["score"], (float, int)) and math.isfinite(metric["score"]) \
            and metric["score"] > 0, identity
        if not smoke:
            assert row["forks"] == 2 and row["warmupIterations"] == row["measurementIterations"] == 5, identity
            allocation = row["secondaryMetrics"]["gc.alloc.rate.norm"]
            assert allocation["scoreUnit"] == "B/op"
            assert math.isfinite(allocation["score"]) and allocation["score"] >= 0, identity
    required = expected(current)
    assert seen == required, f"missing={required - seen}; unexpected={seen - required}"
    return {"rows": len(seen), "methods": len({name for name, _, _ in seen}),
            "matchedBaselineRows": 16, "currentOnlyRows": 46 if current else 0,
            "measurement": "smoke-only" if smoke else "release-quality-pending-review"}


class ResultTests(unittest.TestCase):
    def setUp(self):
        self.rows = [{"benchmark": name, "mode": mode, "params": dict(params),
                      "threads": 4 if name == CONTENTION else 1, "forks": 2,
                      "warmupIterations": 5, "measurementIterations": 5,
                      "primaryMetric": {"score": 1.0, "scoreUnit": "ops/us" if mode == "thrpt" else "us/op"},
                      "secondaryMetrics": {"gc.alloc.rate.norm": {"score": 0, "scoreUnit": "B/op"}}}
                     for name, mode, params in sorted(expected(True))]

    def test_exact_current_and_baseline(self):
        self.assertEqual(validate(self.rows, True)["rows"], 62)
        self.assertEqual(validate([r for r in self.rows if r["benchmark"] in BASELINE], False)["rows"], 16)

    def test_missing_mode_parameter_and_duplicate(self):
        for rows in (self.rows[:-1], self.rows + [self.rows[0]],
                     [r for r in self.rows if r["params"].get("metered") != "false"],
                     [r for r in self.rows if r["mode"] != "thrpt"]):
            with self.assertRaises(AssertionError):
                validate(rows, True)

    def test_quality_units_threads_and_allocation(self):
        for field, value in (("threads", 99), ("forks", 1), ("warmupIterations", 0),
                             ("measurementIterations", 1), ("secondaryMetrics", {})):
            rows = copy.deepcopy(self.rows)
            rows[0][field] = value
            with self.assertRaises((AssertionError, KeyError)):
                validate(rows, True)
        for metric in ({"score": float("nan"), "scoreUnit": "us/op"},
                       {"score": 1, "scoreUnit": "seconds/op"}):
            rows = copy.deepcopy(self.rows)
            rows[0]["primaryMetric"] = metric
            with self.assertRaises(AssertionError):
                validate(rows, True)


if __name__ == "__main__":
    if sys.argv[1:] == ["--self-test"]:
        unittest.main(argv=[sys.argv[0]], verbosity=2)
    else:
        if len(sys.argv) not in (3, 4) or sys.argv[1] not in ("current", "baseline") \
                or (len(sys.argv) == 4 and sys.argv[3] != "--smoke"):
            sys.exit("Usage: verify-v30-benchmark-results.py current|baseline report.json [--smoke]")
        print(json.dumps(validate(json.loads(Path(sys.argv[2]).read_text()),
                                  sys.argv[1] == "current", "--smoke" in sys.argv), indent=2))
