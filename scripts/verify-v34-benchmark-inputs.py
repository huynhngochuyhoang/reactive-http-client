#!/usr/bin/env python3
"""Freeze/compare the inputs of the existing JMH harness, not performance scores."""
import argparse
import hashlib
import json
import math
from pathlib import Path
import os
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
MODULE = ROOT / "reactive-http-client-benchmarks"
GROUP = "io/github/huynhngochuyhoang"


def require(condition, message):
    if not condition:
        raise ValueError(message)


def digest(path):
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


def source_hashes():
    paths = [ROOT / "pom.xml", MODULE / "pom.xml"]
    paths.extend(path for path in (MODULE / "src").rglob("*") if path.is_file())
    return {str(path.relative_to(ROOT)): digest(path) for path in sorted(paths)}


def classpath_inputs(classpath, version, published):
    dependencies = {}
    starter = None
    for value in classpath.strip().split(os.pathsep):
        path = Path(value).resolve()
        require(path.is_file() and path.suffix == ".jar", "Only resolved dependency JARs are allowed; no reactor classes")
        require(path.name not in dependencies, "Duplicate classpath artifact")
        dependencies[path.name] = digest(path)
        if f"/{GROUP}/" not in str(path):
            continue
        require(path.name == f"reactive-http-client-starter-{version}.jar", "Unexpected project artifact/version")
        require(starter is None, "Multiple starter JARs")
        with zipfile.ZipFile(path) as jar:
            metadata = jar.read("META-INF/maven/io.github.huynhngochuyhoang/reactive-http-client-starter/pom.properties").decode()
            require(f"version={version}" in metadata.splitlines(), "Wrong embedded starter version")
        if published:
            markers = path.with_name("_remote.repositories").read_text().splitlines()
            require(f"{path.name}>maven-central=" in markers, "Baseline JAR is not Central-resolved")
            require(f"{path.stem}.pom>maven-central=" in markers, "Baseline POM is not Central-resolved")
        starter = {"path": str(path), "sha256": digest(path), "version": version}
    require(starter is not None, "Missing starter JAR")
    del dependencies[Path(starter["path"]).name]
    return starter, dependencies


def verify_shaded(shaded, starter):
    with zipfile.ZipFile(shaded) as bundle, zipfile.ZipFile(starter["path"]) as artifact:
        names = [name for name in artifact.namelist() if name.endswith(".class")]
        require(names, "Starter has no classes")
        require(all(bundle.read(name) == artifact.read(name) for name in names), "Shaded starter differs from resolved artifact")
        return len(names)


def compare(baseline, current):
    require(baseline["starter"]["version"] == "4.4.2" and baseline["published"], "Expected published 4.4.2")
    require(current["starter"]["version"] == "4.5.0-SNAPSHOT" and not current["published"], "Expected current development artifact")
    require(baseline["sourceFiles"] == current["sourceFiles"], "Harness/POM sources differ")
    require(baseline["dependencies"] == current["dependencies"], "Non-starter dependencies differ")
    require(baseline["sourceCommit"] == current["sourceCommit"], "Harness source commits differ")
    return {"matched": True, "sourceFiles": len(current["sourceFiles"]),
            "nonStarterJars": len(current["dependencies"]),
            "baselineStarterSha256": baseline["starter"]["sha256"],
            "currentStarterSha256": current["starter"]["sha256"],
            "scoredPerformanceEvidence": False}


def expected_rows():
    package = "io.github.huynhngochuyhoang.httpstarter.benchmarks."
    rows = set()
    for method in ("WarmPublisher", "WarmSubscription"):
        for profile in ("MINIMAL", "AUTO_NO_REGISTRY", "AUTO_REGISTRY", "RESILIENCE_ENABLED_ONLY", "OBSERVER", "HOOK"):
            for scenario in ("GET", "TARGET"):
                rows.add((package + "V34DefaultPathBenchmark.defaultV34NoNetwork" + method, profile, scenario))
    for profile in ("MINIMAL", "AUTO_NO_REGISTRY", "AUTO_REGISTRY"):
        for scenario in ("GET", "TARGET", "STRING", "JSON", "ENTITY", "EMPTY", "ERROR4", "ERROR5"):
            rows.add((package + "V34DefaultPathBenchmark.defaultV34LoopbackCall", profile, scenario))
    for method in ("ContextAndProxy", "FirstPublisher", "FirstCall"):
        for profile in ("AUTO_NO_REGISTRY", "AUTO_REGISTRY"):
            rows.add((package + "V34ConstructionBenchmark.defaultV34Construction" + method, profile, ""))
    return rows


