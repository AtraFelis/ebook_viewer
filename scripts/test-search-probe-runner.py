"""Host safety tests. No ADB process or Android device is used."""

import importlib.util
import subprocess
import unittest
from pathlib import Path
from unittest.mock import Mock, patch


spec = importlib.util.spec_from_file_location("probe_runner", Path(__file__).with_name("run-search-probe.py"))
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)


class ProbeRunnerTest(unittest.TestCase):
    def fake(self):
        adb = Mock()
        adb.reader_process.return_value = "unchanged-process"
        adb.available.return_value = runner.MIN_AVAILABLE_KB + 1024
        adb.instrument.return_value = {"cases": [
            {"operation": operation, "outcome": outcome, "childTerminated": True, "isolatedUid": True,
             "mainBlocked": True, "parentUnbound": True, "workerBlocked": True, "recoveryMs": 100}
            for operation, outcome in runner.EXPECTED_GUARDS
        ]}
        return adb

    def test_low_memory_never_starts_instrumentation(self):
        adb = self.fake()
        adb.available.return_value = runner.MIN_AVAILABLE_KB - 1
        with self.assertRaises(runner.ProbeError):
            runner.execute(adb, "search", "text-1000.pdf", "single-keep")
        adb.instrument.assert_not_called()
        adb.stop.assert_called_once()

    def test_cleanup_failures_preserve_report_and_attempt_both_checks(self):
        for stop_fails, reader_fails in ((True, False), (False, True), (True, True)):
            with self.subTest(stop=stop_fails, reader=reader_fails):
                adb = self.fake()
                report = {"failureClass": "SyntheticFailure"}
                adb.instrument.side_effect = runner.ProbeError("Original failure", report)
                if stop_fails:
                    adb.stop.side_effect = runner.ProbeError("Synthetic stop failure")
                if reader_fails:
                    adb.reader_process.side_effect = ["unchanged-process", runner.ProbeError("Synthetic transport failure")]
                with self.assertRaises(runner.ProbeError) as failure:
                    runner.execute(adb, "guards", "text-1000.pdf", "single-keep")
                self.assertIs(report, failure.exception.report)
                self.assertIn("Original failure", str(failure.exception))
                self.assertEqual(2, adb.reader_process.call_count)
                adb.stop.assert_called_once()

    def test_reader_change_during_cleanup_preserves_original_report(self):
        adb = self.fake()
        report = {"failureClass": "SyntheticFailure"}
        adb.instrument.side_effect = runner.ProbeError("Original failure", report)
        adb.reader_process.side_effect = ["unchanged-process", "changed-process"]
        with self.assertRaises(runner.ProbeError) as failure:
            runner.execute(adb, "guards", "text-1000.pdf", "single-keep")
        self.assertIs(report, failure.exception.report)
        self.assertIn("Reader process state changed", str(failure.exception))

    def test_interruption_still_cleans_up_and_preserves_interrupt(self):
        adb = self.fake()
        adb.instrument.side_effect = KeyboardInterrupt()
        with self.assertRaises(KeyboardInterrupt):
            runner.execute(adb, "guards", "text-1000.pdf", "single-keep")
        adb.stop.assert_called_once()
        self.assertEqual(2, adb.reader_process.call_count)

    def test_reader_not_running_is_not_reported_as_live_survival(self):
        adb = self.fake()
        adb.reader_process.return_value = ""
        result = runner.execute(adb, "guards", "text-1000.pdf", "single-keep")
        self.assertFalse(result["readerWasRunning"])

    def test_install_refuses_before_adb_install_when_reserve_is_low(self):
        adb = runner.Adb("adb", "synthetic-device")
        with patch.object(adb, "available", return_value=runner.MIN_AVAILABLE_KB - 1), \
                patch.object(adb, "call") as call:
            with self.assertRaises(runner.ProbeError):
                adb.install(Path("synthetic.apk"))
        call.assert_not_called()

    def test_install_uses_only_test_apk_and_fixed_deadline(self):
        adb = runner.Adb("adb", "synthetic-device")
        with patch.object(adb, "available", return_value=runner.MIN_AVAILABLE_KB), \
                patch.object(adb, "call") as call:
            adb.install(Path("synthetic.apk"))
        call.assert_called_once_with("install", "--no-streaming", "-r", "-t", "synthetic.apk", timeout=60)

    def test_incomplete_guards_never_start_pdf(self):
        adb = self.fake()
        adb.instrument.return_value = {"cases": []}
        with self.assertRaises(runner.ProbeError):
            runner.execute(adb, "search", "text-1000.pdf", "single-keep")
        self.assertEqual(1, adb.instrument.call_count)
        adb.stop.assert_called_once()

    def test_reader_change_prevents_pdf_and_is_reported(self):
        adb = self.fake()
        adb.reader_process.side_effect = ["original", "changed", "changed"]
        with self.assertRaises(runner.ProbeError):
            runner.execute(adb, "search", "text-1000.pdf", "single-keep")
        self.assertEqual(1, adb.instrument.call_count)

    def test_memory_drop_after_guards_prevents_pdf(self):
        adb = self.fake()
        adb.available.side_effect = [runner.MIN_AVAILABLE_KB + 1, runner.MIN_AVAILABLE_KB - 1]
        with self.assertRaises(runner.ProbeError):
            runner.execute(adb, "search", "text-1000.pdf", "single-keep")
        self.assertEqual(1, adb.instrument.call_count)

    def test_budget_abort_is_not_converted_to_search_success(self):
        adb = self.fake()
        aborted = {"cases": [{"outcome": "RESIDENT_BUDGET", "samples": []}]}
        adb.instrument.side_effect = [adb.instrument.return_value, aborted]
        result = runner.execute(adb, "search", "text-1000.pdf", "single-keep")
        self.assertEqual("RESIDENT_BUDGET", result["search"]["cases"][0]["outcome"])
        adb.stop.assert_called_once()

    def test_host_timeout_force_stops_only_diagnostic_package(self):
        commands = []

        def process(arguments, **kwargs):
            commands.append(arguments)
            if "instrument" in arguments:
                raise subprocess.TimeoutExpired(arguments, kwargs["timeout"])
            return subprocess.CompletedProcess(arguments, 0, "", "")

        with patch.object(subprocess, "run", side_effect=process):
            with self.assertRaises(runner.ProbeError):
                runner.Adb("adb", "synthetic-device").instrument("guards", "text-1000.pdf", "single-keep")
        self.assertEqual(["shell", "am", "force-stop", runner.PACKAGE], commands[-1][3:])
        self.assertTrue(all(runner.READER not in command for command in commands))

    def test_wrong_guard_outcome_blocks_pdf_even_when_case_count_matches(self):
        adb = self.fake()
        adb.instrument.return_value["cases"][1]["outcome"] = "OK"
        with self.assertRaises(runner.ProbeError):
            runner.execute(adb, "search", "text-1000.pdf", "single-keep")
        self.assertEqual(1, adb.instrument.call_count)

    def test_parent_recovery_requires_blocked_worker_and_bounded_binder_death(self):
        for field, value in (("workerBlocked", False), ("parentUnbound", False),
                             ("recoveryMs", 2001), ("recoveryMs", -1), ("recoveryMs", None)):
            with self.subTest(field=field, value=value):
                adb = self.fake()
                case = next(c for c in adb.instrument.return_value["cases"] if c["operation"] == "abandon")
                case[field] = value
                with self.assertRaises(runner.ProbeError):
                    runner.execute(adb, "search", "images-430.pdf", "single-keep")
                self.assertEqual(1, adb.instrument.call_count)

    def test_main_thread_guard_requires_actual_blockage_confirmation(self):
        adb = self.fake()
        case = next(c for c in adb.instrument.return_value["cases"] if c["operation"] == "stall-main")
        case["mainBlocked"] = False
        with self.assertRaises(runner.ProbeError):
            runner.execute(adb, "search", "images-430.pdf", "single-keep")
        self.assertEqual(1, adb.instrument.call_count)

    def test_failed_instrumentation_cannot_pass_on_result_json_alone(self):
        output = 'INSTRUMENTATION_RESULT: p2ProbeReport={"failureClass":"AssertionError"}\n'
        output += 'INSTRUMENTATION_RESULT: p2ProbePassed=false\n'
        with patch.object(subprocess, "run", return_value=subprocess.CompletedProcess([], 0, output, "")):
            with self.assertRaises(runner.ProbeError):
                runner.Adb("adb", "synthetic-device").instrument("guards", "text-1000.pdf", "single-keep")

    def test_cleanup_timeout_does_not_expose_device_identifier(self):
        with patch.object(subprocess, "run", side_effect=subprocess.TimeoutExpired(["synthetic-device"], 5)):
            with self.assertRaises(runner.ProbeError) as failure:
                runner.Adb("adb", "synthetic-device").stop()
        self.assertNotIn("synthetic-device", str(failure.exception))

    def test_transport_failure_is_not_a_missing_reader_process(self):
        output = subprocess.CompletedProcess([], 1, "", "device synthetic-device unavailable")
        with patch.object(subprocess, "run", return_value=output):
            with self.assertRaises(runner.ProbeError):
                runner.Adb("adb", "synthetic-device").reader_process()

    def test_pidof_with_no_process_can_return_empty_without_error(self):
        with patch.object(subprocess, "run", return_value=subprocess.CompletedProcess([], 1, "", "")):
            self.assertEqual("", runner.Adb("adb", "synthetic-device").reader_process())


if __name__ == "__main__":
    unittest.main()
