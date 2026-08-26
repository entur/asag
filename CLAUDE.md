# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
# Full build + tests
mvn verify

# Skip tests
mvn package -DskipTests

# Run all tests
mvn test

# Run a single test class
mvn test -pl . -Dtest=StopPlaceToGeoJsonFeatureMapperTest

# Run a single test method
mvn test -Dtest=MapBoxUpdateRouteBuilderTest#testMapLayerDataSuccess
```

Entur's internal Artifactory (needed to resolve `org.entur.ror.helpers:*` (gcp-storage, slack), `org.entur:netex-java-model`, and the `superpom` parent itself) must be configured in your own `~/.m2/settings.xml` (server ids `central`/`snapshots` pointing at `entur2.jfrog.io`) — there is no repo-committed settings file anymore. The old `.github/workflows/settings.xml` was removed; CI (`.github/workflows/push.yml`) now runs plain `mvn verify` with no `-s` flag.

Java **25** is required (`<release>25</release>` in the compiler plugin — this repo overrides the superpom default of Java 17). If you switch JDKs between builds, run `mvn clean` first: stale `target/` classes compiled under a different JDK cause a "class file version" mismatch at test time.

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

Integration tests are plain `@SpringBootTest`-based — there is no `@ExtendWith(CamelSpringBootExtension.class)`; that class does not exist in the current `camel-test-spring-junit5`. The property `camel.springboot.use-advice-with=true` prevents the context from autostarting — it is started manually in `@BeforeEach` after route advice is applied via the static `AdviceWith.adviceWith(context, routeId, a -> ...)`.

`@EnableWireMock` (from `org.wiremock.integrations:wiremock-spring-boot`) stubs Mapbox API endpoints and the Slack webhook. The WireMock port is injected via `${wiremock.server.port}` and overrides `mapbox.api.url` to `http://localhost:${wiremock.server.port}`.

The `@ActiveProfiles("test")` annotation activates `TestConfig`, which replaces `BlobStoreService` with a Mockito mock. The mock returns a `FileInputStream` over `src/test/resources/stops.zip`.

`asag.run.on.startup=false` must be set in `@SpringBootTest` properties, otherwise `AsagApp`'s `ApplicationRunner.run()` fires the whole pipeline during context startup, before `@BeforeEach` has a chance to wire up the WireMock stubs.

`@DirtiesContext(classMode = AFTER_EACH_TEST_METHOD)` is used in `MapBoxUpdateRouteBuilderTest` because each test scenario leaves the Camel context in a different state.

---

## Key Configuration Properties

All `@Value` fields live in `MapBoxUpdateRouteBuilder`. There is no `application.properties`; all config is injected at runtime from Kubernetes ConfigMaps/Secrets. Locally, pass them as system properties or via test `properties = {...}`.

| Property | Default | Notes |
|----------|---------|-------|
| `mapbox.api.url` | `https://api.mapbox.com` | Standard HTTPS scheme for `camel-http` |
| `mapbox.upload.status.max.retries` | `20` | Set to `3` in tests |
| `mapbox.upload.status.poll.delay` | `20000` (ms) | Set to `0` in tests |
| `tiamat.export.blobstore.subdirectory` | `tiamat` | Sub-path in GCS bucket |
| `mapbox.aws.region` | `us-east-1` | |

---

## Version Stack

| Component | Version | Notes |
|-----------|---------|-------|
| Parent POM | `org.entur.ror:superpom:6.4.0` | Manages Spring Boot 4.0.6; defaults Java to 17 |
| Spring Boot | 4.0.6 | Managed by superpom |
| Apache Camel | 4.18.0 | Via `camel-bom` + `camel-spring-boot-bom` (both required in `dependencyManagement`) |
| Java | 25 | `<release>25</release>` in compiler plugin — overrides superpom's Java 17 default |
| AWS SDK | v2 (`2.42.1`) | `software.amazon.awssdk:s3`, via `software.amazon.awssdk:bom` |
| JAXB | `jakarta.xml.bind-api:4.0.5` + `com.sun.xml.bind:jaxb-impl:4.0.5` | Jakarta EE namespace |
| JUnit | 5 (Jupiter) | Managed by Spring Boot |
| `netex-java-model` | `2.0.16` | `org.entur:netex-java-model` |
| `entur.helpers` | `7.1.0` | `org.entur.ror.helpers:storage-gcp-gcs`, `:slack` |
| WireMock | `org.wiremock.integrations:wiremock-spring-boot:4.1.0` | `@EnableWireMock` in tests |

**Docker**: `eclipse-temurin:25-jre-alpine`.