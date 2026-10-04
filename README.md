# WienScore

WienScore is my Innovation Lab 2 project (LQ_Web). Enter a Vienna address and
choose the student or senior profile to get a grade from 1 (best) to 5.
The result shows nearby green spaces, public transport, healthcare and libraries,
with distances and an explanation of the score.

The profiles give different weights to the same factors. This is a simple
proximity model, not an official quality-of-life rating. Distances are straight-line
estimates, not walking routes. Noise and air quality are not included.

## Run with Docker

You need Docker with Linux containers, Docker Compose, internet access and a free
port 8081. From the project folder:

```powershell
docker compose up --build -d
```

Open http://localhost:8081 and try **Hoher Markt 1, 1010 Wien**. You can select an
address suggestion while typing. The first calculation takes longer because it
downloads the datasets. Address lookup always needs access to Vienna's data service.
If required data are unavailable, the app shows a message instead of a grade.

To view logs or stop the app:

```powershell
docker compose logs --tail=100
docker compose down
```

The data cache stays in a Docker volume between runs. The Docker build also runs
the backend and frontend tests.

## Local development

The project uses Java 21, Spring Boot and Angular. You need Java 21 and Node
24.15 or newer within 24.x, with npm. Maven is included through the wrapper.

Start the backend:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

In a second terminal, start the frontend:

```powershell
cd frontend
npm ci
npm start
```

Open http://localhost:4201. The frontend forwards API requests to the backend on
port 8081. On Linux/macOS, use `sh ./mvnw` instead of `.\mvnw.cmd`.

## Checks and export

Run `.\mvnw.cmd -B -ntp clean verify` in `backend`, and
`npm test -- --watch=false` followed by `npm run build` in `frontend`.
With the app running, `./scripts/smoke-test.ps1` checks both profiles using live data.

To export a Docker image and matching source ZIP, run this from a clean, committed
checkout with Python 3.11+ and Docker installed. Use a new local output folder
outside any synced Documents folder:

```powershell
python scripts/export-handover.py --output "$env:TEMP\wienscore-handover"
```

The export includes startup instructions and file checksums.

## Sprint plan

| Sprint | Planned dates (2026) | Focus |
| --- | --- | --- |
| 1 | 14–20 September | Setup, address lookup, first score and Docker |
| 2 | 21–27 September | Profiles, explanations and error handling |
| 3 | 28 September–4 October | Validation, fixes and handover |

These are the planned weeks. Some work was completed early. The project diary is
submitted separately.

## Data and license

Datenquelle: [Stadt Wien](https://data.wien.gv.at),
[CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).
WienScore calculates distances and scores from these data. The result includes
sources and snapshot dates used. The project code is under the [MIT license](LICENSE).
