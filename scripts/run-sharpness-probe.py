"""Install/run the synthetic-only probe. No private document or device identifier is printed."""

import argparse
import hashlib
import json
import os
import re
import subprocess
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "dev.example.ebookviewer.p0"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--runs", type=int, choices=(1, 3), default=3)
    parser.add_argument("--label", choices=("baseline", "page-reuse"), default="baseline")
    args = parser.parse_args()
    adb = str(Path(os.environ["ANDROID_HOME"]) / "platform-tools/adb.exe")

    def call(arguments, timeout=30):
        try:
            result = subprocess.run([adb, *arguments], capture_output=True, text=True,
                                    encoding="utf-8", errors="replace", timeout=timeout)
        except subprocess.TimeoutExpired:
            raise RuntimeError("ADB deadline exceeded") from None
        if result.returncode:
            raise RuntimeError("ADB operation failed; output withheld")
        return result.stdout

    devices = [s.split()[0] for s in call(["devices"]).splitlines()[1:]
               if len(s.split()) == 2 and s.split()[1] == "device"]
    if len(devices) != 1:
        raise RuntimeError("Exactly one authorized Android device required")
    prefix = ["-s", devices[0]]
    memory = call(prefix + ["shell", "cat", "/proc/meminfo"])
    available = re.search(r"(?m)^MemAvailable:\s+(\d+)", memory)
    if not available or int(available.group(1)) < 2 * 1024 * 1024:
        raise RuntimeError("2 GiB available memory required before installing/running this probe")
    policy = call(prefix + ["shell", "dumpsys", "window", "policy"])
    if re.search(r"(?:showing|isStatusBarKeyguard|mShowingLockscreen)=true", policy):
        raise RuntimeError("Unlock the device before running this UI probe")
    apk = ROOT / "app/build/outputs/apk/profile/app-profile.apk"
    test_apk = ROOT / "app/build/outputs/apk/androidTest/profile/app-profile-androidTest.apk"
    directory = ROOT / "local-artifacts/sharpness"
    directory.mkdir(parents=True, exist_ok=True)
    snapshots = {}
    for kind, path in (("app", apk), ("test", test_apk)):
        if not path.is_file():
            raise RuntimeError("Build both profile APKs first")
        snapshots[kind] = path.read_bytes()
        frozen = directory / f"{args.label}-{kind}.apk"
        frozen.write_bytes(snapshots[kind])
        call(prefix + ["install", "--no-streaming", "-r", "-t", str(frozen)], timeout=60)
    memory = call(prefix + ["shell", "cat", "/proc/meminfo"])
    available = re.search(r"(?m)^MemAvailable:\s+(\d+)", memory)
    if not available or int(available.group(1)) < 2 * 1024 * 1024:
        raise RuntimeError("Memory reserve fell after install; probe not started")
    try:
        result = call(prefix + ["shell", "am", "instrument", "-w", "-e", "suite", "sharpness",
                                "-e", "runs", str(args.runs),
                                PACKAGE + ".test/dev.example.ebookviewer.P0Instrumentation"], timeout=600)
    except RuntimeError:
        # Only this synthetic probe's target process; never clear app data or touch other apps.
        call(prefix + ["shell", "am", "force-stop", PACKAGE], timeout=10)
        raise
    # This runner only launches built-in synthetic fixtures; never request/save raw device logs.
    (directory / f"{args.label}-profile-instrumentation.txt").write_text(result, encoding="utf-8")
    match = re.search(r"(?m)^INSTRUMENTATION_STATUS: sharpnessReport=(.+)$", result)
    if "P0 tests=1, failures=0" not in result or not match:
        raise RuntimeError("Sharpness probe failed; see synthetic instrumentation result")
    report = json.loads(match.group(1))
    if len(report["runs"]) != 3 * args.runs:
        raise RuntimeError("Incomplete fixture/run count")
    report["apkSha256"] = hashlib.sha256(snapshots["app"]).hexdigest()
    report["testApkSha256"] = hashlib.sha256(snapshots["test"]).hexdigest()
    (directory / f"{args.label}-profile.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"passed": True, "runs": len(report["runs"]), "apkSha256": report["apkSha256"]}))


if __name__ == "__main__":
    main()
