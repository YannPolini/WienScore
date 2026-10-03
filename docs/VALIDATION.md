# Sprint 3 model and address validation

Historical v1 record. For the current implementation, see [v2 verification](MODEL_V2_VALIDATION.md).

Checked 17 September 2026 against application revision `21b69bc`, model
`profiles-draft-v1`. This is a small purposive sample, not a representative survey
or a validation of quality of life. No parameters were changed to fit it.

## Research basis and decision

OECD measures well-being across material and non-material dimensions, including
housing, health, environment and subjective well-being. Four proximity factors
cover only a narrow part of that scope. WienScore should continue to describe an
amenity-access estimate. [OECD, How's Life?](https://www.oecd.org/en/publications/how-s-life_23089679.html)

WHO's age-friendly programme includes transport, outdoor spaces and community
support/health services. These motivate considering access for older users;
one pharmacy's proximity cannot measure healthcare or mobility accessibility.
This source does not establish Senior weights.
[WHO, age-friendly programmes, 2023](https://www.who.int/news/item/19-04-2023-who-releases-new-guide-on-developing-national-programmes-for-age-friendly-cities-and-communities)

WHO's review considers health and social effects of urban green-space
interventions. It supports investigating green-space access, but the project's
nearest park point omits size, entrances, usability and quality.
[WHO, urban green-space review, 2017](https://www.who.int/europe/publications/m/item/urban-green-space-interventions-and-health--a-review-of-impacts-and-effectiveness.-full-report)

The IFLA–UNESCO manifesto identifies public libraries as supporting education,
culture and information access. This motivates a library factor; it does not
justify a 25% Student weight or imply municipal libraries capture university
study resources. [IFLA–UNESCO Public Library Manifesto, 2022](https://www.ifla.org/public-library-manifesto/)

Sources consulted 17 September 2026 inform factor choice, not numeric calibration.
The 1000/800/1000/2000 m limits, linear decay, weights and 80/60/40/20 cutoffs remain
design assumptions. Retain `profiles-draft-v1` with its caveats. Empirical calibration
would require agreed outcomes, user input and independent observations. These
checks do not establish an optimal parameter set.

## Contrasting address results

Six exact dataset addresses span five districts and different measured amenity
patterns. They were not selected to obtain particular grades. Three initial
no-match inputs are retained below. Distances are rounded metres; scores here
are rounded only for display.

| Address | Park | Stop | Pharmacy | Library | Student score / grade | Senior score / grade |
|---|---:|---:|---:|---:|---:|---:|
| Hoher Markt 1, 1010 | 257 | 103 | 2 | 844 | 77.84 / 2 | 85.59 / 1 |
| Marchfeldstraße 10, 1200 | 212 | 236 | 282 | 115 | 78.65 / 2 | 75.06 / 2 |
| Karlsplatz 6, 1010 | 163 | 149 | 165 | 633 | 78.89 / 2 | 82.27 / 1 |
| Hüttelbergstraße 26, 1140 | 54 | 298 | 811 | 1618 | 55.43 / 3 | 56.37 / 3 |
| Wagramer Straße 22, 1220 | 382 | 122 | 64 | 657 | 75.49 / 2 | 78.96 / 2 |
| Am Himmel 115, 1190 | 1023 | 381 | 1070 | 3668 | 20.93 / 4 | 13.08 / 5 |

The results follow the formula: Hoher Markt's very close pharmacy benefits Senior;
Marchfeldstraße's very close library benefits Student. Hüttelbergstraße has a
nearby park but longer pharmacy/library distances. At Am Himmel, only the stop is
within its limit. Its poor grade does not establish poor quality of life or lack
of greenery; woodland and unmapped green space are not measured by the park layer.
These are interpretations of the data, not independent field observations.

`scripts/validate-live.py` independently recomputed all 12 scores/grades, checked
identical locations, nearest IDs, distances and hashes across profiles, and ran
11 input cases. `scripts/verify-snapshots.py` independently scanned the original
checksum-verified amenity snapshots and confirmed all **48** nearest IDs/distances
within one micrometre of the Java calculation. This tolerance checks numerical
implementation, not geographic accuracy. Full evidence:
[sprint3-validation.json](evidence/sprint3-validation.json). Matching snapshots
remain in the academic evidence folder dated 2026-09-17.

## Sensitivity

Each result was recalculated with all distance limits multiplied by 0.8 and 1.2,
equal weights, and each individual weight multiplied by 0.8/1.2 then normalized:
11 exploratory scenarios per result. All use complete data. This never changes
production rules or renormalizes around missing categories.

| Address | Student grade range including baseline | Senior grade range |
|---|---:|---:|
| Hoher Markt 1 | 1–2 | 1–2 |
| Marchfeldstraße 10 | 1–2 | 2 |
| Karlsplatz 6 | 1–2 | 1–2 |
| Hüttelbergstraße 26 | 2–3 | 2–3 |
| Wagramer Straße 22 | 2 | 1–2 |
| Am Himmel 115 | 4–5 | 4–5 |

Grades can change under modest parameter variations. At Wagramer Straße, reducing
Senior's park weight by 20% then normalizing crosses into grade 1. At Am Himmel,
changing one Student weight can cross from 4 to 5. A one-grade difference is not
an established difference in lived well-being. These are scenario ranges, not
confidence intervals.

## Address coverage findings

Empty input, apartment/Top markers and unknown profiles return HTTP 400. A
nonexistent street, wrong postcode and `Marchfeldstrasse` instead of
`Marchfeldstraße` return no match without a grade. `Vienna` works as a city suffix.
`Hauptplatz 1, 4020 Linz` receives format guidance without a grade; the existing
automated boundary fixture checks a resolved outside-Vienna point.

Initial inputs `Karlsplatz 13, 1040 Wien`, `Wagramer Straße 5, 1220 Wien` and
`Am Himmel 1, 1190 Wien` returned no match. The first is a concrete coverage gap:
the official WFS NAME is `Karlsplatz 13 Technische Universität Wien`; the parser
supports exact street-and-number labels. No annotation was stripped and no nearby
entry was silently selected. Source inspection identified supported Karlsplatz 6,
Wagramer Straße 22 and Am Himmel 115 for the numerical sample. This does not prove
the initial buildings nonexistent. Annotated labels, spelling variants and broader
coverage remain limitations. Fuzzy matching is outside implemented scope.

## Repeat the checks

With Python 3 and a running app, from the repository root:

```powershell
python scripts/validate-live.py --output backend/target/live-validation.json
python scripts/verify-snapshots.py --evidence docs/evidence/sprint3-validation.json --cache <preserved-cache-directory>
```

Live assertions can fail when source availability, coverage, model or snapshots
change. Investigate before updating expectations. Preserve the dated evidence.
