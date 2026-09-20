# syntax=docker/dockerfile:1
FROM node:24.18.0-bookworm-slim AS frontend
WORKDIR /build/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm test -- --watch=false && npm run build

FROM eclipse-temurin:21-jdk-jammy AS backend
WORKDIR /build/backend
COPY backend/ ./
COPY --from=frontend /build/frontend/dist/wienscore/browser/ ./src/main/resources/static/
RUN sh ./mvnw -B -ntp clean verify

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN groupadd --gid 10001 wienscore && useradd --uid 10001 --gid wienscore --no-create-home wienscore \
    && mkdir /app/data-cache && chown wienscore:wienscore /app/data-cache
COPY --from=backend /build/backend/target/wienscore-0.0.1-SNAPSHOT.jar /app/wienscore.jar
USER 10001:10001
EXPOSE 8081
ENV WIENSCORE_CACHE_DIRECTORY=/app/data-cache
ENTRYPOINT ["java", "-jar", "/app/wienscore.jar"]
