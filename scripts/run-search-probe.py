"""Host deadline/cleanup for the separate synthetic-only Android probe APK."""

import argparse
import json
import re
import subprocess
from pathlib import Path


PACKAGE = "dev.example.ebookviewer.searchprobe"
READER = "dev.example.ebookviewer.p0"
ROOT = Path(__file__).resolve().parents[1]
MIN_AVAILABLE_KB = 2176 * 1024
EXPECTED_GUARDS = [
    ("ping", "OK"), ("stall", "TIME_BUDGET"), ("cancel", "CANCELLED"),
    ("allocate", "RESIDENT_BUDGET"), ("die", "PROCESS_DIED"),
    ("search", "INPUT_LIMIT"), ("ping", "OK"),
]


class ProbeError(RuntimeError):
    def __init__(self, message: str, report: dict | None = None):
        super().__init__(message)
        self.report = report


class AdbTimeout(ProbeError):
    pass


class Adb:
    def __init__(self, executable: str, serial: str):
        self.prefix = [executable, "-s", serial]

    def call(self, *arguments: str, timeout: int = 10, allow_missing: bool = False) -> str:
        try:
            result = subprocess.run(
                self.prefix + list(arguments), capture_output=True, text=True,
                encoding="utf-8", errors="replace", timeout=timeout,
            )
        except subprocess.TimeoutExpired:
            raise AdbTimeout("ADB deadline exceeded") from None
        except OSError:
            raise ProbeError("ADB executable could not start") from None
        missing_process = allow_missing and result.returncode == 1 and not result.stderr.strip()
        if result.returncode and not missing_process:
            # Do not echo the command, device identifier or arbitrary Android output.
            raise ProbeError("ADB operation failed")
        return result.stdout

    def reader_process(self) -> str:
        return self.call("shell", "pidof", READER, allow_missing=True).strip()

    def stop(self) -> None:
        self.call("shell", "am", "force-stop", PACKAGE, timeout=5)

    def available(self) -> int:
        text = self.call("shell", "cat", "/proc/meminfo")
        match = re.search(r"(?m)^MemAvailable:\s+(\d+)\s+kB", text)
        if not match:
            raise ProbeError("System memory counter unavailable")
        return int(match.group(1))

    def instrument(self, suite: str, fixture: str, mode: str) -> dict:
        arguments = ["shell", "am", "instrument", "-w", "-e", "suite", suite]
        if suite == "search":
            arguments += ["-e", "fixture", fixture, "-e", "mode", mode]
        arguments.append(f"{PACKAGE}/{PACKAGE}.ProbeInstrumentation")
        try:
            text = self.call(*arguments, timeout=40 if suite == "guards" else 25)
        except AdbTimeout:
            try:
                self.stop()
            except ProbeError:
                raise ProbeError("Host deadline exceeded; diagnostic cleanup unconfirmed") from None
            raise ProbeError("Host deadline exceeded; probe package force-stopped") from None
        match = re.search(r"(?m)^INSTRUMENTATION_RESULT: p2ProbeReport=(.+)$", text)
        passed = re.search(r"(?m)^INSTRUMENTATION_RESULT: p2ProbePassed=true\s*$", text)
        if not match:
            raise ProbeError("Probe returned no structured report")
        try:
            report = json.loads(match.group(1))
        except ValueError:
            raise ProbeError("Probe report could not be decoded") from None
        if not isinstance(report, dict):
            raise ProbeError("Probe report is not an object")
        if not passed:
            raise ProbeError("Probe failed: " + report.get("failureClass", "unknown"), report)
        return report


def execute(adb: Adb, suite: str, fixture: str, mode: str) -> dict:
    before = adb.reader_process()
    try:
        if adb.available() < MIN_AVAILABLE_KB:
            raise ProbeError("Not enough available system memory; probe not started")
        guards = adb.instrument("guards", fixture, mode)
        cases = guards.get("cases", [])
        if len(cases) != len(EXPECTED_GUARDS) or any(
            (case.get("operation"), case.get("outcome")) != expected
            or case.get("childTerminated") is not True or case.get("isolatedUid") is not True
            for case, expected in zip(cases, EXPECTED_GUARDS)
        ):
            raise ProbeError("Incomplete guard suite; no PDF probe permitted")
        if before != adb.reader_process():
            raise ProbeError("Reader process state changed; no PDF probe permitted")
        result = {"guards": guards, "search": None, "readerWasRunning": bool(before)}
        if suite == "search":
            if adb.available() < MIN_AVAILABLE_KB:
                raise ProbeError("Memory reserve fell; no PDF probe permitted")
            result["search"] = adb.instrument("search", fixture, mode)
        return result
    finally:
        adb.stop()
        if before != adb.reader_process():
            raise ProbeError("Reader process state changed; stop further diagnostics")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", required=True)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--cleanup-only", action="store_true")
    parser.add_argument("--install-apk", action="store_true")
    parser.add_argument("--suite", choices=("guards", "search"), default="guards")
    parser.add_argument("--fixture", choices=("text-1000.pdf", "images-430.pdf", "images-1000.pdf", "dense-100.pdf"),
                        default="text-1000.pdf")
    parser.add_argument("--mode", choices=("single-keep", "single-reopen", "dual-keep", "dual-reopen"),
                        default="single-keep")
    arguments = parser.parse_args()
    adb = Adb(arguments.adb, arguments.serial)
    if arguments.cleanup_only:
        adb.stop()
        print(json.dumps({"diagnosticPackageStopped": True}))
        return
    if arguments.install_apk:
        apk = ROOT / "searchprobe/build/outputs/apk/debug/searchprobe-debug.apk"
        if not apk.is_file():
            raise ProbeError("Build the separate diagnostic APK first")
        adb.call("install", "--no-streaming", "-r", "-t", str(apk), timeout=60)
    result = execute(adb, arguments.suite, arguments.fixture, arguments.mode)
    output = ROOT / "local-artifacts/search-isolation"
    output.mkdir(parents=True, exist_ok=True)
    name = "guards" if arguments.suite == "guards" else f"{arguments.fixture}-{arguments.mode}"
    (output / f"{name}.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    summary = {"guardCasesPassed": len(result["guards"]["cases"]),
               "readerWasRunning": result["readerWasRunning"], "readerProcessUnchanged": True}
    if result["search"]:
        case = result["search"]["cases"][0]
        samples = case["samples"]
        summary.update({"fixture": arguments.fixture, "mode": arguments.mode,
                        "outcome": case["outcome"], "searchComplete": case["outcome"] == "OK",
                        "pages": samples[-1]["pages"] if samples else 0,
                        "peakSampleRssKb": max((s["rssKb"] for s in samples), default=0),
                        "elapsedMs": case["elapsedMs"]})
    print(json.dumps(summary))


if __name__ == "__main__":
    try:
        main()
    except ProbeError as failure:
        if failure.report:
            output = ROOT / "local-artifacts/search-isolation"
            output.mkdir(parents=True, exist_ok=True)
            (output / "last-failure.json").write_text(
                json.dumps(failure.report, indent=2) + "\n", encoding="utf-8"
            )
        print(json.dumps({"passed": False, "reason": str(failure)}))
        raise SystemExit(1)
