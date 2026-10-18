"""Compare saved synthetic probe APKs using the existing 30s x 3 FrameMetrics test."""

import argparse
import hashlib
import json
import os
import re
import subprocess
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--label", choices=("baseline", "page-reuse"), required=True)
    label = parser.parse_args().label
    root = Path(__file__).resolve().parents[1]
    output = root / "local-artifacts/sharpness"
    adb = str(Path(os.environ["ANDROID_HOME"]) / "platform-tools/adb.exe")

    def call(args, timeout=15):
        try:
            result = subprocess.run([adb, *args], capture_output=True, text=True,
                                    encoding="utf-8", errors="replace", timeout=timeout)
        except subprocess.TimeoutExpired:
            raise RuntimeError("Frame probe ADB deadline exceeded") from None
        if result.returncode:
            raise RuntimeError("Frame probe ADB operation failed; output withheld")
        return result.stdout

    devices = [s.split()[0] for s in call(["devices"]).splitlines()[1:]
               if len(s.split()) == 2 and s.split()[1] == "device"]
    if len(devices) != 1:
        raise RuntimeError("One authorized device required")
    prefix = ["-s", devices[0]]
    package = "dev.example.ebookviewer.p0"

    def installed_sha(name):
        paths = re.findall(r"(?m)^package:(/data/app/[A-Za-z0-9_~+=./-]+/base\.apk)\s*$",
                           call(prefix + ["shell", "pm", "path", name]))
        if len(paths) != 1:
            return None
        match = re.match(r"[0-9a-f]{64}", call(prefix + ["shell", "sha256sum", paths[0]]))
        return match.group(0) if match else None

    hashes = {}
    for kind, name, path in (
        ("app", package, output / f"{label}-app.apk"),
        ("test", package + ".test", root / "app/build/outputs/apk/androidTest/profile/app-profile-androidTest.apk"),
    ):
        expected = hashlib.sha256(path.read_bytes()).hexdigest()
        if installed_sha(name) != expected:
            try:
                call(prefix + ["install", "--no-streaming", "-r", "-t", str(path)], 60)
            except RuntimeError:
                # A lost install acknowledgement is not proof of failure or success: verify bytes.
                pass
        if installed_sha(name) != expected:
            raise RuntimeError("Installed APK hash not verified; no frame workload permitted")
        hashes[kind] = expected
    policy = call(prefix + ["shell", "dumpsys", "window", "policy"])
    if re.search(r"(?:showing|isStatusBarKeyguard|mShowingLockscreen)=true", policy):
        raise RuntimeError("Unlock the device before UI measurement")
    memory = call(prefix + ["shell", "cat", "/proc/meminfo"])
    available = re.search(r"(?m)^MemAvailable:\s+(\d+)", memory)
    if not available or int(available.group(1)) < 2 * 1024 * 1024:
        raise RuntimeError("Frame memory reserve not met; no workload started")
    thermal = re.search(r"Thermal Status:\s*(\d+)", call(prefix + ["shell", "dumpsys", "thermalservice"]))
    try:
        result = call(prefix + ["shell", "am", "instrument", "-w", "-e", "probe", "true",
                                "-e", "seconds", "30", "-e", "runs", "3",
                                package + ".test/dev.example.ebookviewer.P0Instrumentation"], 300)
    except RuntimeError:
        call(prefix + ["shell", "am", "force-stop", package], 10)
        raise
    (output / f"{label}-frames-instrumentation.txt").write_text(result, encoding="utf-8")
    match = re.search(r"(?m)^INSTRUMENTATION_STATUS: p0Report=(.+)$", result)
    if "P0 tests=1, failures=0" not in result or not match:
        raise RuntimeError("Frame probe failed; see synthetic instrumentation result")
    report = json.loads(match.group(1))
    assert len(report["runs"]) == 3, "Incomplete frame repetitions"
    report.update({"apkSha256": hashes["app"], "testApkSha256": hashes["test"],
                   "thermalStatusBefore": int(thermal.group(1)) if thermal else None})
    (output / f"{label}-frames.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"label": label, "p95Ms": [x["p95Ms"] for x in report["runs"]],
                      "p99Ms": [x["p99Ms"] for x in report["runs"]]}))


if __name__ == "__main__":
    main()
