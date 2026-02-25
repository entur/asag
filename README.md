# asag ![Build](https://github.com/entur/asag/actions/workflows/push.yml/badge.svg)

**asag** is a Kubernetes CronJob that keeps a [Mapbox](https://mapbox.com) tileset up to date with NeTEx stop-place data exported from Entur's Tiamat stop registry.

Maintained by **team-ror** (`team.rutedata@entur.org`).

---

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Data Flow](#data-flow)
- [Components](#components)
- [Configuration](#configuration)
- [Docker](#docker)
- [Helm / Kubernetes](#helm--kubernetes)
- [CI/CD](#cicd)
- [Development](#development)
- [Testing](#testing)
- [Dependencies](#dependencies)

---

## Overview

Once a day (06:30 UTC) the job:

1. Downloads `tiamat_export_geocoder_latest.zip` from a GCS bucket
2. Parses the NeTEx XML inside it, filtering out expired entities
3. Maps `StopPlace`, `Quay`, `Parking` and `TariffZone` entities to a GeoJSON `FeatureCollection`
4. Uploads the resulting `.geojson` file to Mapbox via their Uploads API (using temporary AWS S3 credentials)
5. Polls Mapbox until the tileset processing is complete (or times out)
6. Posts a status summary to Slack

The application has **no HTTP server** — it is a pure batch ETL job (`WebApplicationType.NONE`).

---

## Architecture

```
┌─────────────────────┐
│  Google Cloud       │
│  Storage (GCS)      │  tiamat_export_geocoder_latest.zip
└────────┬────────────┘
         │ download
         ▼
┌─────────────────────┐
│  BlobStoreService   │  unzip
└────────┬────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────┐
│  DeliveryPublicationStreamToGeoJson                     │
│                                                         │
│  NeTEx XML ──JAXB──► filter by validity period          │
│                       │                                 │
│                       ├─► StopPlaceToGeoJsonFeatureMapper│
│                       ├─► QuayToGeoJsonFeatureMapper    │
│                       ├─► ParkingToGeoJsonFeatureMapper │
│                       └─► TariffZoneToGeoJsonFeatureMapper│
└────────┬────────────────────────────────────────────────┘
         │ GeoJSON FeatureCollection
         ▼
┌─────────────────────┐
│  Mapbox API         │  GET /uploads/v1/{user}/credentials
│  (credentials)      │  → temporary AWS S3 credentials
└────────┬────────────┘
         │
         ▼
┌─────────────────────┐
│  AWS S3             │  PUT .geojson (via temp credentials)
└────────┬────────────┘
         │
         ▼
┌─────────────────────┐
│  Mapbox Uploads API │  POST /uploads/v1/{user}
│  + status polling   │  GET  /uploads/v1/{user}/{id}
│  (max 20 retries,   │  (20 s delay between retries)
│   20 s interval)    │
└────────┬────────────┘
         │
         ▼
┌─────────────────────┐
│  Slack Webhook      │  started / success / error / timeout
└─────────────────────┘
```

---

## Data Flow

| Step | Route ID | Description |
|------|----------|-------------|
| 1 | `mapbox-download-latest-tiamat-export-to-folder` | Download zip from GCS |
| 2 | `mapbox-unzip-tiamat-export` | Unzip NeTEx archive to local dir |
| 3 | `mapbox-find-first-xml-file-recursive` | Locate the NeTEx XML file |
| 4 | `mapbox-transform-from-tiamat` | NeTEx XML → GeoJSON |
| 5 | `mapbox-retrieve-aws-credentials` | Fetch temporary S3 credentials from Mapbox |
| 6 | `upload-mapbox-data-aws` | Upload GeoJSON to S3 |
| 7 | `initiate-mapbox-upload` | POST to Mapbox Uploads API |
| 8 | `mapbox-poll-retry-upload-status` | Poll until complete/error/timeout |

All routes are wired together in `MapBoxUpdateRouteBuilder.java` using Apache Camel DSL.

---

## Components

### `mapbox/`

| Class | Responsibility |
|-------|---------------|
| `MapBoxUpdateRouteBuilder` | Master Camel route orchestrator |
| `DeliveryPublicationStreamToGeoJson` | Streaming NeTEx XML → GeoJSON transformer |
| `AwsS3Uploader` | Upload file to AWS S3 via temporary Mapbox credentials |
| `ValidityFilter` | Exclude NeTEx entities past their validity period |
| `StopPlaceToGeoJsonFeatureMapper` | Map `StopPlace` → GeoJSON Feature |
| `QuayToGeoJsonFeatureMapper` | Map `Quay` → GeoJSON Feature |
| `ParkingToGeoJsonFeatureMapper` | Map `Parking` → GeoJSON Feature |
| `TariffZoneToGeoJsonFeatureMapper` | Map `TariffZone` → GeoJSON Feature |
| `ZoneToGeoJsonFeatureMapper` | Shared base mapper for zone-type entities |
| `MapperHelper` | Reflection/enum utilities for property mapping |
| `KeyValuesHelper` | Extract NeTEx `keyList` values |

### `service/`

| Class | Responsibility |
|-------|---------------|
| `BlobStoreService` | Read files from Google Cloud Storage |
| `UploadStatusHubotReporter` | Post Slack notifications via webhook |

### `netex/`

| Class | Responsibility |
|-------|---------------|
| `PublicationDeliveryHelper` | JAXB utilities for parsing NeTEx XML |

---

## Configuration

All configuration is injected via environment variables (Kubernetes ConfigMap / Secrets). There is no `application.properties` file.

### Required variables

| Variable | Spring property | Description |
|----------|----------------|-------------|
| `BLOBSTORE_GCS_CONTAINER_NAME` | `blobstore.gcs.container.name` | GCS bucket containing the NeTEx export |
| `BLOBSTORE_GCS_PROJECT_ID` | `blobstore.gcs.project.id` | GCP project ID |
| `MAPBOX_ACCESS_TOKEN` | `mapbox.access.token` | Mapbox API access token (secret) |
| `MAPBOX_USER` | `mapbox.user` | Mapbox account username |
| `MAPBOX_TILESET_FILE_NAME` | `mapbox.tileset.file.name` | Tileset identifier / dataset name |
| `HELPER_SLACK_ENDPOINT` | *(via entur helpers slack)* | Slack webhook URL (secret) |

### Optional variables

| Variable | Default | Description |
|----------|---------|-------------|
| `BLOBSTORE_GCS_CREDENTIAL_PATH` | *(workload identity)* | Path to GCP service account JSON |
| `MAPBOX_DOWNLOAD_DIRECTORY` | `files/mapbox` | Local working directory for temp files |
| `TIAMAT_EXPORT_BLOBSTORE_SUBDIRECTORY` | `tiamat/geocoder` | Sub-path within the GCS bucket |
| `JAVA_OPTIONS` | `-server -Xmx1500m -Dfile.encoding=UTF-8` | JVM flags |
| `TZ` | `Europe/Oslo` | Container timezone |

### Production values (from Helm)

```
BLOBSTORE_GCS_CONTAINER_NAME = ror-kakka-production
BLOBSTORE_GCS_PROJECT_ID     = ent-kakka-prd
MAPBOX_TILESET_FILE_NAME     = neon-1287
MAPBOX_DOWNLOAD_DIRECTORY    = files/tmp/mapbox
```

---

## Docker

**Base image:** `adoptopenjdk/openjdk11:alpine-jre`

```dockerfile
FROM adoptopenjdk/openjdk11:alpine-jre
WORKDIR /deployments
COPY target/asag-*-SNAPSHOT.jar asag.jar
RUN addgroup appuser && adduser --disabled-password appuser --ingroup appuser
RUN chown -R appuser:appuser /deployments
USER appuser
CMD java $JAVA_OPTIONS -jar asag.jar
```

Key points:
- Minimal Alpine-based JRE image
- Non-root `appuser` for security
- No `EXPOSE` — batch job, no inbound ports
- JVM tuned via `$JAVA_OPTIONS` at runtime

### Build & run locally

```bash
# Build the JAR
mvn package -DskipTests

# Build the image
docker build -t asag:local .

# Run (minimal example)
docker run --rm \
  -e BLOBSTORE_GCS_CONTAINER_NAME=my-bucket \
  -e BLOBSTORE_GCS_PROJECT_ID=my-project \
  -e BLOBSTORE_GCS_CREDENTIAL_PATH=/creds/sa.json \
  -e MAPBOX_ACCESS_TOKEN=pk.xxx \
  -e MAPBOX_USER=myuser \
  -e MAPBOX_TILESET_FILE_NAME=my-tileset \
  -e HELPER_SLACK_ENDPOINT=https://hooks.slack.com/... \
  -v /path/to/sa.json:/creds/sa.json:ro \
  asag:local
```

### Copy generated GeoJSON from a running pod

While the job is running (look for `Upload: {"tileset":...}` in the logs):

```bash
kubectl cp <pod-name>:/deployments/files/mapbox/<tileset>.geojson .
```

---

## Helm / Kubernetes

Chart location: `helm/asag/`

### Chart summary

| Field | Value |
|-------|-------|
| Chart name | `asag` |
| Chart version | `0.1.0` |
| App version | `0.1.99.2` |
| Dependency | `entur/common:1.21.1` |

The chart uses Entur's internal `common` Helm library which renders CronJob, ConfigMap, ExternalSecret, PodDisruptionBudget, and VerticalPodAutoscaler manifests from a shared template.

### Kubernetes resource summary (production)

| Resource | Kind | Notes |
|----------|------|-------|
| `asag` | `CronJob` | Schedule `30 06 * ? *`, `concurrencyPolicy: Forbid` |
| `asag` | `ConfigMap` | Runtime env vars |
| `asag-mapbox` | `ExternalSecret` | `MAPBOX_ACCESS_TOKEN`, `MAPBOX_USER` |
| `asag-slack` | `ExternalSecret` | `HELPER_SLACK_ENDPOINT` |
| `asag` | `PodDisruptionBudget` | `minAvailable: 0%` |
| `asag` | `VerticalPodAutoscaler` | `updateMode: Off` (advisory) |

### Resource requests / limits

| | CPU | Memory |
|-|-----|--------|
| Request | 0.1 | 1000 Mi |
| Limit | 1 | 3000 Mi |

### Security context

- `runAsNonRoot: true`
- `runAsUser: 1000`
- `allowPrivilegeEscalation: false`
- `seccompProfile: RuntimeDefault`

### Useful Helm commands

```bash
# Lint the chart
helm lint helm/asag

# Template (dry-run)
helm template asag helm/asag -f helm/asag/values.yaml

# Update chart dependencies
helm dependency update helm/asag
```

---

## CI/CD

### `push.yml` — Build, test and publish

**Trigger:** push or PR to `master`

| Job | Condition | What it does |
|-----|-----------|-------------|
| `maven-verify` | always | Checkout, Java 11 (Liberica), `mvn verify`, upload JAR artifact |
| `docker-build` | push to master, entur org | Build Docker image from JAR artifact |
| `docker-push` | after docker-build | Push image to registry |

Requires secrets: `JFROG_USER`, `JFROG_PASS` (Entur Artifactory for internal Maven dependencies).

### `codeql.yml` — Security scanning

**Trigger:** push/PR to master + weekly (`0 3 * * MON`)

Uses `entur/gha-security/.github/workflows/code-scan.yml@v2` for Java 11 CodeQL analysis.

---

## Development

### Prerequisites

- Java 11
- Maven 3.6+
- Access to Entur's Artifactory (for internal Maven packages like `gcp-storage` and `netex-java-model`)

### Build

```bash
mvn verify -s .github/workflows/settings.xml
```

The `-s` flag points to a settings file that configures Entur's internal Maven repository. For local builds without Artifactory access, you may need to install internal packages manually.

### Maven settings

The Maven settings file at `.github/workflows/settings.xml` is downloaded from Entur's GitHub org during CI. It configures the Artifactory repositories for:
- `org.entur.ror.helpers:gcp-storage`
- `org.entur.ror.helpers:slack`
- `org.entur:netex-java-model`

---

## Testing

**Framework:** JUnit 4 + AssertJ + WireMock (via `spring-cloud-contract-wiremock`)

```bash
mvn test
```

### Test classes

| Test | Type | Description |
|------|------|-------------|
| `MapBoxUpdateRouteBuilderTest` | Integration | Full Camel route: success, error, and timeout scenarios |
| `DeliveryPublicationStreamToGeoJsonTest` | Unit | NeTEx XML → GeoJSON conversion |
| `StopPlaceToGeoJsonFeatureMapperTest` | Unit | Stop place mapping, adjacent sites, parent/child refs |
| `QuayToGeoJsonFeatureMapperTest` | Unit | Quay public code mapping |
| `ParkingToGeoJsonFeatureMapperTest` | Unit | Parking capacity, vehicle types, covered status |
| `ZoneToGeoJsonFeatureMapperTest` | Unit | Point and polygon geometry from NeTEx centroid/polygon |

### Test infrastructure

- **`TestConfig`** — `@Profile("test")` bean that replaces `BlobStoreService` with a Mockito mock (no GCS needed)
- **WireMock** — stubs Mapbox credentials, upload initiation, status polling, and Slack webhook
- **Test resources** — `publication-delivery.xml`, `adjacent_sites_netex.xml`, `stops.zip`

### Integration test scenarios

| Scenario | Expected outcome |
|----------|-----------------|
| Mapbox upload succeeds | Route property `finished` |
| Mapbox returns error status | Route property `error` |
| Mapbox keeps returning incomplete | Route property `timeout` after 20 retries |

---

## Dependencies

### Runtime (key libraries)

| Library | Version | Purpose |
|---------|---------|---------|
| Spring Boot | 2.1.1.RELEASE | Application framework |
| Apache Camel | 2.22.3 | Integration routing |
| `netex-java-model` (Entur) | 1.0.13 | NeTEx JAXB model |
| `geojson-jackson` | 1.8.1 | GeoJSON serialisation |
| AWS Java SDK S3 | 1.11.502 | S3 upload (AWS SDK v1) |
| `gcp-storage` (Entur helpers) | 1.86 | GCS client |
| `slack` (Entur helpers) | 1.86 | Slack notifications |
| Apache HttpComponents | 4.5.2 | HTTP client |
| Logstash Logback Encoder | 5.3 | Structured JSON logging |
| Guava | 26.0-jre | Utilities |
| commons-compress | 1.18 | ZIP handling |
| JAXB API / Runtime | 2.3.0 / 2.3.0.1 | XML binding (Java 11 compat) |

### Test

| Library | Version |
|---------|---------|
| JUnit | 4.12 |
| AssertJ | 3.3.0 |
| spring-cloud-contract-wiremock | 1.2.2.RELEASE |
| camel-test-spring | 2.22.3 |

> **Note for refactoring / dependency updates:**
> All major dependencies are significantly out of date. Key upgrade targets:
> - Spring Boot `2.1.1` → `3.x` (requires Java 17+, Jakarta EE namespace migration)
> - Apache Camel `2.22.3` → `4.x` (major API changes; route DSL largely compatible)
> - AWS SDK `1.11.502` → AWS SDK v2 (`2.x`)
> - JUnit `4.12` → JUnit 5 (Jupiter)
> - Base Docker image `adoptopenjdk/openjdk11` → `eclipse-temurin:21-jre-alpine` (AdoptOpenJDK is archived)
> - `hibernate-validator` `5.2.3.Final` → `8.x` (aligns with Spring Boot 3)