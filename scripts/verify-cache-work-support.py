#!/usr/bin/env python3
"""Validate the V30 fixture and execute the documented capture sanitizer offline."""

import copy
import datetime as dt
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / "docs/fixtures/support-bundle-cache-work.json"
SCHEMA = ROOT / "docs/fixtures/rhttpclients-schema-v1.json"
DOC = ROOT / "docs/26-support-bundles.md"
MAX_LONG = 9223372036854775807
WORK_FIELDS = [name for name in json.loads(SCHEMA.read_text())["clients"][0]
               if name.startswith("cacheWork")]


def number(value):
    assert type(value) is int and 0 <= value <= MAX_LONG, value
    return value


def timestamp(value):
    assert isinstance(value, str) and value.endswith("Z"), value
    return dt.datetime.fromisoformat(value.replace("Z", "+00:00"))


def counters(value):
    assert isinstance(value, dict) and value
    for child in value.values():
        if isinstance(child, dict):
            counters(child)
        else:
            number(child)


def difference(before, after):
    assert before.keys() == after.keys()
    return {key: difference(before[key], after[key]) if isinstance(before[key], dict)
            else number(after[key] - before[key]) for key in before}


def validate_fixture(f):
    assert f["schemaVersion"] == 1 and f["projectVersion"] == "4.3.0-SNAPSHOT"
    assert f["captureScope"] == "cache-work"
    for name in [f["clientName"], f["processInstance"], *f["target"].values()]:
        assert isinstance(name, str) and re.fullmatch(r"[A-Za-z0-9_.-]{1,64}", name)
    for name in ("window", "counterWindow", "quietWindow"):
        window = f[name]
        assert 0 < number(window["durationSeconds"]) <= 300
        assert (timestamp(window["endedAt"]) - timestamp(window["startedAt"])).total_seconds() \
            == window["durationSeconds"]
    assert f["counterWindow"]["resetObserved"] is False
    config = f["configuration"]
    for field in ("observabilityEnabled", "cacheMetricsEnabled", "meterRegistryAvailable"):
        assert config[field] is True  # This fixture deliberately captures registered meters.
    assert config["pool"]["metricsEnabled"] is True
    assert config["pool"]["protocol"] == "HTTP/2"
    number(config["logicalCallTimeoutMs"])
    number(config["requestTimeoutMs"])
    policies = config["policies"]
    # One fully measured policy keeps the illustrative conservation equations explicit.
    assert len(policies) == 1 and len(config["apiPolicies"]) == 1
    policy_name, policy = next(iter(policies.items()))
    api_name, mapped_policy = next(iter(config["apiPolicies"].items()))
    assert mapped_policy == policy_name
    for name in (policy_name, api_name):
        assert re.fullmatch(r"[A-Za-z0-9_.-]{1,64}", name)
    assert policy["source"] in ("client", "method")
    assert policy["singleFlight"] is True
    assert 0 < number(policy["refreshAfterMs"]) < number(policy["ttlMs"])
    assert number(policy["refreshTimeoutMs"]) > 0
    assert number(policy["maximumEntries"]) > 0
    assert number(policy["maximumTotalDecodedResponseBytes"]) > 0
    dimensions = {"activeCallers": "maximumConcurrentCallers",
                  "activeLoads": "maximumConcurrentLoads",
                  "activeRefreshes": "maximumConcurrentRefreshes"}
    for maximum in dimensions.values():
        assert 1 <= number(policy[maximum]) <= 1000000
    points = f["checkpoints"]
    assert [p["phase"] for p in points] == [
        "before-traffic", "saturated", "before-quiet", "recovered-before-close", "after-close"]
    times = [timestamp(p["capturedAt"]) for p in points]
    assert all(a < b for a, b in zip(times, times[1:]))
    assert points[0]["capturedAt"] == f["window"]["startedAt"] == f["counterWindow"]["startedAt"]
    assert points[-1]["capturedAt"] == f["window"]["endedAt"]
    assert points[-2]["capturedAt"] == f["counterWindow"]["endedAt"] == f["quietWindow"]["endedAt"]
    assert points[-3]["capturedAt"] == f["quietWindow"]["startedAt"]
    start, close = f["lifecycle"]
    assert start["type"] == "factory-start" and close["type"] == "factory-close"
    assert timestamp(start["capturedAt"]) < times[0] < times[-2] < timestamp(close["capturedAt"]) < times[-1]
    assert (times[-2] - timestamp(start["capturedAt"])).total_seconds() * 1000 < policy["ttlMs"]
    for point in points:
        assert point["contextOrdinal"] == start["contextOrdinal"] == close["contextOrdinal"] == "context-1"
        assert point["policies"].keys() == policies.keys()
        occupancy = point["policies"][policy_name]
        assert number(occupancy["entries"]) <= policy["maximumEntries"]
        assert number(occupancy["retainedDecodedResponseBytes"]) <= policy["maximumTotalDecodedResponseBytes"]
        assert occupancy["retainedDecodedResponseBytes"] == occupancy["entries"] * 100
        if point["phase"] == "after-close":
            assert point["factoryState"] == "closed" and number(point["workMeterRegistrations"]) == 0
            assert occupancy["entries"] == occupancy["retainedDecodedResponseBytes"] == 0
            assert point["pool"] is None and point["apiCounters"] is None and point["policyCounters"] is None
            assert all(occupancy[name] is None for name in dimensions)
            continue
        assert point["factoryState"] == "open" and number(point["workMeterRegistrations"]) == 11
        for active, maximum in dimensions.items():
            assert number(occupancy[active]) <= policy[maximum]
        pool = point["pool"]
        for value in pool.values():
            number(value)
        assert pool["idleConnections"] <= pool["totalConnections"] <= config["pool"]["maximumConnections"]
        assert pool["activeStreams"] <= pool["totalConnections"] * config["pool"]["maximumConcurrentStreams"]
        assert point["apiCounters"].keys() == config["apiPolicies"].keys()
        assert point["policyCounters"].keys() == policies.keys()
        counters(point["apiCounters"])
        counters(point["policyCounters"])
        api = point["apiCounters"][api_name]
        pc = point["policyCounters"][policy_name]
        assert api["loads"].keys() == api["refreshes"].keys() == {"success", "failure", "cancellation"}
        assert pc["rejections"].keys() == {"caller_capacity", "load_capacity"}
        assert pc["refreshSkips"].keys() == {"capacity", "already_refreshing", "entry_unavailable"}
        assert pc["admissions"].keys() == {
            "admitted", "bypassed_unknown_size", "bypassed_over_budget", "bypassed_capacity"}
        assert api["lookups"]["hit"] == api["callers"]["FRESH_HIT"] + api["callers"]["STALE_HIT"]
        assert api["lookups"]["miss"] == sum(api["callers"][n] for n in (
            "MISS_LOADER", "COALESCED_WAITER", "LOAD_REJECTED"))
        assert pc["rejections"]["caller_capacity"] == api["callers"]["CALLER_REJECTED"]
        assert pc["rejections"]["load_capacity"] == api["callers"]["LOAD_REJECTED"]
        # Fixture-specific facts: no preparation failures or in-progress publication cleanup.
        assert api["callers"]["MISS_LOADER"] == sum(api["loads"].values()) + occupancy["activeLoads"]
        assert api["callers"]["STALE_HIT"] == sum(api["refreshes"].values()) \
            + sum(pc["refreshSkips"].values()) + occupancy["activeRefreshes"]
        assert pc["admissions"]["admitted"] == api["loads"]["success"] + api["refreshes"]["success"]
        assert all(v == 0 for k, v in pc["admissions"].items() if k != "admitted")
        assert all(v == 0 for v in pc["evictions"].values())
        assert occupancy["entries"] == api["loads"]["success"]
    for before, after in zip(points[:3], points[1:4]):
        difference(before["apiCounters"], after["apiCounters"])
        difference(before["policyCounters"], after["policyCounters"])
    counters(f["deltas"])
    assert f["deltas"]["api"] == difference(points[0]["apiCounters"], points[-2]["apiCounters"])
    assert f["deltas"]["policies"] == difference(points[0]["policyCounters"], points[-2]["policyCounters"])
    quiet_delta = difference(points[-3]["apiCounters"], points[-2]["apiCounters"])[api_name]
    assert quiet_delta["loads"]["cancellation"] == quiet_delta["refreshes"]["cancellation"] == 1
    assert all(v == 0 for v in quiet_delta["callers"].values())
    assert all(points[-2]["policies"][policy_name][name] == 0 for name in dimensions)
    terminal = f["affectedCaller"]
    assert times[0] <= timestamp(terminal["capturedAt"]) <= times[-2]
    assert terminal["clientName"] == f["clientName"] and terminal["apiName"] == api_name
    assert terminal["cacheOutcome"] == "CALLER_REJECTED" and terminal["reason"] == "CALLER_CAPACITY"
    assert terminal["errorType"] == "CacheWorkRejectedException"
    assert terminal["errorCategory"] == "CACHE_ADMISSION_ERROR"
    assert terminal["failureStage"] is None and terminal["statusCode"] is None
    assert number(terminal["subscriptionAttemptCount"]) == 0
    assert terminal["requestDispatched"] is False and terminal["cancelled"] is False
    assert number(terminal["durationMs"]) <= f["counterWindow"]["durationSeconds"] * 1000


