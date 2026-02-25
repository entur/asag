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

The settings file configures Entur's internal Artifactory for `org.entur.ror.helpers` (gcp-storage, slack) and `org.entur:netex-java-model`. Without it, these artifacts won't resolve. Java 17+ is required (`<release>17</release>` in compiler plugin).

---

## Architecture

This is a **batch ETL job** with no HTTP server (`WebApplicationType.NONE`). It runs as a Kubernetes CronJob (daily 06:30 UTC), executes a single pipeline, then exits.

### Integration layer: Apache Camel

All orchestration lives in one file: `src/main/java/org/entur/asag/mapbox/MapBoxUpdateRouteBuilder.java`.

It defines ~13 `direct:` routes chained together. The entry point is `direct:uploadTiamatToMapboxAsGeoJson`. Route IDs (e.g. `"mapbox-convert-upload-tiamat-data"`) are used in tests to intercept and replace endpoints via `AdviceWithRouteBuilder`.

Key Camel patterns in use:
- `camel-http` for outbound HTTP — URLs use standard `http://` or `https://` scheme (Camel 4; previously `http4://`/`https4://` with `camel-http4`)
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
| Mapbox Uploads API | Write | `camel-http` (REST calls in route builder) |
| AWS S3 | Write | `software.amazon.awssdk:s3` v2 via `AwsS3Uploader` — credentials are temporary and come from Mapbox |
| Slack | Write | `org.entur.ror.helpers:slack` via `UploadStatusHubotReporter` |

---

## Testing Patterns

Integration tests use `@ExtendWith(CamelSpringBootExtension.class)` (Camel 4 / JUnit 5 pattern). The property `camel.springboot.use-advice-with=true` prevents the context from autostarting — it is started manually in `@BeforeEach` after route advice is applied via `AdviceWith.adviceWith(...)`.

The `@ActiveProfiles("test")` annotation activates `TestConfig`, which replaces `BlobStoreService` with a Mockito mock. The mock returns a `FileInputStream` over `src/test/resources/stops.zip`.

WireMock stubs Mapbox API endpoints and the Slack webhook. The WireMock port is injected via `${wiremock.server.port}` and overrides `mapbox.api.url` to `http://localhost:${wiremock.server.port}`.

`@DirtiesContext(classMode = AFTER_EACH_TEST_METHOD)` is used in `MapBoxUpdateRouteBuilderTest` because each test scenario leaves the Camel context in a different state.

---

## Key Configuration Properties

All `@Value` fields live in `MapBoxUpdateRouteBuilder`. There is no `application.properties`; all config is injected at runtime from Kubernetes ConfigMaps/Secrets. Locally, pass them as system properties or via test `properties = {...}`.

| Property | Default | Notes |
|----------|---------|-------|
| `mapbox.api.url` | `https://api.mapbox.com` | Standard HTTPS scheme for `camel-http` |
| `mapbox.upload.status.max.retries` | `20` | Set to `3` in tests |
| `mapbox.upload.status.poll.delay` | `20000` (ms) | Set to `0` in tests |
| `tiamat.export.blobstore.subdirectory` | `tiamat/geocoder` | Sub-path in GCS bucket |
| `mapbox.aws.region` | `us-east-1` | |

---

## Version Stack

| Component | Version | Notes |
|-----------|---------|-------|
| Parent POM | `org.entur.ror:superpom:4.7.0` | Manages Spring Boot 3.4.7 + Java 17 |
| Spring Boot | 3.4.7 | Managed by superpom |
| Apache Camel | 4.4.4 | Via `camel-spring-boot-bom` |
| Java | 17 | `<release>17</release>` in compiler plugin |
| AWS SDK | v2 (2.28.14) | `software.amazon.awssdk:s3` |
| JAXB | `jakarta.xml.bind-api:4.0.2` | Jakarta EE 10 namespace |
| JUnit | 5 (Jupiter) | Managed by Spring Boot 3 |

**Pending**: `entur.helpers.version` (currently `2.0.0`) and `netex-java-model.version` (currently `2.0.13`) need verification against Entur Artifactory for Spring Boot 3 / Jakarta EE compatibility.

**Docker**: Update base image from `adoptopenjdk/openjdk11:alpine-jre` to `eclipse-temurin:21-jre-alpine`.