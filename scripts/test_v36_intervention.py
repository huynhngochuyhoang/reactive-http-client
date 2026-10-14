"""Synthetic V36-P5-1 controls; no experiment processes."""
import json
from pathlib import Path
import unittest
from unittest.mock import patch

import test_v36_capture as ordinary

CAPTURE = ordinary.CAPTURE
REVIEW = ordinary.REVIEW


class InterventionTest(unittest.TestCase):
    def setUp(self):
        self.fixture = ordinary.CaptureTest()
        self.fixture.setUp()
        self.addCleanup(self.fixture.doCleanups)
        self.root = self.fixture.fixture.root
        self.out = self.root / 'intervention'
        self.out.mkdir()
        self.prior = {'state': 'ordinary-complete; intervention unexecuted',
                      'attemptedStageIds': list(range(1, 41)), 'completedStageIds': list(range(1, 41)),
                      'unattemptedStageIds': list(range(41, 49)), 'activeSeconds': 502.14051217597444,
                      'rawBytes': 123}
        (self.out / 'prior-budget.json').write_text(json.dumps(self.prior))
        (self.out / 'sealed-inputs.json').write_text('{}')
        baseline = self.root / 'build-baseline'
        baseline.mkdir()
        (baseline / 'inputs.json').write_text(json.dumps({'starter': {'version': '4.4.2'}, 'sourceCommit': 'synthetic'}))
        self.result = {'metrics': {'allocation': {'mean': 2000}}, 'allocationMode': 'high',
                       'forkAssociation': 'single-owned-worker', 'intervention': {'nonInliningObserved': True}}

    def execute(self, argv, directory, stage_id, timeout, started, worker_required, raw_root, **kwargs):
        self.assertEqual(timeout, 120)
        self.assertTrue(worker_required)
        self.assertTrue(kwargs['check_probes'])
        self.assertEqual(kwargs['prior_bytes'], 123)
        self.assertAlmostEqual(1000 - started, self.prior['activeSeconds'])
        kwargs['on_spawn']()
        directory.mkdir(parents=True)
        (directory / 'run.log').write_text(CAPTURE.BLACKHOLE_LOG + '\n')

    def ledger(self):
        return json.loads((self.out / 'execution-ledger.json').read_text())

    def test_remaining_schedule_carries_budget_without_preflights(self):
        with patch.object(CAPTURE.time, 'monotonic', return_value=1000), \
                patch.object(CAPTURE, 'execute', side_effect=self.execute) as execute, \
                patch.object(CAPTURE.review, 'v36_stage', return_value=self.result):
            CAPTURE.intervention(self.root, self.out)
        self.assertEqual(execute.call_count, 8)
        self.assertEqual(self.ledger()['attemptedStageIds'], list(range(41, 49)))
        self.assertEqual(self.ledger()['completedStageIds'], list(range(41, 49)))
        self.assertEqual(self.ledger()['activeSeconds'], self.prior['activeSeconds'])
        self.assertEqual(self.ledger()['unattemptedStageIds'], [])
        self.assertEqual(self.ledger()['preflightAttempts'], 0)
        self.assertEqual(self.ledger()['probeAllowanceCharged'], 16)
        self.assertEqual(self.ledger()['probeInvocationsInferred'], 16)
        self.assertIsNone(self.ledger()['individualProbeInventory'])
        for number in range(41, 49):
            stage = json.loads((self.out / f'stage-{number:02}.json').read_text())
            traced = CAPTURE.stage_spec(dict(stage, lane='T'), self.root, self.out,
                                        self.out / 'boundary-recorder.jar', stage['seal'])
            without_identity = lambda flags: [flag for flag in flags if not flag.startswith('-Dv36.')]
            self.assertEqual(without_identity(stage['jvmArgs']), without_identity(traced['jvmArgs']) +
                             ['-XX:CompileCommand=dontinline,' + CAPTURE.base.TARGET])

    def test_failed_attempt_is_charged_and_cannot_be_retried(self):
        def fail(*args, **kwargs):
            kwargs['on_spawn']()
            raise RuntimeError('synthetic failure')
        with patch.object(CAPTURE, 'execute', side_effect=fail) as execute:
            with self.assertRaisesRegex(RuntimeError, 'synthetic'):
                CAPTURE.intervention(self.root, self.out)
            with self.assertRaises(FileExistsError):
                CAPTURE.intervention(self.root, self.out)
        self.assertEqual(execute.call_count, 1)
        self.assertEqual(self.ledger()['attemptedStageIds'], [41])
        self.assertEqual(self.ledger()['completedStageIds'], [])
        self.assertEqual(self.ledger()['probeAllowanceCharged'], 2)
        self.assertEqual(self.ledger()['probeInvocationsInferred'], 0)
        self.assertEqual(self.ledger()['unattemptedStageIds'], list(range(42, 49)))

    def test_missing_autodetection_evidence_stops_remaining_stages(self):
        def no_probe_log(*args, **kwargs):
            self.execute(*args, **kwargs)
            (args[1] / 'run.log').write_text('different mode\n')
        with patch.object(CAPTURE.time, 'monotonic', return_value=1000), \
                patch.object(CAPTURE, 'execute', side_effect=no_probe_log) as execute, \
                patch.object(CAPTURE.review, 'v36_stage', return_value=self.result):
            with self.assertRaisesRegex(ValueError, 'autodetection'):
                CAPTURE.intervention(self.root, self.out)
        self.assertEqual(execute.call_count, 1)
        self.assertEqual(self.ledger()['probeInvocationsInferred'], 0)

    def test_time_and_combined_output_limits_include_prior_capture(self):
        with patch.object(CAPTURE.time, 'monotonic', return_value=1000), \
                patch.object(CAPTURE, 'size', return_value=1):
            CAPTURE.check_bounds(self.out, self.root, 0, 1100, prior_bytes=8 * CAPTURE.GIB - 1)
            for start, deadline, prior in [(-6201, 1100, 0), (0, 999, 0), (0, 1100, 8 * CAPTURE.GIB)]:
                with self.assertRaisesRegex(ValueError, 'bound exceeded'):
                    CAPTURE.check_bounds(self.out, self.root, start, deadline, prior_bytes=prior)

    def test_only_exact_probe_commands_are_allowed(self):
        common = [str(CAPTURE.JAVA), '-XX:+UnlockExperimentalVMOptions']
        tail = ['-XX:CompileCommand=blackhole,some/fake/Class.method', '-version']
        self.assertTrue(CAPTURE.probe_command(common + tail))
        self.assertTrue(CAPTURE.probe_command(common + ['-XX:CompileCommand=quiet'] + tail))
        for args in [common, common + tail + ['extra'], [str(CAPTURE.JAVA), '-jar', 'other.jar']]:
            self.assertFalse(CAPTURE.probe_command(args))

    def test_changed_prior_budget_or_manifest_is_rejected(self):
        prior = self.root / 'prior'
        (prior / 'capture').mkdir(parents=True)
        ledger = prior / 'capture/execution-ledger.json'
        ledger.write_text(json.dumps(self.prior))
        manifest = prior / 'SHA256SUMS'
        manifest.write_text(CAPTURE.base.digest(ledger) + '  capture/execution-ledger.json\n')
        with patch.object(CAPTURE, 'PRIOR_MANIFEST', CAPTURE.base.digest(manifest)):
            self.assertEqual(CAPTURE.prior_capture(prior)['activeSeconds'], self.prior['activeSeconds'])
            ledger.write_text('{}')
            with self.assertRaisesRegex(ValueError, 'evidence changed'):
                CAPTURE.prior_capture(prior)
        changed = dict(self.prior, activeSeconds=0)
        ledger.write_text(json.dumps(changed))
        manifest.write_text(CAPTURE.base.digest(ledger) + '  capture/execution-ledger.json\n')
        with patch.object(CAPTURE, 'PRIOR_MANIFEST', CAPTURE.base.digest(manifest)):
            with self.assertRaisesRegex(ValueError, 'budget mismatch'):
                CAPTURE.prior_capture(prior)
        with self.assertRaisesRegex(ValueError, 'manifest mismatch'):
            CAPTURE.prior_capture(prior)

    def test_unapproved_preparation_or_changed_seal_launches_nothing(self):
        plan = self.root / 'pending.md'
        plan.write_text('approval pending')
        with patch.object(CAPTURE, 'AMENDMENT', plan), patch.object(CAPTURE.subprocess, 'run') as run:
            with self.assertRaisesRegex(ValueError, 'approval missing'):
                CAPTURE.prepare(self.root, self.root / 'unapproved', self.root)
            run.assert_not_called()
        (self.out / 'sealed-inputs.json').write_text(json.dumps({str(plan): 'wrong'}))
        with patch.object(CAPTURE, 'execute') as execute:
            with self.assertRaisesRegex(ValueError, 'Sealed input changed'):
                CAPTURE.intervention(self.root, self.out)
            execute.assert_not_called()

    def test_directive_alone_is_not_compiler_confirmation(self):
        directive = '-XX:CompileCommand=dontinline,' + CAPTURE.base.TARGET
        self.fixture.expected['jvmArgs'].append(directive)
        command = self.fixture.command['argv']
        command[command.index('-jvmArgs') + 1] = ' '.join(self.fixture.expected['jvmArgs'])
        worker = self.fixture.worker['argv']
        worker.insert(worker.index('-cp'), directive)
        count = int(self.fixture.record['argumentCount'])
        self.fixture.record['argument.' + str(count)] = directive
        self.fixture.record['argumentCount'] = str(count + 1)
        self.fixture.write()
        trace = REVIEW.compiler(self.fixture.directory / 'compilation-pid7.xml')
        trace['vmArguments'].append(directive)
        expected = dict(self.fixture.expected, lane='I')
        with patch.object(REVIEW, 'compiler', return_value=trace):
            result = REVIEW.v36_stage(self.fixture.directory, expected)
        self.assertFalse(result['intervention']['nonInliningObserved'])
        trace['tasks'][0]['policyInlining'] = [{'callee': CAPTURE.base.TARGET, 'caller': 'caller',
                                               'decision': 'inline_fail', 'reason': 'disallowed by CompileCommand'}]
        with patch.object(REVIEW, 'compiler', return_value=trace):
            result = REVIEW.v36_stage(self.fixture.directory, expected)
        self.assertTrue(result['intervention']['nonInliningObserved'])
        self.assertFalse(result['intervention']['ordinaryHighWitness'])
        self.assertEqual(result['executingCodeIdentity'], 'unverified')
        trace['tasks'][0]['policyInlining'].append(dict(trace['tasks'][0]['policyInlining'][0], decision='inline_success'))
        with patch.object(REVIEW, 'compiler', return_value=trace):
            self.assertTrue(REVIEW.v36_stage(self.fixture.directory, expected)['intervention']['inliningAlsoObserved'])

    def test_unexpected_observed_jvm_aborts_owned_group(self):
        member = dict(self.fixture.worker, argv=[str(CAPTURE.JAVA), '-jar', 'unexpected.jar'], rss=1)
        with patch.object(CAPTURE, 'host_limits', return_value={}), \
                patch.object(CAPTURE.base, 'snapshot', return_value={}), \
                patch.object(CAPTURE, 'group_processes', return_value=[member]), \
                patch.object(CAPTURE.subprocess, 'Popen') as popen, \
                patch.object(CAPTURE.os, 'killpg') as kill:
            process = popen.return_value
            process.pid = 6
            process.poll.return_value = None
            process.wait.return_value = -9
            with self.assertRaisesRegex(ValueError, 'Unexpected observed JVM'):
                CAPTURE.execute(['synthetic'], self.out / 'bad-child', 'synthetic', 120,
                                CAPTURE.time.monotonic(), True, self.out, check_probes=True)
            kill.assert_called_once_with(6, CAPTURE.signal.SIGKILL)


if __name__ == '__main__':
    unittest.main()
