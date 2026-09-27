"""Tests for evidence bookkeeping; these do not claim native execution."""
import contextlib
import importlib.util
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("native_runner", Path(__file__).with_name("verify-v33-native.py"))
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)


class NativeRunnerTest(unittest.TestCase):
    def test_success_preserves_command_log_and_exit_status(self):
        with tempfile.TemporaryDirectory() as directory:
            out = Path(directory)
            command = [sys.executable, "-c", "print('witness')"]
            runner.run(out, "success", command, out, os.environ)
            self.assertEqual(json.loads((out / "success.command.json").read_text()), command)
            self.assertEqual((out / "success.log").read_text(), "witness\n")
            result = json.loads((out / "success.result.json").read_text())
            self.assertEqual(result["exitStatus"], 0)
            self.assertLessEqual(result["startedAt"], result["endedAt"])

    def test_failure_is_recorded_before_propagation(self):
        with tempfile.TemporaryDirectory() as directory:
            out = Path(directory)
            with self.assertRaises(subprocess.CalledProcessError):
                runner.run(out, "failure", [sys.executable, "-c", "raise SystemExit(7)"], out, os.environ)
            self.assertEqual(json.loads((out / "failure.result.json").read_text())["exitStatus"], 7)

    def test_timeout_is_not_reported_as_success(self):
        with tempfile.TemporaryDirectory() as directory:
            out = Path(directory)
            with self.assertRaises(subprocess.TimeoutExpired):
                runner.run(out, "timeout", [sys.executable, "-c", "import time; time.sleep(60)"],
                           out, os.environ, timeout=0.1)
            result = json.loads((out / "timeout.result.json").read_text())
            self.assertIsNone(result["exitStatus"])
            self.assertIn("timed out", result["error"])

    def test_missing_executable_has_failure_evidence(self):
        with tempfile.TemporaryDirectory() as directory:
            out = Path(directory)
            with self.assertRaises(FileNotFoundError):
                runner.run(out, "missing", [str(out / "missing")], out, os.environ)
            result = json.loads((out / "missing.result.json").read_text())
            self.assertIsNone(result["exitStatus"])
            self.assertIn("error", result)

    def test_refuses_sudo_before_creating_directories(self):
        with patch.object(os, "geteuid", return_value=0), patch.object(sys, "argv", ["runner"]):
            with contextlib.redirect_stderr(io.StringIO()) as errors:
                with self.assertRaises(SystemExit) as caught:
                    runner.main()
            self.assertEqual(caught.exception.code, 2)
            self.assertIn("not sudo", errors.getvalue())


if __name__ == "__main__":
    unittest.main()
