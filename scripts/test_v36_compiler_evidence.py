"""Synthetic evidence only: never launch a benchmark or compiler diagnostic JVM."""
import copy
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET

SPEC = importlib.util.spec_from_file_location('followup', Path(__file__).with_name('investigate-v35-final-profiles.py'))
FOLLOWUP = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(FOLLOWUP)
REVIEW = FOLLOWUP.review
PACKAGE = 'io.github.huynhngochuyhoang.httpstarter.'


class CompilerEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.jar = self.root / 'build-current/benchmarks.jar'
        self.directory = self.root / 'diagnostic'
        self.directory.mkdir()
        self.extra = FOLLOWUP.schedule()[0][4]
        metric = {'score': 15, 'scoreError': 1, 'scoreUnit': 'ns/op', 'rawData': [[10] * 5, [20] * 5]}
        self.row = {'benchmark': PACKAGE + 'benchmarks.V34DefaultPathBenchmark.defaultV34NoNetworkWarmPublisher',
            'params': {'profile': 'HOOK', 'scenario': 'GET'}, 'mode': 'avgt', 'threads': 1, 'forks': 2,
            'warmupIterations': 5, 'measurementIterations': 5, 'warmupTime': '1 s', 'measurementTime': '1 s',
            'jvmArgs': FOLLOWUP.allocation.FLAGS + self.extra, 'jdkVersion': '21.0.8', 'jmhVersion': '1.37',
            'primaryMetric': metric, 'secondaryMetrics': {'gc.alloc.rate.norm': dict(metric, scoreUnit='B/op')}}
        self.command = {'exit': 0, 'argv': FOLLOWUP.allocation.command(self.pair(), 'current', 'HOOK', 2,
            self.extra, False, self.directory)}
        self.write_json()
        for pid, millis in [('7', 200), ('8', 100)]:
            self.trace(pid, millis)

    def pair(self):
        self.jar.parent.mkdir()
        (self.jar.parent / 'inputs.json').write_text(json.dumps({
            'starter': {'version': '4.5.0-SNAPSHOT'}, 'sourceCommit': 'synthetic'}))
        return self.root

    def write_json(self):
        (self.directory / 'command.json').write_text(json.dumps(self.command))
        (self.directory / 'results.json').write_text(json.dumps([self.row]))

    def trace(self, pid, millis):
        path = self.directory / f'compilation-pid{pid}.xml'
        path.write_text(f'''<hotspot_log time_ms="{millis}" process="{pid}">
          <vm_arguments><args>{' '.join(self.row['jvmArgs'])}</args><properties>java.class.path={self.jar}</properties></vm_arguments>
          <nmethod compile_id="1" method="{PACKAGE}core.CacheWorkPolicy validate ()V" stamp="2"/>
          <make_not_entrant compile_id="1" stamp="4"/>
          <nmethod compile_id="2" method="{PACKAGE}core.CacheWorkPolicy validate ()V" stamp="5"/>
          <nmethod compile_id="99" method="unrelated.Type work ()V" stamp="6"/>
          <make_not_entrant compile_id="99" stamp="7"/>
          <compilation_log><task compile_id="1" method="{PACKAGE}core.CacheWorkPolicy validate ()V" stamp="1">
            <klass id="1" name="{PACKAGE}core.EffectiveCachePolicy$Selection"/>
            <method id="2" holder="1" name="disabled"/><parse method="2"><call method="2"/>
            <inline_success reason="inline (hot)"/></parse><eliminate_allocation type="1">
            <jvms method="2" bci="0"/></eliminate_allocation><task_done success="1" stamp="2"/>
          </task><task compile_id="2" method="{PACKAGE}core.CacheWorkPolicy validate ()V" stamp="4.5">
            <task_done success="1" stamp="5"/>
          </task></compilation_log><hotspot_log_done stamp="10"/></hotspot_log>''')
        (self.directory / f'classes-{pid}.log').write_text(''.join(
            f'[0.5s][info][class,load] {PACKAGE}{name} source: {self.jar.as_uri()}\n'
            for name in ('core.EffectiveCachePolicy$Selection', 'config.ReactiveHttpClientProperties$CachePolicyConfig')))
        return path

    def summarize(self):
        return FOLLOWUP.summarize(self.directory, 'current', 'HOOK', 2, self.extra, self.root)

    def test_followup_never_pairs_scores_by_timestamp(self):
        result = self.summarize()
        self.assertEqual(result['metrics']['allocation']['forkMeans'], [10, 20])
        for trace in result['traces']:
            self.assertNotIn('fork', trace)
            self.assertNotIn('allocationBytesPerOp', trace)
        self.assertEqual(result['forkAssociation'], 'unverified')
        self.assertIsNone(result['measurementInterval'])
        self.trace('7', 50)
        self.trace('8', 300)
        reordered = self.summarize()
        self.assertEqual([t['process'] for t in result['traces']], [t['process'] for t in reordered['traces']])

    def test_shared_trace_reader_rejects_missing_duplicate_and_mismatched_process_files(self):
        original = self.directory / 'compilation-pid7.xml'
        saved = original.read_text()
        mutations = {'missing': None, 'duplicate': saved.replace('process="7"', 'process="8"'),
                     'wrong-name': saved.replace('process="7"', 'process="9"'),
                     'truncated': saved[:-20], 'no-done': saved.replace('<hotspot_log_done stamp="10"/>', '')}
        for name, text in mutations.items():
            with self.subTest(name=name):
                if text is None:
                    original.unlink()
                else:
                    original.write_text(text)
                with self.assertRaises((ValueError, ET.ParseError)):
                    REVIEW.diagnostic_traces(self.directory, self.row, self.jar)
                original.write_text(saved)
        (self.directory / 'classes-99.log').write_text('unassociated')
        with self.assertRaisesRegex(ValueError, 'class-load inventory'):
            REVIEW.diagnostic_traces(self.directory, self.row, self.jar)

    def test_rejects_failed_command_and_wrong_profile_or_artifact(self):
        saved = copy.deepcopy(self.command)
        for field in ('exit', 'profile', 'jar', 'flags', 'warmup'):
            with self.subTest(field=field):
                self.command = copy.deepcopy(saved)
                if field == 'exit':
                    self.command['exit'] = 124
                else:
                    argv = self.command['argv']
                    old = {'profile': 'profile=HOOK', 'jar': str(self.jar),
                           'flags': ' '.join(self.row['jvmArgs']), 'warmup': '-wi'}[field]
                    argv[argv.index(old) + (1 if field == 'warmup' else 0)] = 'wrong'
                self.write_json()
                with self.assertRaises(ValueError):
                    self.summarize()

    def test_rejects_missing_and_duplicate_result_rows(self):
        for rows in ([], [self.row, self.row]):
            (self.directory / 'results.json').write_text(json.dumps(rows))
            with self.assertRaisesRegex(ValueError, 'result inventory'):
                self.summarize()

    def test_classpath_and_required_class_inventory_are_checked(self):
        path = self.directory / 'compilation-pid7.xml'
        path.write_text(path.read_text().replace('java.class.path=' + str(self.jar), 'java.class.path=/wrong.jar'))
        with self.assertRaisesRegex(ValueError, 'classpath'):
            self.summarize()
        self.trace('7', 100)
        log = self.directory / 'classes-7.log'
        saved = log.read_text()
        for text in (saved.splitlines()[0] + '\n', saved.splitlines()[0] + '\n' + saved.splitlines()[0] + '\n',
                     saved.replace(self.jar.as_uri(), (self.root / 'other.jar').as_uri())):
            log.write_text(text)
            with self.assertRaises(ValueError):
                self.summarize()

    def test_compilation_history_does_not_promote_unrelated_or_superseded_tasks(self):
        result = REVIEW.compiler(self.directory / 'compilation-pid7.xml')
        first, second = result['tasks']
        self.assertEqual(first['nmethods'][0]['compile_id'], '1')
        self.assertEqual(first['invalidations'][0]['stamp'], '4')
        self.assertEqual(second['nmethods'][0]['compile_id'], '2')
        self.assertEqual(second['invalidations'], [])
        self.assertEqual(first['completion']['success'], '1')
        self.assertEqual([n['compile_id'] for n in result['nmethods']], ['1', '2'])
        self.assertIsNone(result['measurementInterval'])

    def test_incomplete_task_is_not_presented_as_successful_compilation(self):
        path = self.directory / 'compilation-pid7.xml'
        path.write_text(path.read_text().replace('<task_done success="1" stamp="5"/>', ''))
        result = REVIEW.compiler(path)
        self.assertIsNone(result['tasks'][1]['completion'])
        self.assertIsNone(result['measurementInterval'])


if __name__ == '__main__':
    unittest.main()
