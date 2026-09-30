#!/usr/bin/env python3
"""Apply V34's frozen review triggers to existing JMH results; never time code."""
import argparse
import importlib.util
import json
import math
from pathlib import Path

SPEC = importlib.util.spec_from_file_location("inputs", Path(__file__).with_name("verify-v34-benchmark-inputs.py"))
INPUTS = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(INPUTS)
CORE = "io.github.huynhngochuyhoang.httpstarter.core."
HELPERS = {CORE + "StarterInvocationInternalsBenchmark." + name for name in (
    "cachedMethodMetadataLookup", "cachedRequestPlanLookup", "argumentResolutionPathQueryHeaderFromMetadata",
    "argumentResolutionPathQueryHeaderFromPlan")}
HELPERS |= {CORE + "V33PlanningCostBenchmark." + name for name in (
    "metadataColdConcretePlan", "metadataColdParsingAndPlan")}
FLAGS = ["-Xms512m", "-Xmx512m", "-XX:ActiveProcessorCount=2", "-Dorg.slf4j.simpleLogger.defaultLogLevel=warn"]


def identity(row):
    return row["benchmark"], tuple(sorted(row.get("params", {}).items()))


def validate(rows, complete=True):
    INPUTS.require(rows, "Empty scored results")
    indexed = {}
    for row in rows:
        key = identity(row)
        INPUTS.require(key not in indexed, "Duplicate benchmark/parameter row")
        indexed[key] = row
        INPUTS.require(row["benchmark"] in HELPERS or any(
            row["benchmark"] == name and row.get("params", {}) == dict(profile=profile, **({"scenario": scenario} if scenario else {}))
            for name, profile, scenario in INPUTS.expected_rows()), "Unexpected workload parameters")
        INPUTS.require(row["benchmark"] not in HELPERS or not row.get("params"), "Unexpected helper parameters")
        INPUTS.require(row["mode"] == "avgt" and row["threads"] == 1 and row["forks"] == 2
            and row["warmupIterations"] == 5 and row["measurementIterations"] == 5
            and row["warmupTime"] == "1 s" and row["measurementTime"] == "1 s", "Wrong scored settings")
        INPUTS.require(row.get("jvmArgs") == FLAGS, "Wrong frozen JVM flags")
        for metric, unit in ((row["primaryMetric"], "ns/op"),
                             (row.get("secondaryMetrics", {}).get("gc.alloc.rate.norm", {}), "B/op")):
            INPUTS.require(metric.get("scoreUnit") == unit and isinstance(metric.get("score"), (int, float))
                and math.isfinite(metric["score"]) and metric["score"] >= 0, "Missing timing/allocation")
            samples = metric.get("rawData", [])
            INPUTS.require(len(samples) == 2 and all(len(fork) == 5 for fork in samples), "Incomplete samples")
            INPUTS.require(all(math.isfinite(value) and value >= 0 for fork in samples for value in fork), "Invalid samples")
            INPUTS.require(isinstance(metric.get("scoreError"), (int, float))
                and math.isfinite(metric["scoreError"]), "Missing interval")
    if complete:
        INPUTS.verify_results([row for row in rows if row["benchmark"] not in HELPERS], False)
        INPUTS.require({row["benchmark"] for row in rows if row["benchmark"] in HELPERS} == HELPERS,
                       "Missing helper workload")
        INPUTS.require(len(rows) == 60, "Expected 60 primary rows")
    return indexed


def review(baseline, current, complete=True):
    before, after = validate(baseline, complete), validate(current, complete)
    INPUTS.require(before.keys() == after.keys(), "Unmatched rows")
    results = []
    for key, old in before.items():
        new = after[key]
        for field in ("jdkVersion", "jmhVersion", "vmName", "vmVersion"):
            INPUTS.require(old.get(field) and old[field] == new.get(field), "Unmatched toolchain")
        old_time, new_time = old["primaryMetric"]["score"], new["primaryMetric"]["score"]
        old_bytes, new_bytes = (row["secondaryMetrics"]["gc.alloc.rate.norm"]["score"] for row in (old, new))
        results.append(dict(benchmark=key[0], params=dict(key[1]), baselineNs=old_time, currentNs=new_time,
            baselineNsError=old["primaryMetric"]["scoreError"], currentNsError=new["primaryMetric"]["scoreError"],
            baselineBytes=old_bytes, currentBytes=new_bytes,
            baselineBytesError=old["secondaryMetrics"]["gc.alloc.rate.norm"]["scoreError"],
            currentBytesError=new["secondaryMetrics"]["gc.alloc.rate.norm"]["scoreError"],
            latencyReview=new_time > old_time * 1.2, allocationReview=new_bytes - old_bytes > max(32, old_bytes * .05)))
    return sorted(results, key=identity)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", nargs="+", type=Path, required=True)
    parser.add_argument("--current", nargs="+", type=Path, required=True)
    parser.add_argument("--confirmation", action="store_true", help="Require matched subset rather than all 60 primary rows")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    rows = review([row for path in args.baseline for row in json.loads(path.read_text())],
                  [row for path in args.current for row in json.loads(path.read_text())], not args.confirmation)
    args.output.mkdir(exist_ok=False)
    (args.output / "review.json").write_text(json.dumps(rows, indent=2) + "\n")
    flagged = [row for row in rows if row["latencyReview"] or row["allocationReview"]]
    (args.output / "flagged.json").write_text(json.dumps(flagged, indent=2) + "\n")
    print(json.dumps(dict(matchedRows=len(rows), flaggedRows=len(flagged),
        latencyReviewRows=sum(row["latencyReview"] for row in rows),
        allocationReviewRows=sum(row["allocationReview"] for row in rows))))


if __name__ == "__main__":
    main()
