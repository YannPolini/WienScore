# WienScore

A Vienna address-to-grade prototype for the Innovation Lab 2 individual retake,
project **LQ_Web**. Choose a **student** or **senior** profile using real
Vienna open data: parks, Wiener Linien stops, pharmacies and municipal libraries.

Try **Hoher Markt 1, 1010 Wien**. The app shows the resolved location, a grade from
1 (best) to 5, nearest places/distances, contributions and source snapshots.
The weights and thresholds are provisional assumptions, not validated research
or an official quality-of-life rating. Both profiles use the same measured distances
with different documented weights. Exact street spelling is still required.

## Quick start with Docker

Prerequisites: Docker Engine/Desktop running **Linux containers**, Docker Compose,
a free local port **8081**, and internet access. No API keys, database, local JDK,
Node installation or pre-downloaded datasets are needed for Docker startup.
The build downloads public images and Maven/npm dependencies. Runtime needs HTTPS
access to `data.wien.gv.at`; every address lookup is live. A source outage with no
usable cached snapshot produces a visible unavailable result, not a grade.

From the repository root:

```powershell
docker compose up --build -d
```

Open **http://localhost:8081** and calculate `Hoher Markt 1, 1010 Wien`.
Startup normally takes a few seconds after the image is built. The first
calculation loads five layers and can take up to about 90 seconds on timeouts.
Check `http://localhost:8081/actuator/health` for `{"status":"UP"}`. Health means
that the app started; it does not guarantee the external datasets are available.

```powershell
docker compose logs --tail=100
docker compose down
```

The named `dataset-cache` volume preserves validated data across container
restarts. `down` preserves it too. The Docker image serves built Angular and
Spring Boot from a single Java process, running as a non-root user. The port is
bound to the local machine only. Stop a locally running backend before starting
Docker on 8081.

The Docker build runs the backend and frontend tests. `.dockerignore` excludes
host dependencies, build output and cached data; neither a host `node_modules`
nor a previously built JAR is needed. To check the running workflow from PowerShell:

```powershell
./scripts/smoke-test.ps1
```

See [Docker handover](docs/HANDOVER.md) for creating a loadable image, matching
source archive and integrity manifest. Final combined submission packaging and
independent-machine handover remain separate acceptance steps.

## Local development

Requirements: Java **21**, Node **24.15+ within 24.x**, npm, internet for the first
build. Verified here with Java 21.0.11 and Node 24.18.0. Maven Wrapper pins Maven;
a separate Maven install is unnecessary. Set `JAVA_HOME` if Java cannot be found.

Backend, from the repository root:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Frontend, in a second terminal from the repository root:

```powershell
cd frontend
npm ci
npm start
```

Open **http://localhost:4201**. Angular's development proxy forwards `/api/**` and
`/actuator/health` to `127.0.0.1:8081`. Use relative API URLs. Plain backend
startup does not build/serve Angular; its root `/` returns 404. Docker includes
Angular, so the Docker root `/` serves the app. Press Ctrl+C to stop local servers.
On Linux/macOS replace `.\mvnw.cmd` with `sh ./mvnw`.

## Checks

From `backend/`:

```powershell
.\mvnw.cmd -B -ntp clean verify
```

From `frontend/`:

```powershell
npm test -- --watch=false
npm run build
```

The backend suite includes calculation boundaries, both documented profile-weight
example, geometry, exact address queries, ambiguity, missing/invalid data, timeout
fallback, cache expiry/corruption and an HTTP integration flow against a local WFS
fixture. Angular checks the form, loading/errors, result details, stale-result
clearing on address/profile changes, ambiguity, fallback display, request timeout
and retry. Automated tests do not need live Vienna
data. The smoke script and browser acceptance checks do.

Backend JAR: `backend/target/wienscore-0.0.1-SNAPSHOT.jar`.
Frontend build: `frontend/dist/wienscore/browser/`.
See [acceptance evidence](docs/ACCEPTANCE.md) for actual results and limits.