def verify_results(results, smoke):
    seen = set()
    for row in results:
        identity = (row["benchmark"], row.get("params", {}).get("profile", ""), row.get("params", {}).get("scenario", ""))
        require(identity in expected_rows() and identity not in seen, "Unexpected or duplicate workload/parameter row")
        seen.add(identity)
        require(row["mode"] == "avgt" and row["threads"] == 1, "Wrong mode/threads")
        for metric, unit in ((row["primaryMetric"], "ns/op"),
                             (row.get("secondaryMetrics", {}).get("gc.alloc.rate.norm", {}), "B/op")):
            require(isinstance(metric.get("score"), (int, float)) and math.isfinite(metric["score"])
                    and metric["score"] >= 0 and metric.get("scoreUnit") == unit, "Missing/invalid timing or allocation")
            samples = metric.get("rawData", [])
            require(len(samples) == (1 if smoke else 2) and all(len(fork) == (1 if smoke else 5) for fork in samples),
                    "Incomplete fork/iteration samples")
            require(all(isinstance(value, (int, float)) and math.isfinite(value) and value >= 0
                        for fork in samples for value in fork), "Invalid raw sample")
            if not smoke:
                require(isinstance(metric.get("scoreError"), (int, float)) and math.isfinite(metric["scoreError"]),
                        "Missing finite score interval")
        if not smoke:
            require(row["forks"] == 2 and row["warmupIterations"] == 5 and row["measurementIterations"] == 5
                    and row["warmupTime"] == "1 s" and row["measurementTime"] == "1 s", "Wrong scored settings")
    require(seen == expected_rows(), "Missing workload/parameter rows")
    return {"rows": len(seen), "smokeOnly": smoke}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    record = commands.add_parser("record")
    record.add_argument("directory", type=Path)
    record.add_argument("version")
    record.add_argument("--published", action="store_true")
    pair = commands.add_parser("compare")
    pair.add_argument("baseline", type=Path)
    pair.add_argument("current", type=Path)
    results = commands.add_parser("results")
    results.add_argument("files", nargs="+", type=Path)
    results.add_argument("--smoke", action="store_true")
    args = parser.parse_args()
    if args.command == "results":
        rows = [row for file in args.files for row in json.loads(file.read_text())]
        print(json.dumps(verify_results(rows, args.smoke), indent=2))
        return
    if args.command == "compare":
        print(json.dumps(compare(json.loads(args.baseline.read_text()), json.loads(args.current.read_text())), indent=2))
        return
    directory = args.directory.resolve()
    require(directory.is_relative_to(ROOT / "target"), "Evidence belongs under target/")
    starter, dependencies = classpath_inputs((directory / "classpath.txt").read_text(), args.version, args.published)
    shaded = directory / "benchmarks.jar"
    result = {"starter": starter, "dependencies": dependencies, "published": args.published,
              "sourceFiles": source_hashes(), "shadedSha256": digest(shaded),
              "verifiedShadedStarterClasses": verify_shaded(shaded, starter),
              "sourceCommit": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
              "worktree": subprocess.check_output(["git", "status", "--porcelain"], cwd=ROOT, text=True)}
    with (directory / "inputs.json").open("x") as output:
        json.dump(result, output, indent=2, sort_keys=True)
        output.write("\n")
    print(json.dumps({"version": args.version, "nonStarterJars": len(dependencies), "shadedClasses": result["verifiedShadedStarterClasses"]}))


if __name__ == "__main__":
    main()
