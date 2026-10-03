# WienScore

A Vienna address-to-grade prototype for the Innovation Lab 2 individual retake,
project **LQ_Web**. Choose a **student** or **senior** profile using real
Vienna open data: public green areas, transport modes, general practitioners, pharmacies and municipal libraries.

Try **Hoher Markt 1, 1010 Wien**. The app shows the resolved location, a grade from
1 (best) to 5, nearest places/distances, contributions and source snapshots.
Planning benchmarks inform green-space and transport targets; weights, decline and
other thresholds are design choices, not a validated quality-of-life rating. Both profiles use the same measured distances
with different documented weights. Address suggestions support official labels and common German spelling variants.

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
calculation loads six layers and can take up to about 105 seconds on timeouts.
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
- [Current scoring-fix verification](docs/MODEL_V3_VALIDATION.md)
- [Historical v2 verification](docs/MODEL_V2_VALIDATION.md)
- [Historical v1 validation](docs/VALIDATION.md)
- [Export and handover instructions](docs/HANDOVER.md)

Current model: `neighbourhood-proximity-v3`. Green space uses four size/distance
benchmarks; transport takes the better bus/tram or rail proximity score; healthcare
combines general practitioners and pharmacies. Scores decrease continuously from 100 at zero distance to 80 at each
benchmark, then to zero at three times that distance. Benchmark attainment is shown separately. Fixed profile weights
combine four domains; all required data and a verified Vienna address are mandatory.
The breakdown shows measurements, assumptions and a 2×/4× cutoff sensitivity check.
Noise, walking routes and verified park entrances remain outside this version.
See the model documentation for exact formulas, sources and the remaining data gates.

Start typing a street and house number to see up to eight official Vienna address
suggestions. Select with a click or Arrow keys and Enter; Escape closes the list.
Suggestions start after three characters and a short typing pause. They support
case-insensitive prefixes, ss/ß and ae/ä, oe/ö, ue/ü variants, and an optional
postcode and Wien/Vienna suffix. Add a house number to narrow broad searches.
Selected addresses are re-fetched by official feature ID on the server; client
coordinates are never trusted. Annotated names such as Karlsplatz 13 Technische
Universität Wien can therefore be selected and assessed. Editing the address
clears the selected ID. No general typo correction or street aliases are promised.
Manual entry without selecting a suggestion retains the exact-address fallback.
Leave out apartment/Top/Tür/Stiege numbers. Source outages show a message, and
manual entry stays available. Suggestions require live access to Vienna's service.

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
