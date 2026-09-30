import copy
import importlib.util
from pathlib import Path
import tempfile
import unittest
import zipfile

SPEC = importlib.util.spec_from_file_location("inputs", Path(__file__).with_name("verify-v34-benchmark-inputs.py"))
INPUTS = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(INPUTS)


class BenchmarkInputsTest(unittest.TestCase):
    def pair(self):
        baseline = {"starter": {"version": "4.4.2", "sha256": "baseline"}, "published": True,
                    "sourceFiles": {"fixture.java": "identical"}, "dependencies": {"netty.jar": "same"}, "sourceCommit": "reachable"}
        current = copy.deepcopy(baseline)
        current.update(starter={"version": "4.5.0-SNAPSHOT", "sha256": "current"}, published=False)
        return baseline, current

    def test_accepts_only_the_expected_starter_difference(self):
        self.assertTrue(INPUTS.compare(*self.pair())["matched"])

    def test_rejects_harness_dependency_and_commit_mismatches(self):
        for field in ("sourceFiles", "dependencies", "sourceCommit"):
            with self.subTest(field=field):
                baseline, current = self.pair()
                current[field] = "different"
                with self.assertRaises(ValueError):
                    INPUTS.compare(baseline, current)

    def test_rejects_local_or_wrong_baselines(self):
        for changes in ({"published": False}, {"starter": {"version": "4.4.1"}}):
            with self.subTest(changes=changes):
                baseline, current = self.pair()
                baseline.update(changes)
                with self.assertRaises(ValueError):
                    INPUTS.compare(baseline, current)

    def test_rejects_reactor_class_directories(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(ValueError, "no reactor classes"):
                INPUTS.classpath_inputs(directory, "4.4.2", True)

    def test_rejects_installed_jar_and_stale_shading(self):
        with tempfile.TemporaryDirectory() as directory:
            home = Path(directory) / INPUTS.GROUP / "reactive-http-client-starter/4.4.2"
            home.mkdir(parents=True)
            path = home / "reactive-http-client-starter-4.4.2.jar"
            with zipfile.ZipFile(path, "w") as jar:
                jar.writestr("META-INF/maven/io.github.huynhngochuyhoang/reactive-http-client-starter/pom.properties", "version=4.4.2\n")
                jar.writestr("Fixture.class", b"baseline")
            marker = home / "_remote.repositories"
            marker.write_text(f"{path.name}>=\n")
            with self.assertRaisesRegex(ValueError, "not Central-resolved"):
                INPUTS.classpath_inputs(str(path), "4.4.2", True)
            marker.write_text(f"{path.name}>maven-central=\n{path.stem}.pom>maven-central=\n")
            starter, _ = INPUTS.classpath_inputs(str(path), "4.4.2", True)
            bundle = Path(directory) / "benchmarks.jar"
            with zipfile.ZipFile(bundle, "w") as jar:
                jar.writestr("Fixture.class", b"candidate")
            with self.assertRaisesRegex(ValueError, "differs"):
                INPUTS.verify_shaded(bundle, starter)

    def samples(self):
        return [{"benchmark": name, "params": {"profile": profile, "scenario": scenario},
                 "mode": "avgt", "threads": 1,
                 "primaryMetric": {"score": 1, "scoreUnit": "ns/op", "rawData": [[1]]},
                 "secondaryMetrics": {"gc.alloc.rate.norm": {"score": 1, "scoreUnit": "B/op", "rawData": [[1]]}}}
                for name, profile, scenario in INPUTS.expected_rows()]

    def test_requires_every_parameter_combination(self):
        self.assertEqual(INPUTS.verify_results(self.samples(), True)["rows"], 54)
        with self.assertRaisesRegex(ValueError, "Missing workload"):
            INPUTS.verify_results(self.samples()[1:], True)

    def test_rejects_missing_allocations_and_duplicate_rows(self):
        rows = self.samples()
        rows[0]["secondaryMetrics"] = {}
        with self.assertRaisesRegex(ValueError, "allocation"):
            INPUTS.verify_results(rows, True)
        rows = self.samples()
        with self.assertRaisesRegex(ValueError, "duplicate"):
            INPUTS.verify_results(rows + rows[:1], True)

    def test_cannot_promote_smoke_samples_to_scored_evidence(self):
        with self.assertRaisesRegex(ValueError, "Incomplete fork"):
            INPUTS.verify_results(self.samples(), False)


if __name__ == "__main__":
    unittest.main()
