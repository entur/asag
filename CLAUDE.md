# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
# Full build + tests (requires Entur Artifactory credentials)
mvn verify -s .github/workflows/settings.xml

# Skip tests
mvn package -DskipTests -s .github/workflows/settings.xml

# Run all tests
mvn test -s .github/workflows/settings.xml

# Run a single test class
mvn test -pl . -Dtest=StopPlaceToGeoJsonFeatureMapperTest -s .github/workflows/settings.xml

# Run a single test method
mvn test -Dtest=MapBoxUpdateRouteBuilderTest#testMapLayerDataSuccess -s .github/workflows/settings.xml
```

The settings file configures Entur's internal Artifactory for `org.entur.ror.helpers` (gcp-storage, slack) and `org.entur:netex-java-model`. Without it, these artifacts won't resolve. Java 11 is required (`<release>11</release>` in compiler plugin).

---

## Architecture

This is a **batch ETL job** with no HTTP server (`WebApplicationType.NONE`). It runs as a Kubernetes CronJob (daily 06:30 UTC), executes a single pipeline, then exits.

### Integration layer: Apache Camel

All orchestration lives in one file: `src/main/java/org/entur/asag/mapbox/MapBoxUpdateRouteBuilder.java`.

It defines ~13 `direct:` routes chained together. The entry point is `direct:uploadTiamatToMapboxAsGeoJson`. Route IDs (e.g. `"mapbox-convert-upload-tiamat-data"`) are used in tests to intercept and replace endpoints via `AdviceWithRouteBuilder`.

Key Camel patterns in use:
- `camel-http4` (not `camel-http`) for outbound HTTP — URLs must use `http4://` or `https4://` scheme
- `loopDoWhile` for the Mapbox upload status polling loop
- Exchange properties (`PROPERTY_STATE`) carry final job outcome: `finished`, `error`, or `timeout`
- `direct:uploadMapboxDataAws` is replaced with a mock in integration tests to skip real S3 uploads

### Transformation layer: NeTEx → GeoJSON

`DeliveryPublicationStreamToGeoJson` is a **stateful `@Service`** — it accumulates `stopPlaces`, `parkings`, and `tariffZones` in instance-level `Set`/`Map` fields during XML streaming, then flushes them all to a single GeoJSON `OutputStream` at the end. This means it is **not thread-safe** and **not reusable across multiple calls** without re-initialisation.

The XML is parsed by streaming (`XMLEventReader`) and only three element names trigger JAXB unmarshalling: `StopPlace`, `Parking`, `TariffZone`. Quays are not streamed directly — they are nested inside `StopPlace` and extracted via the stop place's `getQuays()` after unmarshalling.

The `finalStopPlaceType` property on stop-place GeoJSON features is computed from adjacent-site types joined with `_` (e.g. `bus_railStation`). Submode takes precedence over `stopPlaceType`.

### External integrations

| Service | Direction | Library |
|---------|-----------|---------|
| Google Cloud Storage | Read | `org.entur.ror.helpers:gcp-storage` via `BlobStoreService` |
| Mapbox Uploads API | Write | `camel-http4` (REST calls in route builder) |
| AWS S3 | Write | `aws-java-sdk-s3` v1 via `AwsS3Uploader` — credentials are temporary and come from Mapbox |
| Slack | Write | `org.entur.ror.helpers:slack` via `UploadStatusHubotReporter` |

---

## Testing Patterns

Integration tests use `@RunWith(CamelSpringRunner.class)` + `@UseAdviceWith` (Camel 2.x pattern). The context must **not** autostart — it is started manually in `@Before` after route advice is applied. Forgetting this causes routes to start before mocks are wired.

The `@ActiveProfiles("test")` annotation activates `TestConfig`, which replaces `BlobStoreService` with a Mockito mock. The mock returns a `FileInputStream` over `src/test/resources/stops.zip`.

WireMock stubs Mapbox API endpoints and the Slack webhook. The WireMock port is injected via `${wiremock.server.port}` and overrides `mapbox.api.url` to `http4://localhost:${wiremock.server.port}`.

`@DirtiesContext(classMode = AFTER_EACH_TEST_METHOD)` is used in `MapBoxUpdateRouteBuilderTest` because each test scenario leaves the Camel context in a different state.

---

## Key Configuration Properties

All `@Value` fields live in `MapBoxUpdateRouteBuilder`. There is no `application.properties`; all config is injected at runtime from Kubernetes ConfigMaps/Secrets. Locally, pass them as system properties or via test `properties = {...}`.

| Property | Default | Notes |
|----------|---------|-------|
| `mapbox.api.url` | `https4://api.mapbox.com` | Must use `https4://` scheme for camel-http4 |
| `mapbox.upload.status.max.retries` | `20` | Set to low value in tests |
| `mapbox.upload.status.poll.delay` | `20000` (ms) | Set to `0` in tests |
| `tiamat.export.blobstore.subdirectory` | `tiamat/geocoder` | Sub-path in GCS bucket |
| `mapbox.aws.region` | `us-east-1` | Note: `@Value` string has a bug — missing closing `}` in source |

---

## Dependency Upgrade Notes

The codebase is significantly behind current versions. Major constraints for upgrades:

- **Spring Boot 2.1.1 → 3.x**: requires Java 17+, `javax.*` → `jakarta.*` namespace migration across all imports (JAXB, Camel, validators)
- **Camel 2.22.3 → 4.x**: `camel-http4` is replaced by `camel-http`; `SpringRouteBuilder` → `RouteBuilder`; `CamelSpringRunner` → Camel Spring Boot test support changes; `ModelCamelContext` API changes
- **AWS SDK v1 → v2**: `AmazonS3` → `S3Client`; credentials model changes; `AwsS3Uploader` needs full rewrite
- **Docker base image**: `adoptopenjdk/openjdk11:alpine-jre` is archived — replace with `eclipse-temurin:21-jre-alpine`
- **JAXB**: Explicit `jaxb-api`/`jaxb-runtime` dependencies are needed for Java 11+ (already present); Jakarta EE 10 moves to `jakarta.xml.bind`
- **JUnit 4 → 5**: `@RunWith` → `@ExtendWith`; Camel's `CamelSpringRunner` has a JUnit 5 equivalent in newer Camel