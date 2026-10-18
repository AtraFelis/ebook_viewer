"""Validate/compare synthetic onDraw results, retaining censored/timeout samples."""

import json
import math
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1] / "local-artifacts/sharpness"


def summarize(label):
    report = json.loads((ROOT / f"{label}-profile.json").read_text(encoding="utf-8"))
    groups = {}
    for run in report["runs"]:
        for scenario in run["scenarios"]:
            key = run["fixture"] + "/" + scenario["direction"]
            group = groups.setdefault(key, {"conditions": [], "samples": [], "runs": 0})
            assert len(scenario["samples"]) == 30, "Incomplete sample count"
            group["conditions"].append([run[x] for x in ("fixtureSha256", "width", "height", "initialZoom", "refreshHz")])
            group["samples"].extend(scenario["samples"])
            group["runs"] += 1
    for key, group in groups.items():
        assert all(x == group["conditions"][0] for x in group["conditions"]), "Conditions changed: " + key
        values = sorted(x["sharp95Ms"] if x["sharp95Ms"] is not None else math.inf for x in group["samples"])
        p95 = values[math.ceil(len(values) * .95) - 1]
        group["p95Censored"] = not math.isfinite(p95)
        group["p95Ms"] = p95 if math.isfinite(p95) else None
        group["timeouts"] = sum(x["timeout"] for x in group["samples"])
        group["count"] = len(group.pop("samples"))
        group["conditions"] = group["conditions"][0]
    return groups


def main():
    baseline = summarize("baseline")
    output = {"baseline": baseline}
    if (ROOT / "page-reuse-profile.json").exists():
        improved = summarize("page-reuse")
        assert baseline.keys() == improved.keys()
        for key, group in improved.items():
            before = baseline[key]
            assert before["conditions"] == group["conditions"], "Before/after conditions changed: " + key
            assert before["runs"] == group["runs"] == 3, "Three repetitions required for comparison"
            old, new = before["p95Ms"], group["p95Ms"]
            # Either <=500 or a 30% gain; already-fast baseline stays <=500.
            if old is not None and old > 500:
                target = max(500.0, old * .7)
            else:
                target = 500.0
            group["targetMs"] = target
            group["normalScrollTargetMet"] = new is not None and new <= target and group["timeouts"] <= before["timeouts"]
        output["pageReuse"] = improved
    (ROOT / "comparison.json").write_text(json.dumps(output, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(output))
    if "pageReuse" in output and not all(x["normalScrollTargetMet"] for x in output["pageReuse"].values()):
        raise SystemExit("Normal-scroll comparison target not met; results retained")


if __name__ == "__main__":
    main()
