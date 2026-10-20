"""Frozen APK comparison on public fixtures only. Preserves installation data and old reports."""

import argparse
import hashlib
import json
import os
import re
import subprocess
from datetime import datetime, timezone
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "local-artifacts/p2-integration"
PACKAGE = "dev.example.ebookviewer.p0"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--label", choices=("baseline", "final"), required=True)
    parser.add_argument("--mode", choices=("normal", "fling", "zoom32", "frames", "long"), required=True)
    parser.add_argument("--complex", action="store_true")
    args = parser.parse_args()
    assert not args.complex or args.mode == "normal"
    OUTPUT.mkdir(parents=True, exist_ok=True)
    adb = str(Path(os.environ["ANDROID_HOME"]) / "platform-tools/adb.exe")

    def call(arguments, timeout=30):
        try:
            result = subprocess.run([adb, *arguments], capture_output=True, text=True,
                                    encoding="utf-8", errors="replace", timeout=timeout)
        except subprocess.TimeoutExpired:
            raise RuntimeError("ADB deadline exceeded; workload is not a pass") from None
        if result.returncode:
            raise RuntimeError("ADB operation failed; device output withheld")
        return result.stdout

    devices = [x.split()[0] for x in call(["devices"]).splitlines()[1:]
               if len(x.split()) == 2 and x.split()[1] == "device"]
    if len(devices) != 1:
        raise RuntimeError("Exactly one authorized device is required")
    prefix = ["-s", devices[0]]

    def guard():
        memory = re.search(r"(?m)^MemAvailable:\s+(\d+)", call(prefix + ["shell", "cat", "/proc/meminfo"]))
        if not memory or int(memory.group(1)) < 2 * 1024 * 1024:
            raise RuntimeError("2 GiB available memory required; workload not started")
        power = call(prefix + ["shell", "dumpsys", "power"])
        if not re.search(r"\bmWakefulness=Awake\b", power):
            raise RuntimeError("Wake and unlock device before measurement")
        policy = call(prefix + ["shell", "dumpsys", "window", "policy"])
        if re.search(r"(?:showing|isStatusBarKeyguard|mShowingLockscreen)=true", policy):
            raise RuntimeError("Unlock device before measurement")
        thermal = re.search(r"Thermal Status:\s*(\d+)", call(prefix + ["shell", "dumpsys", "thermalservice"]))
        if not thermal or int(thermal.group(1)) > 1:
            raise RuntimeError("Thermal status unavailable or above LIGHT; workload not started")
        return int(thermal.group(1))

    before_thermal = guard()
    started_at = datetime.now(timezone.utc).isoformat()
    battery = call(prefix + ["shell", "dumpsys", "battery"])
    battery_conditions = {}
    for field in ("status", "level", "temperature"):
        value = re.search(r"(?m)^\s*" + field + r":\s*(\d+)\s*$", battery)
        battery_conditions[field] = int(value.group(1)) if value else None
    app = OUTPUT / f"{args.label}-app.apk"
    test = OUTPUT / "common-test.apk"
    if not app.is_file() or not test.is_file():
        raise RuntimeError("Freeze baseline/final app APKs and common profile test APK first")
    hashes = {}
    for name, path in ((PACKAGE, app), (PACKAGE + ".test", test)):
        expected = hashlib.sha256(path.read_bytes()).hexdigest()
        paths = re.findall(r"(?m)^package:(/data/app/[A-Za-z0-9_~+=./-]+/base\.apk)\s*$",
                           call(prefix + ["shell", "pm", "path", name]))
        actual = call(prefix + ["shell", "sha256sum", paths[0]]).split()[0] if len(paths) == 1 else None
        if actual != expected:
            print(f"Installing {args.label if name == PACKAGE else 'common test'} APK; data preserved", flush=True)
            # Package commits outlived the former 90s client deadline on this device.
            # Do not launch instrumentation while a slow replacement is still pending.
            call(prefix + ["install", "--no-streaming", "-r", "-t", str(path)], 300)
        paths = re.findall(r"(?m)^package:(/data/app/[A-Za-z0-9_~+=./-]+/base\.apk)\s*$",
                           call(prefix + ["shell", "pm", "path", name]))
        if len(paths) != 1 or call(prefix + ["shell", "sha256sum", paths[0]]).split()[0] != expected:
            raise RuntimeError("Installed APK hash mismatch; workload not permitted")
        hashes["app" if name == PACKAGE else "test"] = expected
    guard()
    key = f"{args.label}-{args.mode}" + ("-complex" if args.complex else "")
    arguments = ["shell", "am", "instrument", "-w"]
    frames = args.mode in ("frames", "long")
    if frames:
        arguments += ["-e", "probe", "true", "-e", "seconds", "600" if args.mode == "long" else "30",
                      "-e", "runs", "1" if args.mode == "long" else "3"]
        if args.label == "final":
            arguments += ["-e", "autoBackground", "true"]
    else:
        arguments += ["-e", "suite", "sharpness", "-e", "runs", "3",
                      "-e", "sharpnessMode", args.mode]
        if args.complex:
            arguments += ["-e", "complexSharpness", "true"]
    arguments += [PACKAGE + ".test/dev.example.ebookviewer.P0Instrumentation"]
    print(f"Measurement started: {key}", flush=True)
    try:
        result = call(prefix + arguments, 780 if args.mode == "long" else 1200)
    except RuntimeError:
        call(prefix + ["shell", "am", "force-stop", PACKAGE])
        raise
    (OUTPUT / f"{key}-instrumentation.txt").write_text(result, encoding="utf-8")
    field = "p0Report" if frames else "sharpnessReport"
    match = re.search(r"(?m)^INSTRUMENTATION_STATUS: " + field + r"=(.+)$", result)
    if "P0 tests=1, failures=0" not in result or not match:
        raise RuntimeError("Probe failed; inspect public-fixture instrumentation report")
    report = json.loads(match.group(1))
    assert len(report["runs"]) == (1 if args.mode == "long" else (3 if frames or args.complex else 9))
    assert all(not run.get("debuggable", report.get("debuggable", True)) for run in report["runs"])
    report["apkSha256"] = hashes["app"]
    report["testApkSha256"] = hashes["test"]
    report["thermalStatusBefore"] = before_thermal
    after_thermal = re.search(r"Thermal Status:\s*(\d+)", call(prefix + ["shell", "dumpsys", "thermalservice"]))
    report["thermalStatusAfter"] = int(after_thermal.group(1)) if after_thermal else None
    report["startedAtUtc"] = started_at
    report["finishedAtUtc"] = datetime.now(timezone.utc).isoformat()
    report["batteryBefore"] = battery_conditions
    report["fontScale"] = call(prefix + ["shell", "settings", "get", "system", "font_scale"]).strip()
    (OUTPUT / f"{key}.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"label": args.label, "mode": args.mode, "complex": args.complex,
                      "runs": len(report["runs"]), "appSha256": hashes["app"]}))


if __name__ == "__main__":
    main()
