# V3 ceiling-effect correction

23 September 2026. Model `neighbourhood-proximity-v3`.

## Cause and correction

V2 assigned 100 to every measurement at or below its distance benchmark. Once
all domains reached 100, every normalized weight vector necessarily returned 100.
This was a scoring-design defect for comparing proximity, not a lookup, cache,
profile selection or rounding failure.

V3 distinguishes benchmark attainment from continuous proximity utility: 100 at
zero distance, 80 at the benchmark, then zero at 3× the benchmark, with continuous
linear segments. `withinTarget` separately reports whether each measured distance
meets the benchmark. The 80-point anchor is a declared design choice, aligned with
the existing grade-1 boundary; it is not a research-calibrated wellbeing effect.
Benchmarks, source selection, domain aggregation and profile weights are unchanged.
No percentile normalization or forced Student/Senior difference is introduced.

## Controlled live comparison

Eight addresses × two profiles were reassessed by official address ID. Assertions
verified identical source hashes, nearest feature IDs and distances against the
saved v2 evidence. Only scoring changed. Full responses are in
[evidence/model-v3-validation.json](evidence/model-v3-validation.json).

| Address | Student old → new | Senior old → new |
|---|---:|---:|
| Hoher Markt 1, 1010 Wien | 100.00 → 91.95 | 100.00 → 93.43 |
| Marchfeldstraße 10, 1200 Wien | 100.00 → 92.39 | 100.00 → 92.48 |
| Karlsplatz 6, 1010 Wien | 100.00 → 94.25 | 100.00 → 94.86 |
| Hüttelbergstraße 26, 1140 Wien | 93.22 → 85.10 | 92.23 → 84.24 |
| Wagramer Straße 22, 1220 Wien | 99.11 → 92.37 | 99.11 → 93.01 |
| Am Himmel 115, 1190 Wien | 81.86 → 72.30 | 82.47 → 72.60 |
| Breitenfurter Straße 544 | 87.50 → 84.31 | 93.75 → 89.88 |
| Raffineriestraße 42, 1220 Wien | 68.75 → 64.45 | 56.25 → 53.38 |

All sixteen results are below 100. Six of eight addresses still receive grade 1
under both profiles; well-served addresses are not forced into lower grades merely
to spread the distribution. Am Himmel receives grade 2 under both; Raffineriestraße
receives Student grade 2 / Senior grade 3. Close or equal profile results remain
legitimate when measurements and weighted advantages balance. Display rounding
can also make nearby values look equal.

## Regression coverage

- Distances inside every benchmark strictly decrease the score; exact values
  include 20 m / 500 m → 99.2 and 400 m / 500 m → 84.
- Continuity at the benchmark and all 2×/3×/4× cutoff scenarios are checked.
- Two fixture locations meeting **every** benchmark must have distinct scores
  below 100 for both profiles, with benchmark flags still true.
- Existing green-area eligibility, healthcare mean, transport maximum, source
  failures, repeatability and address-selection tests remain in place.

The model continues to omit noise, walking routes and verified green entrances.
The change fixes saturation; it does not establish empirical QoL validity.

Final Docker build passed: 59 backend tests and 19 frontend tests. The live smoke
script passed after restart. Browser verification shows Hoher Markt 1 at 91.95 for
Student; factor scores and benchmark attainment are separately rendered.
