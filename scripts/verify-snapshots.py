"""Independently check nearest amenities in saved live evidence against cache files."""
import argparse
import hashlib
import json
import math
from pathlib import Path

LAYERS = ["PARKINFOOGD", "HALTESTELLEWLOGD", "APOTHEKEOGD", "BUECHEREIOGD"]


def distance(a, b):
    lon1, lat1, lon2, lat2 = map(math.radians, [*a, *b])
    h = math.sin((lat2 - lat1) / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin((lon2 - lon1) / 2) ** 2
    return 2 * 6371000 * math.asin(math.sqrt(min(1, h)))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--evidence", type=Path, required=True)
    parser.add_argument("--cache", type=Path, required=True)
    args = parser.parse_args()
    evidence = json.loads(args.evidence.read_text(encoding="utf-8"))
    snapshots = {}
    for layer in LAYERS:
        envelope = json.loads((args.cache / (layer + ".json")).read_text(encoding="utf-8-sig"))
        assert hashlib.sha256(envelope["geoJson"].encode()).hexdigest() == envelope["sha256"]
        snapshots[layer] = envelope, json.loads(envelope["geoJson"])["features"]
    count = 0
    for row in evidence["addresses"]:
        for result in row["responses"].values():
            location = result["location"]
            point = [location["longitude"], location["latitude"]]
            for factor, layer in zip(result["factors"], LAYERS):
                envelope, features = snapshots[layer]
                source = next(s for s in result["sources"] if s["layer"] == layer)
                assert source["sha256"] == envelope["sha256"], "Wrong snapshot for this evidence"
                nearest = min((distance(point, f["geometry"]["coordinates"]), f["id"]) for f in features)
                assert nearest[1] == factor["nearestId"]
                assert math.isclose(nearest[0], factor["distanceMetres"], abs_tol=1e-6)
                count += 1
    print(f"Verified {count} nearest-feature IDs/distances against checksum-verified stored snapshots.")
