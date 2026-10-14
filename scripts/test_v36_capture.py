"""Synthetic V36 capture guards; no measured JVM or pilot is launched."""
import copy
import json
from pathlib import Path
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET

import test_v36_compiler_evidence as legacy

REVIEW = legacy.REVIEW
CAPTURE = REVIEW.module('capture', 'capture-v36-allocation.py')


class CaptureTest(unittest.TestCase):
    def setUp(self):
        self.fixture = legacy.CompilerEvidenceTest()
        self.fixture.setUp()
        self.addCleanup(self.fixture.doCleanups)
        self.directory = self.fixture.directory
        self.jar = self.fixture.jar
        self.recorder = self.fixture.root / 'boundary-recorder.jar'
        self.stage = {'id': 25, 'lane': 'T', 'profile': 'HOOK', 'side': 'current'}
        self.expected = CAPTURE.stage_spec(self.stage, self.fixture.root, self.fixture.root, self.recorder, 'seal')
        # The synthetic directory is independent of the real schedule output directory.
        self.command = {'exit': 0, 'argv': self.expected['argv'], 'stageId': self.expected['stageId'], 'launcherPid': 6}
        self.worker = {'pid': 7, 'ppid': 6, 'pgid': 6, 'startTicks': 100,
                       'argv': [str(CAPTURE.JAVA), *self.expected['jvmArgs'], '-cp',
                                str(self.jar) + ':' + str(self.recorder), 'org.openjdk.jmh.runner.ForkedMain', '127.0.0.1', '1']}
        self.row = copy.deepcopy(self.fixture.row)
        self.row['forks'] = 1
        self.row['jvmArgs'] = self.expected['jvmArgs']
        for metric in (self.row['primaryMetric'], self.row['secondaryMetrics']['gc.alloc.rate.norm']):
            metric['rawData'] = [[1744.01] * 5]
            metric['score'] = 1744.01
        for path in self.directory.glob('*8.*'):
            path.unlink()
        self.fixture.row = self.row
        self.fixture.trace('7', 200)
        trace = self.directory / 'compilation-pid7.xml'
        trace.write_text(trace.read_text().replace('java.class.path=' + str(self.jar),
                                                  'java.class.path=' + str(self.jar) + ':' + str(self.recorder)))
        self.record = {'schema': 'v36-1', 'complete': 'true', 'stage': self.expected['stageId'], 'seal': 'seal',
                       'pid': '7', 'classpath': str(self.jar) + ':' + str(self.recorder),
                       'benchmark': self.row['benchmark'], 'profile': 'HOOK', 'scenario': 'GET',
                       'recorderOrigin': self.recorder.as_uri(), 'jmhOrigin': self.jar.as_uri(),
                       'argumentCount': str(len(self.expected['jvmArgs']))}
        self.record.update({'argument.' + str(i): arg for i, arg in enumerate(self.expected['jvmArgs'])})
        for i in range(10):
            self.record[f'iteration.{i}.type'] = 'WARMUP' if i < 5 else 'MEASUREMENT'
            for phase, millis in [('before', 1000 + i * 1100), ('after', 2000 + i * 1100)]:
                self.record[f'iteration.{i}.{phase}.uptimeMs'] = str(millis)
                self.record[f'iteration.{i}.{phase}.nano'] = str(millis * 1000000)
                self.record[f'iteration.{i}.{phase}.nanoAfter'] = str(millis * 1000000 + 100)
        self.write()

    def write(self):
        for name, value in [('command', self.command), ('workers', [self.worker]), ('results', [self.row])]:
            (self.directory / (name + '.json')).write_text(json.dumps(value))
        root = ET.Element('properties')
        for key, value in self.record.items():
            ET.SubElement(root, 'entry', key=key).text = value
        ET.ElementTree(root).write(self.directory / 'boundaries.xml')

    def read(self):
        return REVIEW.v36_stage(self.directory, self.expected)

    def test_schedule_and_profiler_controls_are_exact(self):
        stages = CAPTURE.schedule()
        self.assertEqual([s['id'] for s in stages], list(range(1, 49)))
        self.assertEqual([sum(s['lane'] == lane for s in stages) for lane in 'OMTI'], [16, 8, 16, 8])
        self.assertEqual([(s['profile'], s['side']) for s in stages[:4]],
                         [('AUTO_REGISTRY', 'baseline'), ('HOOK', 'baseline'), ('HOOK', 'current'), ('AUTO_REGISTRY', 'current')])
        for lane in 'OMTI':
            result = CAPTURE.stage_spec(dict(self.stage, lane=lane), self.fixture.root, self.fixture.root, self.recorder, 'seal')
            self.assertEqual(CAPTURE.PROFILER in result['argv'], lane != 'O')
            self.assertEqual('-XX:+LogCompilation' in result['jvmArgs'], lane in 'TI')
            self.assertEqual(any('dontinline' in a for a in result['jvmArgs']), lane == 'I')

    def test_identity_and_envelopes_do_not_assert_executed_code(self):
        result = self.read()
        self.assertEqual(result['forkAssociation'], 'single-owned-worker')
        self.assertEqual(result['allocationMode'], 'low')
        self.assertEqual(len(result['iterationEnvelopes']), 10)
        self.assertEqual(len(result['compilerEnvelopeEvents']), 5)
        self.assertIsNone(result['measurementInterval'])
        self.assertEqual(result['executingCodeIdentity'], 'unverified')
        trace = self.directory / 'compilation-pid7.xml'
        trace.write_text(trace.read_text().replace('time_ms="200"', 'time_ms="1"'))
        self.assertEqual(self.read()['worker']['pid'], 7)

    def test_rejects_substituted_identity_and_vm_inputs(self):
        saved = copy.deepcopy(self.record)
        for key in ('stage', 'seal', 'pid', 'classpath', 'profile', 'scenario', 'jmhOrigin', 'recorderOrigin', 'argument.0', 'complete'):
            with self.subTest(key=key):
                self.record = dict(saved, **{key: 'wrong'})
                self.write()
                with self.assertRaises(ValueError): self.read()

    def test_rejects_missing_or_duplicate_workers_and_results(self):
        for name, value in [('workers', []), ('workers', [self.worker, self.worker]),
                            ('results', []), ('results', [self.row, self.row])]:
            self.write()
            (self.directory / (name + '.json')).write_text(json.dumps(value))
            with self.assertRaises(ValueError): self.read()

    def test_rejects_failed_and_mismatched_command_or_result(self):
        command, row = copy.deepcopy(self.command), copy.deepcopy(self.row)
        for kind in ('failed', 'command', 'stage', 'forks', 'profile', 'flags'):
            self.command, self.row = copy.deepcopy(command), copy.deepcopy(row)
            if kind == 'failed': self.command['exit'] = 124
            elif kind == 'command': self.command['argv'] = ['wrong']
            elif kind == 'stage': self.command['stageId'] = 'other'
            elif kind == 'forks': self.row['forks'] = 2
            elif kind == 'profile': self.row['params']['profile'] = 'MINIMAL'
            else: self.row['jvmArgs'] = []
            self.write()
            with self.assertRaises(ValueError): self.read()

    def test_rejects_missing_pairs_wrong_phase_and_overlapping_clocks(self):
        saved = copy.deepcopy(self.record)
        for kind in ('missing', 'phase', 'overlap', 'clock', 'extra'):
            self.record = dict(saved)
            if kind == 'missing': del self.record['iteration.5.after.nano']
            elif kind == 'phase': self.record['iteration.5.type'] = 'WARMUP'
            elif kind == 'overlap': self.record['iteration.5.before.nano'] = '1'
            elif kind == 'clock': self.record['iteration.5.after.uptimeMs'] = '50000'
            else: self.record['iteration.10.type'] = 'MEASUREMENT'
            self.write()
            with self.assertRaises(ValueError): self.read()

    def test_rejects_wrong_or_truncated_compiler_evidence(self):
        path = self.directory / 'compilation-pid7.xml'
        saved = path.read_text()
        for text in (saved[:-15], saved.replace('process="7"', 'process="9"'),
                     saved.replace('java.class.path=', 'wrong=')):
            path.write_text(text)
            with self.assertRaises((ValueError, ET.ParseError)): self.read()

    def test_boundary_precision_does_not_assign_events_to_timed_code(self):
        envelope = {'beforeUptimeMs': 1000, 'afterUptimeMs': 2000}
        self.assertEqual([REVIEW.envelope_position(stamp, envelope) for stamp in (None, '0.9', '1.001', '1.5', '2', '3')],
                         ['unknown', 'before', 'boundary-ambiguous', 'inside-envelope', 'boundary-ambiguous', 'after'])

    def test_host_limits_apply_to_ancestors_and_memory_headroom(self):
        good = {'/proc/meminfo': 'MemAvailable: 4194304 kB', 'freeDiskBytes': 20 * CAPTURE.GIB,
                'affinity': [0, 1], 'ancestralCgroupLimits': [{'cpu.max': 'max 100000', 'memory.max': 'max', 'memory.current': '0'}]}
        CAPTURE.validate_host(good)
        for kind in ('ram', 'disk', 'affinity', 'cpu', 'memory'):
            state = copy.deepcopy(good)
            if kind == 'ram': state['/proc/meminfo'] = 'MemAvailable: 1 kB'
            elif kind == 'disk': state['freeDiskBytes'] = 0
            elif kind == 'affinity': state['affinity'] = [0]
            elif kind == 'cpu': state['ancestralCgroupLimits'][0]['cpu.max'] = '100000 100000'
            else: state['ancestralCgroupLimits'][0].update({'memory.max': str(4 * CAPTURE.GIB), 'memory.current': str(2 * CAPTURE.GIB)})
            with self.assertRaises(ValueError): CAPTURE.validate_host(state)

    def test_an_existing_execution_ledger_prevents_automatic_retry(self):
        (self.directory / 'execution-ledger.json').write_text('{}')
        with patch.object(CAPTURE, 'execute') as execute:
            with self.assertRaises(FileExistsError): CAPTURE.ordinary(self.fixture.root, self.directory)
            execute.assert_not_called()

    def test_resource_abort_kills_only_owned_group_and_keeps_partial_records(self):
        destination = self.directory / 'failed'
        member = dict(self.worker, rss=3 * CAPTURE.GIB)
        with patch.object(CAPTURE, 'host_limits', return_value={}), \
                patch.object(CAPTURE.base, 'snapshot', return_value={}), \
                patch.object(CAPTURE, 'group_processes', return_value=[member]), \
                patch.object(CAPTURE.subprocess, 'Popen') as popen, \
                patch.object(CAPTURE.os, 'killpg') as kill:
            process = popen.return_value
            process.pid = 6
            process.poll.return_value = None
            process.wait.return_value = -9
            with self.assertRaisesRegex(ValueError, 'bound exceeded'):
                CAPTURE.execute(['synthetic'], destination, 'synthetic', 120,
                                CAPTURE.time.monotonic(), True, self.directory)
            kill.assert_called_once_with(6, CAPTURE.signal.SIGKILL)
            process.wait.assert_called_once()
        self.assertEqual(json.loads((destination / 'command.json').read_text())['exit'], -9)
        self.assertEqual(json.loads((destination / 'workers.json').read_text())[0]['pid'], 7)


if __name__ == '__main__':
    unittest.main()
