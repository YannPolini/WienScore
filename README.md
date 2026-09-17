# WienScore

Quality-of-life scores for Vienna addresses, tailored to students and seniors.

Innovation Lab 2 individual project: **LQ_Web**.

## Current setup

- `backend/`: Java 21 / Spring Boot 4.1.1, Spring Web, Actuator and Maven Wrapper.
- `frontend/`: Angular 22, TypeScript, SCSS, routing and an HTTP client.

The frontend displays a minimal starting page. The backend currently provides a
health endpoint. Address lookup, scoring, Docker packaging and production serving
of Angular will be added during development.

## Requirements

- JDK 21 (`java -version` should report version 21).
- Node.js 24.15 or newer within the 24.x line, with npm (verified with Node 24.19).
- Internet access to install dependencies and download Maven on the first build.

Maven Wrapper downloads the pinned Maven version automatically; a separate Maven
installation is not required. If Java cannot be found, set `JAVA_HOME` to your JDK
installation directory.

## Run the backend

From the repository root in PowerShell:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Check `http://localhost:8081/actuator/health` — its JSON should include `"status":"UP"`.
The backend root URL `/` currently returns 404; open Angular on port 4201 instead.
Press Ctrl+C to stop.

On Linux/macOS, use `sh ./mvnw` in place of `.\mvnw.cmd`.

## Run the frontend

Keep Spring Boot running and open a second terminal at the repository root:

```powershell
cd frontend
npm ci
npm start
```

Open `http://localhost:4201`. You only need `npm ci` after cloning or when the
lockfile changes. Angular CLI is installed locally; no global installation is needed.

During development, `src/proxy.conf.json` forwards `/api/**` and
`/actuator/health` to Spring Boot at `127.0.0.1:8081`. Check
`http://localhost:4201/actuator/health` to verify the connection. Future HTTP client
calls should use relative paths such as `/api/...`; application API routes are
not implemented yet. This development proxy does not configure production hosting.

WienScore uses ports 4201 and 8081 to avoid conflicting with LifeDash on 4200/8080.

## Frontend checks

From `frontend/`:

```powershell
npm test -- --watch=false
npm run build
```

The production files are generated in `frontend/dist/wienscore/browser/`.

## Test and package

From `backend/`:

```powershell
.\mvnw.cmd clean verify
java -jar target/wienscore-0.0.1-SNAPSHOT.jar
```

`verify` runs the application-context smoke test and creates the executable JAR.
The test verifies startup wiring; feature acceptance tests will be added during
implementation.

Open `backend/pom.xml` as a Maven project in your Java IDE. Source code starts in
`backend/src/main/java/dev/polini/wienscore/`.