## Data, calculation and API

- [Source manifest, model and limitations](docs/SOURCES_AND_MODEL.md)
- [Captured per-dataset license/update metadata](docs/source-metadata.json)
- [API contract and screen behavior](docs/API.md)
- [Implementation/verification record](docs/ACCEPTANCE.md)
- [Research, contrasting locations and sensitivity checks](docs/VALIDATION.md)
- [Export and handover instructions](docs/HANDOVER.md)

Model rules live in `ScoringModel.java`: distance limits 1000/800/1000/2000 m,
student weights 25/40/10/25%, senior weights 35/25/35/5%, linear distance scores and grade cutoffs
80/60/40/20. Current model: `profiles-draft-v1`. Change the model version when changing the rules. No weights are
silently renormalized. Grades require all four categories and a verified location
inside Vienna's district polygons. Missing names are disclosed; missing or invalid
coordinates invalidate a snapshot.

Address lookup is deliberately exact: street and house number, optionally postcode
and Wien or Vienna. Leave out apartment/Top/Tür/Stiege numbers; explicit markers
produce helpful feedback, not a guessed building. For example, use
`Marchfeldstraße 10, 1200 Wien`, including the correct street spelling.
No fuzzy matching or autocomplete is implemented. No-match, ambiguity,
invalid input, out-of-boundary coordinates and unusable data give explicit outcomes.
Some real buildings have annotated labels in the source and are unsupported:
`Karlsplatz 13, 1040 Wien` is one observed example. See the validation report
for successful examples and retained no-match cases.
The UI never silently picks an ambiguous result or keeps a result for edited input.
The chosen profile is sent with each request; controls are disabled during the
calculation. A request that stalls for two minutes ends with a retry message.
Older-snapshot fallbacks are highlighted above the result as well as in source
details. Incomplete data show individual available factors without a partial grade.

Datenquelle: Stadt Wien – https://data.wien.gv.at. Selected data: **CC BY 4.0**.
WienScore derives distances and scores from the source data. See the source manifest
for per-layer attribution, coverage, limitations and metadata evidence.

## Cache and configuration

Five mandatory layers are cached in memory and as validated JSON envelopes in
`backend/data-cache/` for local backend runs, or `/app/data-cache` in Docker.
Address lookups are not persisted. Each response identifies the used snapshot by
retrieval time and SHA-256. Keep the response and snapshot files together if you
need to reproduce that exact dataset state later; the cache is not an archive.

Spring properties can be overridden with environment variables:

| Environment variable | Default | Meaning |
|---|---|---|
| `WIENSCORE_CACHE_DIRECTORY` | `./data-cache` | Writable snapshot directory; Docker sets `/app/data-cache`. |
| `WIENSCORE_CACHE_REFRESH` | `PT24H` | Refresh a layer after 24 hours, on the next calculation. |
| `WIENSCORE_CACHE_MAX_AGE` | `P7D` | Withhold data once 7 days old if refresh fails. Must be ≥ refresh interval. |
| `WIENSCORE_CACHE_RETRY` | `PT5M` | Retry interval after a failed refresh. |
| `WIENSCORE_REQUEST_TIMEOUT` | `PT15S` | Timeout per source request (connect timeout: 5 s). |
| `WIENSCORE_WFS_URL` | `https://data.wien.gv.at/daten/geo` | Integration-test override; production attribution assumes the official source. |

Durations use ISO-8601. No refresh interval guarantees feature freshness. Bad or
incomplete responses never replace a validated snapshot. A failed refresh can use
a dated fallback within the maximum age, with that fact visible in the result.
A fresh installation still needs internet and a functioning address/data source.

No accounts, database, map or air-quality scoring are included. The project uses
Java 21 / Spring Boot 4.1.1 and Angular 22. JTS 1.20.0 provides boundary geometry
operations (EPL 2.0 / EDL 1.0). Repository technical documentation stays here;
academic diary/work records stay in the university NAS folder. No remote push is
part of the implementation workflow unless explicitly requested.
