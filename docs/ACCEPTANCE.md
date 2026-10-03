# First milestone implementation evidence

Work/check date: **17 September 2026**. Starting Git revision: `2e0ef4b`
(`Initialize Spring Boot backend and Angular frontend`); the working tree was clean.
The earlier diary's claim that setup was uncommitted was stale. This record concerns
the implemented student milestone, not a completed sprint review or project approval.
Actual hours have not been supplied and are not inferred from tool/run timestamps.

## What changed

- Angular address form, loading/error states and a resolved-address/result screen.
- Exact Vienna WFS lookup with postcode parsing, safely encoded CQL, explicit
  no-match/ambiguity/unavailable outcomes and a guard against unrelated responses.
- Geographic verification against all 23 Vienna district polygons using JTS.
- Complete-layer validation and memory/disk cache for four amenity datasets and
  boundaries; dated fallback, expiry, corruption rejection and retry backoff.
- Centralized `student-draft-v1` rules, haversine nearest distances, weighted score
  and grade, with factors/source provenance and missing-data behavior in the UI.
- Single-image Docker build serving Angular and Spring Boot, non-root runtime,
  persistent cache volume, startup instructions and a live smoke script.
- API/model/source documentation, captured per-dataset license metadata and tests.

## Checks actually run

| Check | Observed result |
|---|---|
| Initial address increment | Backend compiled/context test passed; Angular two starter tests and production build passed. Live Hoher Markt 1 resolved correctly. |
| Backend feature suite | 36 tests passed, zero failures/errors/skips in final Docker `clean verify`. Earlier local suite of 35 tests passed before adding the unrelated-response guard. |
| Angular suite | 8 tests passed locally and in the Linux Docker build; production builds succeeded. |
| Docker build/start | `docker compose up --build -d` succeeded. Build context excluded local dependencies, cache and build outputs. Root served Angular; `/actuator/health` returned `UP`. |
| Documented smoke script | `scripts/smoke-test.ps1` passed: complete result, four factors, identical repeated score/hashes, contributions sum and no-match has no grade. |
| Cache persistence | Recreated the container after rebuilding; all five source hashes were unchanged and all sources reported `CACHED`. Runtime UID was 10001. |
| Browser through development proxy | Entered Hoher Markt 1 using keyboard only on port 4201; loading, resolved coordinates, grade and factors displayed. |
| Browser layout/input | Inspected normal desktop and 390 × 844 px views. Form, grade and factor cards were readable. Blank input showed guidance; changing input removed prior output; nonexistent input showed no match/no grade. |
| Packaged browser | Docker serves the same interface on port 8081 without Angular's development server. |
| Git whitespace check | `git diff --check` passed; no remote push performed. |

Test fixtures exercise HTTP requests and JSON parsing through a local WFS server.
They deliberately cover grade boundaries; distances 0, D/2, D and beyond D;
student weights; haversine scale; polygon edges/holes; apostrophes; missing/invalid
input; ambiguous and incomplete address responses; outside coordinates; source
timeouts/failures; incomplete/invalid datasets; missing boundary; duplicate IDs;
unknown CRS; unnamed points; disk reuse, expiry and corrupt cache. Synthetic
fixture geometry is clearly distinct from the live Vienna acceptance check.

## Live result and source evidence

`Hoher Markt 1, 1010 Wien` resolved to **16.37307549, 48.21053772**
(longitude, latitude), student score **77.8394350333933**, **grade 2**.

| Factor | Nearest feature | Distance shown | Records in complete snapshot |
|---|---|---:|---:|
| Parks | GA Morzinplatz | 257 m | 1,054 |
| Public transport stops | Rotenturmstraße | 103 m | 1,807 |
| Pharmacies | Apotheke "Zum rothen Krebs" | 2 m | 345 |
| Municipal libraries | Bücherei Zirkusgasse | 844 m | 37 |
| Boundary | 23 district polygons checked | Not scored | 23 |

Full result, retrieval times and hashes: [hoher-markt-docker.json](evidence/hoher-markt-docker.json).
Container identity/size and persistence check: [docker-runtime.json](evidence/docker-runtime.json).
License/update evidence: [source-metadata.json](source-metadata.json).

Two source details changed implementation during verification: an empty address
collection has `crs: null` (now correctly treated as no match); seven transit
records have blank names but usable points (retained with source IDs and disclosed).
The first scoring attempt withheld the grade while transit validation rejected
these names; no favorable default score was substituted.

## Acceptance baseline mapping and remaining work