class FixtureTests(unittest.TestCase):
    def test_canonical_accounting(self):
        validate_fixture(json.loads(FIXTURE.read_text()))

    def test_inconsistent_fixture_mutations(self):
        mutations = [
            (("window", "durationSeconds"), 13),
            (("configuration", "policies", "catalog-read", "source"), "tenant-production"),
            (("configuration", "policies", "catalog-read", "ttlMs"), 500),
            (("configuration", "policies", "catalog-read", "maximumEntries"), 1),
            (("checkpoints", 1, "policies", "catalog-read", "activeCallers"), 4),
            (("checkpoints", 1, "pool", "idleConnections"), 3),
            (("checkpoints", 3, "apiCounters", "catalog.search", "loads", "failure"), "1"),
            (("checkpoints", 3, "apiCounters", "catalog.search", "loads", "cancellation"), -1),
            (("checkpoints", 3, "apiCounters", "catalog.search", "lookups", "hit"), 4),
            (("checkpoints", 3, "policyCounters", "catalog-read", "admissions", "admitted"), 3),
            (("checkpoints", 4, "apiCounters"), {}),
            (("lifecycle", 1, "type"), "factory-start"),
            (("lifecycle", 1, "capturedAt"), "2026-09-10T00:00:13Z"),
            (("deltas", "policies", "catalog-read", "refreshSkips", "capacity"), 99),
            (("affectedCaller", "requestDispatched"), True),
        ]
        for path, value in mutations:
            with self.subTest(path=path):
                fixture = json.loads(FIXTURE.read_text())
                parent = fixture
                for part in path[:-1]:
                    parent = parent[part]
                parent[path[-1]] = value
                with self.assertRaises(AssertionError):
                    validate_fixture(fixture)


class CaptureTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        doc = DOC.read_text()
        cls.script = re.search(r'```bash\nEXAMPLE_RHTTPCLIENTS_SCHEMA=.*?\n```', doc, re.S).group(0)[8:-4]
        cls.script = cls.script.replace("/path/to/reviewed/rhttpclients-schema-v1.json", str(SCHEMA))

    def capture(self, diagnostics=None, health=None, http="200", exit_status=0):
        diagnostics = self.diagnostics() if diagnostics is None else diagnostics
        health = self.health() if health is None else health
        with tempfile.TemporaryDirectory(prefix="cache-work-capture-") as directory:
            root = Path(directory)
            for area, stem, body in (("diagnostics", "rhttpclients", diagnostics),
                                     ("health", "reactive-http-client-health", health)):
                folder = root / "support-bundle" / area
                folder.mkdir(parents=True)
                (folder / (stem + "-http-status.txt")).write_text(http)
                (folder / (stem + "-curl-exit-status.txt")).write_text(str(exit_status))
                (root / (stem + ".raw.json")).write_text(body if isinstance(body, str) else json.dumps(body))
            result = subprocess.run(["bash", "-c", self.script], cwd=root,
                                    env={**os.environ, "EXAMPLE_CLIENT_NAME": "example-client"},
                                    capture_output=True, text=True, timeout=10)
            outputs = []
            for path in ("diagnostics/rhttpclients.json", "health/health.json"):
                target = root / "support-bundle" / path
                outputs.append(json.loads(target.read_text()) if target.exists() else None)
            self.assertNotIn("compile error", result.stderr, result.stderr)
            return outputs

    @staticmethod
    def diagnostics():
        result = json.loads(SCHEMA.read_text())
        result["projectVersion"] = "4.3.0-SNAPSHOT"
        return result

    @staticmethod
    def health():
        return {"status": "UP", "details": {"minSamples": 5, "errorRateThreshold": 0.5,
                "example-client": {"status": "UP", "reason": "ERROR_RATE_WITHIN_THRESHOLD",
                "samples": 10, "errors": 0, "sampleCount": 10, "errorCount": 0,
                "poolAcquireFailureCount": 0, "minSamples": 5, "errorRateThreshold": 0.5, "errorRate": 0}}}

    def test_current_and_published_versions(self):
        self.assertIsNotNone(self.capture()[0])
        for version in ("4.1.0", "4.2.0"):
            doc = self.diagnostics()
            doc["projectVersion"] = version
            for field in WORK_FIELDS:
                del doc["clients"][0][field]
            if version == "4.1.0":
                del doc["clients"][0]["cacheMaximumTotalDecodedResponseBytes"]
                del doc["clients"][0]["cacheRetainedDecodedResponseBytes"]
            self.assertEqual(self.capture(doc)[0], doc)
        doc["projectVersion"] = "4.3.0-SNAPSHOT"
        self.assertIsNone(self.capture(doc)[0])

    def test_work_unknown_absent_lazy_open_closed_mixed(self):
        for state in ("unknown", "absent", "uninitialized", "open", "closed", "mixed"):
            with self.subTest(state=state):
                doc = self.diagnostics()
                client = doc["clients"][0]
                if state == "unknown":
                    for field in WORK_FIELDS:
                        client[field] = None
                    for field in ("poolMaxConnections", "compressionEnabled", "cacheMetricsEnabled",
                                  "cachePolicyCount", "cachePolicySources", "cacheHttpMethods"):
                        client[field] = None
                elif state != "absent":
                    client.update(cachePolicyCount=2 if state == "mixed" else 1,
                                  cacheWorkSelection="mixed" if state == "mixed" else "selected",
                                  cacheWorkState="open" if state == "mixed" else state,
                                  cacheWorkLimitedPolicyCount=1,
                                  cacheWorkMaximumConcurrentCallers=3, cacheWorkMaximumConcurrentLoads=2)
                    if state != "uninitialized":
                        client.update(cacheWorkActiveCallers=1, cacheWorkActiveLoads=1)
                self.assertEqual(self.capture(doc)[0], doc)

    def test_invalid_work_facts(self):
        doc = self.diagnostics()
        client = doc["clients"][0]
        client.update(cachePolicyCount=1, cacheWorkSelection="selected", cacheWorkState="open",
                      cacheWorkLimitedPolicyCount=1, cacheWorkMaximumConcurrentCallers=3,
                      cacheWorkMaximumConcurrentLoads=2, cacheWorkMaximumConcurrentRefreshes=1,
                      cacheWorkActiveCallers=2, cacheWorkActiveLoads=1, cacheWorkActiveRefreshes=1)
        self.assertEqual(self.capture(doc)[0], doc)
        for field, value in (
                ("cacheWorkSelection", "tenant-production"), ("cacheWorkState", "active"),
                ("cacheWorkLimitedPolicyCount", 17), ("cacheWorkLimitedPolicyCount", None),
                ("cacheWorkMaximumConcurrentCallers", 1000001), ("cacheWorkActiveCallers", 4),
                ("cacheWorkActiveLoads", "1"), ("cacheWorkActiveLoads", -1),
                ("cacheWorkActiveRefreshes", 2), ("cacheWorkMaximumConcurrentLoads", None),
                ("cacheWorkState", "uninitialized")):
            with self.subTest(field=field, value=value):
                invalid = copy.deepcopy(doc)
                invalid["clients"][0][field] = value
                self.assertIsNone(self.capture(invalid)[0])
        for version in ("4.2.0", "4.3.0-SNAPSHOT"):
            invalid = copy.deepcopy(doc)
            invalid["projectVersion"] = version
            del invalid["clients"][0]["cacheWorkActiveLoads"]
            self.assertIsNone(self.capture(invalid)[0])

    def test_work_policy_count_matches_selected_cache_policies(self):
        for selection, limited, total, valid in (
                ("selected", 1, 0, False),
                ("selected", 1, 1, True),
                ("selected", 1, 2, False),
                ("selected", 2, 1, False),
                ("selected", 2, 2, True),
                ("mixed", 1, 0, False),
                ("mixed", 1, 1, False),
                ("mixed", 1, 2, True),
                ("mixed", 2, 1, False),
                ("mixed", 2, 2, False),
                ("mixed", 2, 3, True),
                ("absent", 0, 0, True),
                ("absent", 0, 2, True),
                ("selected", 1, None, True),
                ("mixed", 1, None, True),
                ("absent", 0, None, True)):
            with self.subTest(selection=selection, limited=limited, total=total):
                doc = self.diagnostics()
                client = doc["clients"][0]
                client.update(cachePolicyCount=total, cacheWorkSelection=selection,
                              cacheWorkLimitedPolicyCount=limited)
                if limited:
                    client.update(cacheWorkState="open",
                                  cacheWorkMaximumConcurrentCallers=3, cacheWorkMaximumConcurrentLoads=2,
                                  cacheWorkActiveCallers=1, cacheWorkActiveLoads=1)
                if valid:
                    self.assertEqual(self.capture(doc)[0], doc)
                else:
                    self.assertIsNone(self.capture(doc)[0])

    def test_existing_scalar_list_and_capacity_guards(self):
        for field, value in (
                ("clientName", {"message": "unsafe"}), ("clientName", "\U0001f600" * 257),
                ("cachePolicySources", ["client"] * 17), ("cacheHttpMethods", ["tenant-production"]),
                ("endpointCount", -1), ("inheritedEndpointCount", 3),
                ("cacheEntryCount", 1), ("cacheRetainedDecodedResponseBytes", -1)):
            with self.subTest(field=field):
                doc = self.diagnostics()
                doc["clients"][0][field] = value
                self.assertIsNone(self.capture(doc)[0])
        doc = self.diagnostics()
        doc["clients"][0].update(cacheMaximumTotalDecodedResponseBytes=1, cacheRetainedDecodedResponseBytes=2)
        self.assertIsNone(self.capture(doc)[0])

    def test_transfer_status_size_and_single_document(self):
        for body in ("", "{} {}", "null", "{", " " * 1048577 + "{}"):
            with self.subTest(body=body[:20]):
                self.assertEqual(self.capture(body, body), [None, None])
        for exit_status in (6, 18, 28, 63):
            self.assertEqual(self.capture(exit_status=exit_status), [None, None])
        for status in ("000", "401", "403"):
            self.assertEqual(self.capture(http=status), [None, None])
        health = self.health()
        health["status"] = "DOWN"
        health["details"]["example-client"].update(status="DOWN", reason="ERROR_RATE_ABOVE_THRESHOLD",
                                                 errors=10, errorCount=10, errorRate=1)
        self.assertEqual(self.capture(health=health, http="503"), [None, {
            "status": "DOWN", "details": {"example-client": health["details"]["example-client"]}}])

    def test_health_unknown_zero_and_consistency(self):
        health = self.health()
        health["details"]["example-client"].update(status="INSUFFICIENT_SAMPLES", reason="NO_SAMPLES",
                                                  samples=0, sampleCount=0)
        del health["details"]["example-client"]["errorRate"]
        out = self.capture(health=health)[1]
        self.assertNotIn("errorRate", out["details"]["example-client"])
        for field, value in (("reason", {"message": "unsafe"}), ("samples", 1e100),
                             ("errorRate", 1), ("minSamples", 6), ("status", "DOWN")):
            with self.subTest(field=field):
                health = self.health()
                health["details"]["example-client"][field] = value
                self.assertIsNone(self.capture(health=health)[1])


if __name__ == "__main__":
    unittest.main(verbosity=2)
