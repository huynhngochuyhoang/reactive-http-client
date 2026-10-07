import copy
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import zipfile

SPEC = importlib.util.spec_from_file_location('review35', Path(__file__).with_name('review-v35-allocation.py'))
REVIEW = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(REVIEW)
RUNNER = REVIEW.RUNNER


class AllocationInvestigationTest(unittest.TestCase):
    def test_schedule_is_bounded_and_keeps_both_artifact_orders(self):
        scored = RUNNER.stages('scored')
        self.assertEqual([r[1] for r in scored], ['baseline', 'current', 'current', 'baseline'])
        self.assertEqual(sum(len(r[2].split(',')) * r[3] for r in scored), 24)
        diagnostic = RUNNER.stages('diagnostic')
        self.assertEqual(sum(r[3] for r in diagnostic), 22)
        self.assertTrue(all(r[2] == 'RESILIENCE_ENABLED_ONLY' for r in diagnostic))

    def test_diagnostic_factors_are_separate_from_each_other_and_scored_flags(self):
        self.assertTrue(all(not r[4] and not r[5] for r in RUNNER.stages('scored')))
        by_name = {r[0]: r[4] for r in RUNNER.stages('diagnostic')}
        control = by_name['trace-baseline']
        for name, directive in [('no-ea', '-XX:-DoEscapeAnalysis'),
                                ('dontinline', f'-XX:CompileCommand=dontinline,{RUNNER.TARGET}'),
                                ('inline', f'-XX:CompileCommand=inline,{RUNNER.TARGET}')]:
            for side in ('baseline', 'current'):
                self.assertEqual(by_name[f'{name}-{side}'], control + [directive])

    def test_metrics_preserve_every_fork_and_reject_missing_samples(self):
        row = {'primaryMetric': {'score': 100, 'scoreError': 1, 'scoreUnit': 'ns/op', 'rawData': [[100] * 5] * 2},
               'secondaryMetrics': {'gc.alloc.rate.norm': {'score': 1264, 'scoreError': 204,
                                     'scoreUnit': 'B/op', 'rawData': [[1136] * 5, [1392] * 5]}}}
        self.assertEqual(REVIEW.metrics(row, 2)['allocation']['forkMeans'], [1136, 1392])
        for mutation in ('missing', 'negative', 'nonfinite'):
            bad = copy.deepcopy(row)
            metric = bad['secondaryMetrics']['gc.alloc.rate.norm']
            if mutation == 'missing':
                metric['rawData'].pop()
            else:
                metric['rawData'][0][0] = -1 if mutation == 'negative' else float('nan')
            with self.assertRaises(ValueError):
                REVIEW.metrics(bad, 2)

    def test_input_audit_rejects_executable_changes_but_records_metadata(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for side, version in [('baseline', '4.4.2'), ('current', '4.5.0-SNAPSHOT')]:
                target = root / f'build-{side}'
                target.mkdir()
                with zipfile.ZipFile(target / 'benchmarks.jar', 'w') as jar:
                    jar.writestr('fixture.class', b'unchanged')
                    jar.writestr('pom.properties', version)
                inputs = {'sourceFiles': {}, 'dependencies': {}, 'starter': {'version': version},
                          'published': side == 'baseline', 'shadedSha256': RUNNER.digest(target / 'benchmarks.jar')}
                (target / 'inputs.json').write_text(json.dumps(inputs))
                (target / 'classpath.txt').write_text('one:two')
            audit = RUNNER.audit_pair(root)
            self.assertEqual(audit['differentResources'], ['pom.properties'])
            current = root / 'build-current'
            with zipfile.ZipFile(current / 'benchmarks.jar', 'w') as jar:
                jar.writestr('fixture.class', b'changed')
            inputs['shadedSha256'] = RUNNER.digest(current / 'benchmarks.jar')
            (current / 'inputs.json').write_text(json.dumps(inputs))
            with self.assertRaisesRegex(ValueError, 'class bytes differ'):
                RUNNER.audit_pair(root)

    def test_timeout_kills_owned_worker_group_and_records_failure(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(RUNNER, 'snapshot', return_value={
                '/proc/meminfo': 'MemAvailable: 4194304 kB'}), patch.object(RUNNER.subprocess, 'Popen') as popen, \
                patch.object(RUNNER.os, 'killpg') as kill, patch.dict(RUNNER.os.environ, {}, clear=True):
            process = popen.return_value
            process.pid = 12345
            process.wait.side_effect = [subprocess.TimeoutExpired(['java'], 1), 0]
            target = Path(directory) / 'timeout'
            with self.assertRaisesRegex(RuntimeError, 'Failed stage'):
                RUNNER.run(['java'], target, timeout=1)
            kill.assert_called_once_with(12345, RUNNER.signal.SIGKILL)
            self.assertTrue(popen.call_args.kwargs['start_new_session'])
            self.assertEqual(json.loads((target / 'command.json').read_text())['exit'], 124)

    def test_compiler_summary_retains_elimination_and_inlining_context(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'compilation.xml'
            path.write_text('''<hotspot_log time_ms="100" process="1"><compilation_log><task compile_id="7"
                method="CacheWorkPolicy validator" stamp="1"><klass id="1" name="test.EffectiveCachePolicy$Selection"/>
                <method id="2" holder="1" name="disabled"/><parse method="2"><call method="2"/>
                <inline_success reason="inline (hot)"/></parse><eliminate_allocation type="1">
                <jvms method="2" bci="0"/></eliminate_allocation></task></compilation_log></hotspot_log>''')
            result = REVIEW.compiler(path)
            self.assertEqual(result['tasks'][0]['policyInlining'][0]['decision'], 'inline_success')
            self.assertEqual(result['tasks'][0]['eliminations'][0]['class'], 'test.EffectiveCachePolicy$Selection')

    def test_loaded_classes_require_the_saved_artifact_and_record_type_timing(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            jar = root / 'benchmarks.jar'
            log = root / 'classes.log'
            package = 'io.github.huynhngochuyhoang.httpstarter.'
            log.write_text(f'[0.5s][info][class,load] {package}core.EffectiveCachePolicy$Selection source: {jar.as_uri()}\n'
                           f'[1.1s][info][class,load] {package}config.ReactiveHttpClientProperties$CachePolicyConfig source: {jar.as_uri()}\n')
            result = REVIEW.loaded_classes(log, jar)
            self.assertEqual(result['starterAndHarnessClassesFromSavedJar'], 2)
            self.assertEqual([r['seconds'] for r in result['selected']], [0.5, 1.1])
            with self.assertRaisesRegex(ValueError, 'outside the saved JAR'):
                REVIEW.loaded_classes(log, root / 'wrong.jar')

    def test_evidence_rejects_recordings_with_environment_or_process_events(self):
        allowed = ' jdk.ObjectAllocationSample 10 200\n jdk.Metadata 1 30\n jdk.Checkpoint 2 40\n'
        REVIEW.validate_jfr_summary(allowed + ' jdk.InitialEnvironmentVariable 0 0\n')
        for extra in [' jdk.InitialEnvironmentVariable 1 30\n', ' jdk.SystemProcess 1 50\n', ' jdk.ProcessStart 1 30\n']:
            with self.assertRaisesRegex(ValueError, 'Scrub JFR'):
                REVIEW.validate_jfr_summary(allowed + extra)


if __name__ == '__main__':
    unittest.main()