| Acceptance ID | Status at this milestone |
|---|---|
| AT-01 | Passed for the documented address in the browser and API; empty input checked. |
| AT-02 | Passed for repeated calls and persisted snapshots across container recreation. Live upstream data may change later. Cache files are not a historical archive. |
| AT-03 | Passed by automated category/grade boundary tests. |
| AT-04 | Student controlled example passed; senior profile/selection not implemented and full check remains pending. |
| AT-05 | Student factors, contributions and stale-result clearing implemented/tested. Profile changes and richer explanations remain Sprint 2. |
| AT-06 | Representative fixtures and live no-match passed, including apostrophe query safety, ambiguity and outside resolved coordinates. Tolerant lookup and comprehensive real address cases remain pending. |
| AT-07 | Automated timeout, missing/incomplete/corrupt data, dated fallback and expiry passed; grade withheld without usable mandatory data. |
| AT-08 | Basic desktop/mobile and keyboard form checks passed; full accessibility/screen-reader/browser coverage remains pending. |
| AT-09 | Runnable isolated Docker build and initial empty-volume startup passed here, followed by persisted-volume checks. Independent clean-machine handover and final exported deliverable remain Sprint 3/submission work. |

## Documentation follow-up on 17 September 2026

The academic Project Diary (v0.3), work log, Sprint 1 evidence review, acceptance
checklist, scope status and dataset follow-up have been updated in the university
NAS project folder. They record the implementation revision `a72c7fe`, the checks
above and the accepted Sprint 2 priorities. Actual hours and formal reviewer
participation remain pending; the technical review is not an external approval.

Still required: senior selection and weighting; research/calibration and contrasting
Vienna locations; richer explanations and broader input/error coverage; final
clean-machine validation/export; actual effort and formal review details, remaining diary sections, slides,
video and ZIP. No external approval or lecturer response is claimed. No overall
study-plan or workload estimates were changed. This implementation uses the accepted
sprint structure and ignores the unreliable LifeDash workload forecasts.

## Sprint 2 implementation on 17 September 2026

The records above preserve the Sprint 1 milestone. The current application adds
Student/Senior selection, profile-specific explanations, visible fallback warnings,
explicit apartment-number guidance, the English Vienna suffix, and timeout/retry
handling. Model `profiles-draft-v1` retains the original student calculations and
adds the documented senior weights. No research validation is implied.

Verification performed:

- Local Maven verify: **45 tests passed**, no failures/errors/skips.
- Angular: **14 tests passed** and production build succeeded.
- Docker build repeated both suites successfully; the rebuilt application started
  on port 8081 as user `10001:10001`.
- The updated live smoke script checked both profiles, repeated results, unchanged
  distances and source hashes across profiles, contribution sums and no-match behavior.
- Browser checks: keyboard form submission; disabled controls while loading;
  profile switching clears the prior result and source details; the senior result
  shows new weights/contributions; apartment markers produce helpful feedback;
  390 × 844 mobile layout remains readable without horizontal page overflow.

Live Hoher Markt 1 result with identical source snapshots:

| Profile | Score (unrounded) | Grade |
|---|---:|---:|
| Student | 77.8394350333933 | 2 |
| Senior | 85.5878386165122 | 1 |

Full responses: [sprint2-profiles.json](evidence/sprint2-profiles.json).
Docker image: `sha256:6174f47408562d12c640b15ed32186aeaf0836394d981e6bf78a9817e29f0c86`.

AT-04 now passes the controlled two-profile example (70/2 and 52.5/3), HTTP profile
selection and browser switching. AT-05 now covers both profiles, contribution
explanations, rounding, result clearing and avoiding an overall-score explanation
on partial data. AT-06/07 coverage adds English city suffixes, explicit apartment
markers, invalid profiles/bodies, both-profile missing data, fallback and exact
seven-day expiry. AT-08 adds a persistent outcome announcement region and tested
client timeout/retry. This is representative coverage, not an exhaustive claim.

Remaining: research/calibration and contrasting-location plausibility checks;
broader real-address coverage (lookup still requires exact street spelling);
screen-reader/cross-browser verification; independent clean-machine handover and
final export; actual effort and formal review details. No formal Sprint 2 review,
course approval or final project acceptance is claimed.

## Sprint 3 validation and handover on 17 September 2026

Candidate revision **4d905e5e3102fa43da8ab9043fed74089f483a6c** contains the
unchanged `profiles-draft-v1` app plus live validation and Docker export tooling.
The [validation report](VALIDATION.md) records research rationale, six successful
addresses across five districts, 12 independently recomputed scores/grades,
11 input cases and 11 sensitivity scenarios per address/profile. All 48 nearest
amenity IDs/distances matched a separate scan of checksum-verified saved datasets.

The sample exposes grade sensitivity and exact-name coverage limits. In particular,
ordinary `Karlsplatz 13, 1040 Wien` fails because the WFS label includes a university
annotation. No-match cases are retained in the evidence; no parameter fitting,
ground-truth well-being validation or comprehensive address coverage is claimed.

The export script archived the clean committed tree and built it with `--no-cache`.
Fresh dependency installation, **45 backend tests**, **14 Angular tests** and
production build all passed. Base images were available on this Docker host; this
does not establish an empty Docker installation or byte-reproducible rebuild.
Build dependencies/base tags can change; the exported image is identified by hash.

