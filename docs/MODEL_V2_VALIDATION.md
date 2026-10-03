# Model v2 implementation and verification

Historical record; superseded by [v3 ceiling-effect correction](MODEL_V3_VALIDATION.md).

Checked 23 September 2026. Then-current model: `neighbourhood-proximity-v2`.
These checks establish implementation behaviour, not empirical validation of wellbeing.
The [model specification](SOURCES_AND_MODEL.md) records sources, formulas and limitations.

## Changes

- Public green-space polygons and four area/distance scales replace park marker points.
- Transport is classified into bus/tram and U-Bahn/regional rail with distinct targets.
  Taking the better mode avoids awarding extra points for duplicate interchange records.
- Exact GP specialties join pharmacies in a single primary-healthcare domain.
- Scores plateau at full credit within targets, decline to zero at 3×, and report
  alternative 2×/4× cutoff results. Four-domain weights are fixed and disclosed.
- UI keeps summaries short and puts places, distances, aggregation and scientific
  references in expandable details. The API exposes all nine submeasurements.
- Noise, walking-network distance, verified green entrances and raster greenery
  remain excluded by the data-readiness gates; this is not the full five-domain proposal.

## Automated and running-app checks

Final Docker build: **57 backend tests and 19 frontend tests passed**, no failures.
The final backend build includes source-count filtering after excluding night stops.
Tests cover the plateau/decline boundaries and monotonicity; both weight sets;
area eligibility, polygon interiors and holes; missing GP data withholding the
whole grade; exact GP filtering excluding a nearer surgeon; transport mode maximum
and night-only exclusion; unknown mode rejection; sensitivity aggregation; missing
qualifying features with no invented distance; cache/restart/expiry behaviour;
address suggestions and selected-ID validation; and the HTTP/UI workflow.

`./scripts/smoke-test.ps1` passed against the final running container: both profiles,
identical repeat scores and hashes, sum of contributions, and no-match withholding.
The script uses 127.0.0.1 to avoid slow IPv6 localhost fallback on this Windows host.
The app remains available at http://localhost:8081/.

Browser check: Hüttelbergstraße 26 renders Student 93.22 / grade 1. The healthcare
breakdown displays pharmacy 811 m (68.9 points) and GP 388 m (100 points), averaged
into 84.4. The method details render sources, assumptions and the 86.4–95.5 cutoff
scenario range. Desktop and a temporary 390×844 viewport were inspected; no horizontal
overflow, and the viewport was restored. This is not a full accessibility audit.

## Live sample and sensitivity

The original six sample addresses were retained without tuning parameters. Three
outer-area manual inputs were additionally tried: Breitenfurter Straße 544 resolved;
Raffineriestraße 10 and Sophienalpenstraße 2 returned NO_MATCH. The suggestion endpoint
returned Raffineriestraße 42 as its first official match; it was assessed by official
ID. Sophienalpenstraße had no suggestions. These outcomes are preserved, not hidden.

Eight resolved addresses, both profiles, full responses and snapshot hashes are in
[evidence/model-v2-validation.json](evidence/model-v2-validation.json).

| Address | Student score / grade | Senior score / grade |
|---|---:|---:|
| Hoher Markt 1, 1010 Wien | 100.00 / 1 | 100.00 / 1 |
| Marchfeldstraße 10, 1200 Wien | 100.00 / 1 | 100.00 / 1 |
| Karlsplatz 6, 1010 Wien | 100.00 / 1 | 100.00 / 1 |
| Hüttelbergstraße 26, 1140 Wien | 93.22 / 1 | 92.23 / 1 |
| Wagramer Straße 22, 1220 Wien | 99.11 / 1 | 99.11 / 1 |
| Am Himmel 115, 1190 Wien | 81.86 / 1 | 82.47 / 1 |
| Breitenfurter Straße 544 | 87.50 / 1 | 93.75 / 1 |
| Raffineriestraße 42, 1220 Wien | 68.75 / 2 | 56.25 / 3 |

Three central examples meet every target and score 100. Seven of the eight addresses
receive grade 1 under the default cutoffs. This deliberately target-based score has
limited discrimination among well-served locations; it does not rank their overall
quality of life. The UI calls it proximity and explains 100 as targets met.
No cutoffs were retrospectively tightened just to spread this small sample's grades.

At Am Himmel 115 the stricter 2× cutoff changes both profiles from grade 1 to grade 2.
At Raffineriestraße 42 both healthcare and library scores are zero, with real distances
beyond the cutoffs; strong green/transport scores compensate through weighted addition.
This compensation is an explicit property of the chosen composite model.

The evidence also compares equal 25% domain weights, and separately perturbs each
profile weight by ±20% relative before normalizing all four weights. These are offline
model scenarios, not runtime missing-data renormalization. Such changes can affect
scores/grades and show that the presets should not be treated as scientific constants.
User/expert preference validation and independent outcome calibration remain future work.

## Historical evidence

[VALIDATION.md](VALIDATION.md) and earlier acceptance entries refer to v1 and remain
unchanged as dated records. Old packaged Docker exports are not the v2 deliverable;
re-export the current image when preparing the final handover.
