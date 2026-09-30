"""Opt-in live acceptance and sensitivity checks (Python 3 standard library).

Run against a running WienScore instance. No expected real-world grade is assumed.
Evidence includes public example addresses, never arbitrary user input or credentials.
"""
import argparse
from datetime import datetime, timezone
import json
import math
from pathlib import Path
import urllib.error
import urllib.request

ADDRESSES = [
    "Hoher Markt 1, 1010 Wien",
    "Marchfeldstraße 10, 1200 Wien",
    "Karlsplatz 6, 1010 Wien",
    "Hüttelbergstraße 26, 1140 Wien",
    "Wagramer Straße 22, 1220 Wien",
    "Am Himmel 115, 1190 Wien",
]
WEIGHTS = {"STUDENT": [.25, .40, .10, .25], "SENIOR": [.35, .25, .35, .05]}
LIMITS = [1000, 800, 1000, 2000]
CATEGORIES = ["Parks", "Public transport stops", "Pharmacies", "Municipal libraries"]


def grade(score):
    return 1 + sum(score < cutoff for cutoff in (80, 60, 40, 20))


def total(distances, weights, scale=1):
    return sum(100 * max(0, 1 - d / (limit * scale)) * w
               for d, limit, w in zip(distances, LIMITS, weights))


def request(base, address, profile):
    body = json.dumps({"address": address, "profile": profile}).encode("utf-8")
    req = urllib.request.Request(base + "/api/assessments", data=body,
                                 headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=120) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        return error.code, json.load(error)


def run(base):
    evidence = {"checkedAt": datetime.now(timezone.utc).isoformat(),
                "modelVersion": "profiles-draft-v1", "addresses": [], "inputChecks": []}
    for address in ADDRESSES:
        row = {"input": address, "responses": {}, "sensitivity": {}}
        for profile, weights in WEIGHTS.items():
            status, result = request(base, address, profile)
            assert status == 200, (address, status, result)
            assert result["status"] == "OK", (address, result["status"], result["message"])
            assert result["modelVersion"] == evidence["modelVersion"]
            factors = result["factors"]
            assert len(factors) == 4 and len(result["sources"]) == 5
            assert [f["category"] for f in factors] == CATEGORIES
            assert [f["weight"] for f in factors] == weights
            assert [f["distanceLimitMetres"] for f in factors] == LIMITS
            distances = [f["distanceMetres"] for f in factors]
            assert all(math.isfinite(d) and d >= 0 for d in distances)
            score = total(distances, weights)
            assert math.isclose(score, result["score"], abs_tol=1e-9)
            assert grade(score) == result["grade"]
            row["responses"][profile] = result
            scenarios = []
            for label, variant_weights, scale in [
                ("distance limits -20%", weights, .8),
                ("distance limits +20%", weights, 1.2),
                ("equal weights", [.25] * 4, 1),
            ]:
                variant = total(distances, variant_weights, scale)
                scenarios.append({"scenario": label, "score": variant, "grade": grade(variant)})
            # Sensitivity only: change one weight, renormalize all four. Production
            # never reweights missing categories. These scenarios use complete data.
            for i in range(4):
                for multiplier in (.8, 1.2):
                    changed = weights.copy()
                    changed[i] *= multiplier
                    changed = [w / sum(changed) for w in changed]
                    variant = total(distances, changed)
                    scenarios.append({"scenario": f"factor {i + 1} weight x{multiplier}",
                                      "score": variant, "grade": grade(variant)})
            row["sensitivity"][profile] = scenarios
        student, senior = row["responses"].values()
        assert student["location"] == senior["location"]
        assert [(f["nearestId"], f["distanceMetres"]) for f in student["factors"]] == [
            (f["nearestId"], f["distanceMetres"]) for f in senior["factors"]]
        assert [(s["layer"], s["sha256"]) for s in student["sources"]] == [
            (s["layer"], s["sha256"]) for s in senior["sources"]]
        evidence["addresses"].append(row)
        print(address, [(p, round(r["score"], 2), r["grade"]) for p, r in row["responses"].items()], flush=True)
    cases = [
        ("", "STUDENT", 400, None),
        ("Hoher Markt 1 Top 2, 1010 Wien", "STUDENT", 400, None),
        ("Hoher Markt 1, 1010 Wien", "unknown", 400, None),
        ("WienScoreNichtVorhanden 9999, 1010 Wien", "STUDENT", 200, "NO_MATCH"),
        ("Hoher Markt 1, 9999 Wien", "SENIOR", 200, "NO_MATCH"),
        ("Hauptplatz 1, 4020 Linz", "STUDENT", 400, None),
        ("Marchfeldstrasse 10, 1200 Wien", "STUDENT", 200, "NO_MATCH"),
        ("Hoher Markt 1, 1010 Vienna", "SENIOR", 200, "OK"),
        # Observed source-coverage limitations, retained rather than hidden by
        # replacing them with successful examples. Annotated NAME fields are
        # currently unsupported even when the building is a real Vienna address.
        ("Karlsplatz 13, 1040 Wien", "STUDENT", 200, "NO_MATCH"),
        ("Wagramer Straße 5, 1220 Wien", "STUDENT", 200, "NO_MATCH"),
        ("Am Himmel 1, 1190 Wien", "STUDENT", 200, "NO_MATCH"),
    ]
    for address, profile, expected_http, expected_status in cases:
        status, result = request(base, address, profile)
        assert status == expected_http, (address, status, result)
        if expected_status:
            assert result["status"] == expected_status, (address, result)
        if expected_status != "OK":
            assert result.get("grade") is None and result.get("score") is None
        evidence["inputChecks"].append({"input": address, "profile": profile,
                                         "httpStatus": status, "response": result})
    return evidence


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://localhost:8081")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    evidence = run(args.base_url.rstrip("/"))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Passed: {len(evidence['addresses'])} addresses, both profiles, {len(evidence['inputChecks'])} input cases. Evidence: {args.output}")