| Export/startup check | Observed result |
|---|---|
| Image | `sha256:14f627c50212b5adff0d401c99d12c6130ee7e5ded172bcb204ad65c14b73ce0` |
| Platform and runtime user | Linux amd64; `10001:10001`, confirmed with `id` |
| Compressed image bytes | 118,462,381 |
| Source ZIP bytes | 153,800 |
| Software files total | 118,621,666 bytes plus manifest; excludes academic artifacts |
| Load | `docker load --input wienscore-image.tar.gz` succeeded |
| Isolated startup | New Compose project `wienscore-handover-check`, new named cache, port 8082, no host build/source mounts |
| Live smoke | Angular root and health; Student 77.8394350333933/2 and Senior 85.5878386165122/1; repeated score/hashes, contribution sum and no-match all passed |
| Cleanup | Test container/network stopped/removed with Compose down; test cache retained; development app on 8081 unaffected |

Manifest: [sprint3-handover-manifest.json](evidence/sprint3-handover-manifest.json).
The academic project folder holds **Handover Candidate 4d905e5**, containing the
image, matching source, startup instructions, smoke script and manifest. This is
a candidate, not the final submission ZIP. Follow [handover instructions](HANDOVER.md).

Browser acceptance added live Am Himmel Student/Senior results, three zero-factor
explanations, clearing on profile change, keyboard expansion of source details and
a 320 × 800 view. The form and factor cards were readable; client and scroll widths
were both 305 px (remaining width is the scrollbar). Viewport override was reset.
The connected in-app browser was the only available browser surface. These checks
are not an actual screen-reader or second-browser test.

AT-01/02/03/04/05/06/07 retain their existing passing representative checks with the
additional evidence above. AT-08 passes basic keyboard/responsive use; actual
assistive-technology/other-browser checks remain open. AT-09 now has a committed
source build and loadable-image empty-cache startup here; second-machine acceptance
remains open. Formal review and actual effort remain blank. Submission preparation
still needs source access/history, remaining diary/estimation fields, slides,
user-recorded video, final combined ZIP size and upload verification. No remote push.


## Address suggestions and UI simplification — 23 September 2026

The working tree adds live official-address suggestions after three characters
and a 300 ms typing pause. Up to eight results support mouse selection, Arrow
keys/Enter, Escape and dismissal on blur. New input cancels the previous request
and clears any selected ID. Manual exact entry remains available. Selected IDs
are re-fetched server-side; source geometry and the Vienna boundary still apply.
The scoring model, datasets and weights have not changed.

The Docker build passed 50 backend and 18 frontend tests. New checks cover German
transliterations/postcodes, query escaping and rejected wildcards, incomplete or
unrelated source responses, annotated labels, selected-ID substitution, boundary
checking, debounce/cancellation, keyboard and mouse selection, ID invalidation,
empty results and unavailable suggestions. Git whitespace checks passed.

Live checks: Karlsplatz 13 returned the official annotated university address;
keyboard selection and calculation succeeded (Student 80.93/100, grade 1).
`marchfeldstrasse 10, 1200 Wien` returned Marchfeldstraße 10, and
`Huettelbergstrasse 26` returned Hüttelbergstraße 26 and 26A. Mouse selection was
checked at a 390 × 844 viewport. Desktop and narrow dropdown layouts were inspected.
This is not an actual touch-device or screen-reader check. Suggestions require
live Vienna data; arbitrary typos/aliases and missing source records remain limits.
Historical September 17 exact-lookup failures remain dated evidence; the new
selection workflow addresses the annotated Karlsplatz example. Existing exported
handover files predate these changes and require a new export for final delivery.


## 23 September 2026 — neighbourhood proximity model v2

Implemented the separately specified four-domain `neighbourhood-proximity-v2`:
public green polygon sizes/boundaries, mode-aware transport, GP-plus-pharmacy
healthcare, municipal libraries, target plateaus, fixed profiles and cutoff sensitivity.
Research sources and remaining noise/routing/entrance gates are recorded in
[SOURCES_AND_MODEL.md](SOURCES_AND_MODEL.md). This does not complete the conditional
five-domain proposal or validate personal quality of life.

Final Docker build: 57 backend and 19 frontend tests passed. Live smoke checks,
eight addresses × two profiles, sensitivity evidence, and desktop/narrow-screen
browser checks are recorded in [MODEL_V2_VALIDATION.md](MODEL_V2_VALIDATION.md).
No commit, push or new handover image export was performed.


## 23 September 2026 — ceiling-effect correction (v3)

The reported all-100 results were traced to v2's full-credit plateau. V3 separates
benchmark attainment from continuous scoring, keeping source measurements,
benchmarks and weights unchanged. Eight addresses × both profiles were compared
with exactly the same source hashes and distances; all sixteen scores are below
100. See [v3 verification](MODEL_V3_VALIDATION.md) for results and regression coverage.
