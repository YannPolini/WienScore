package dev.polini.wienscore.assessment;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.time.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tools.jackson.databind.json.JsonMapper;

class DatasetCacheTest {
    @TempDir Path directory;
    WfsFixture source;
    ViennaWfs wfs;
    final JsonMapper mapper = new JsonMapper();
    final MutableClock clock = new MutableClock();
    DatasetCache cache;

    @BeforeEach void setUp() throws Exception {
        source = new WfsFixture();
        wfs = new ViennaWfs(source.url(), Duration.ofMillis(150), mapper);
        cache = newCache();
    }
    @AfterEach void tearDown() { source.close(); }
    DatasetCache newCache() { return new DatasetCache(wfs, mapper, directory, clock, Duration.ofHours(24), Duration.ofDays(7), Duration.ofMinutes(5)); }

    @Test void completeSnapshotsAreReusedInMemoryAndAfterRestart() {
        var first = cache.get(Dataset.PARKS);
        assertEquals("LIVE", first.state());
        assertEquals("CACHED", cache.get(Dataset.PARKS).state());
        var restored = newCache().get(Dataset.PARKS);
        assertEquals("CACHED", restored.state());
        assertEquals(first.snapshot().sha256(), restored.snapshot().sha256());
        assertEquals(first.snapshot().retrievedAt(), restored.snapshot().retrievedAt());
        assertEquals(1, source.requests.size());
    }

    @Test void timeoutUsesDatedFallbackOnlyUntilMaximumAgeAndBacksOffRetries() {
        var first = cache.get(Dataset.PARKS);
        clock.advance(Duration.ofHours(25));
        source.handler = q -> new WfsFixture.Response(200, WfsFixture.standard(q), 500);
        var fallback = cache.get(Dataset.PARKS);
        assertEquals("STALE_FALLBACK", fallback.state());
        assertEquals(first.snapshot().sha256(), fallback.snapshot().sha256());
        assertEquals(first.snapshot().retrievedAt(), fallback.snapshot().retrievedAt());
        cache.get(Dataset.PARKS);
        assertEquals(2, source.requests.size());
        clock.advance(Duration.ofDays(7));
        assertEquals("UNAVAILABLE", cache.get(Dataset.PARKS).state());
        assertNull(cache.get(Dataset.PARKS).snapshot());
    }

    @Test void incompleteRefreshCannotReplaceAValidSnapshot() {
        var first = cache.get(Dataset.PARKS);
        clock.advance(Duration.ofHours(25));
        source.handler = q -> new WfsFixture.Response(200, WfsFixture.collection(5,
                WfsFixture.point("one", "{\"ANL_NAME\":\"Park\"}", 16.3, 48.2)), 0);
        assertEquals("STALE_FALLBACK", cache.get(Dataset.PARKS).state());
        assertEquals(first.snapshot().sha256(), newCache().get(Dataset.PARKS).snapshot().sha256());
    }

    @ParameterizedTest @EnumSource(ScoringModel.Profile.class)
    void incompleteOrInvalidMandatoryDataWithNoCacheWithholdsTheGrade(ScoringModel.Profile profile) {
        source.handler = q -> q.get("typeName").endsWith("PARKINFOOGD")
                ? new WfsFixture.Response(200, WfsFixture.collection(7), 0)
                : new WfsFixture.Response(200, WfsFixture.standard(q), 0);
        var result = new AssessmentService(new AddressLookup(wfs), cache).assess("Hoher Markt 1", profile);
        assertEquals("DATA_UNAVAILABLE", result.status());
        assertNull(result.grade());
        assertNull(result.score());
        assertTrue(result.message().contains("Parks"));
        assertEquals(3, result.factors().size());
        assertEquals(profile.label, result.profile());
        assertEquals(ScoringModel.rules(profile).get(1).weight(), result.factors().getFirst().weight());
    }

    @ParameterizedTest @EnumSource(ScoringModel.Profile.class)
    void assessmentDisclosesFallbackAndWithholdsGradeAtExactCacheExpiry(ScoringModel.Profile profile) {
        var service = new AssessmentService(new AddressLookup(wfs), cache);
        var first = service.assess("Hoher Markt 1", profile);
        assertEquals("OK", first.status());
        clock.advance(Duration.ofHours(25));
        source.handler = q -> q.get("typeName").endsWith("ADRESSENOGD")
                ? new WfsFixture.Response(200, WfsFixture.standard(q), 0) : new WfsFixture.Response(503, "down", 0);
        var fallback = service.assess("Hoher Markt 1", profile);
        assertEquals(first.score(), fallback.score());
        assertTrue(fallback.sources().stream().allMatch(s -> s.state().equals("STALE_FALLBACK")));
        for (int i = 0; i < 5; i++) {
            assertEquals(first.sources().get(i).retrievedAt(), fallback.sources().get(i).retrievedAt());
            assertEquals(first.sources().get(i).sha256(), fallback.sources().get(i).sha256());
        }
        clock.advance(Duration.ofHours(143)); // Exactly seven days after the original retrieval.
        var expired = service.assess("Hoher Markt 1", profile);
        assertEquals("DATA_UNAVAILABLE", expired.status());
        assertNull(expired.grade());
        assertNull(expired.score());
    }

    @Test void unavailableBoundaryBlocksScoringBeforeAmenitiesLoad() {
        source.handler = q -> q.get("typeName").endsWith("BEZIRKSGRENZEOGD")
                ? new WfsFixture.Response(503, "down", 0)
                : new WfsFixture.Response(200, WfsFixture.standard(q), 0);
        var result = new AssessmentService(new AddressLookup(wfs), cache).assess("Hoher Markt 1", ScoringModel.Profile.STUDENT);
        assertEquals("DATA_UNAVAILABLE", result.status());
        assertNull(result.grade());
        assertEquals(2, source.requests.size());
    }

    @Test void corruptCacheAndFailedSourceDoNotProduceASnapshot() throws Exception {
        cache.get(Dataset.PARKS);
        Path file = directory.resolve("PARKINFOOGD.json");
        Files.writeString(file, Files.readString(file).replace("Near feature", "Changed name"));
        source.handler = q -> new WfsFixture.Response(503, "down", 0);
        assertNull(newCache().get(Dataset.PARKS).snapshot());
    }

    @Test void invalidCoordinatesDuplicateIdsUnknownCrsAndMissingDistrictsAreRejected() {
        String invalid = WfsFixture.point("id", "{\"ANL_NAME\":\"Park\"}", 16, 200);
        assertThrows(IllegalArgumentException.class, () -> cache.validate(Dataset.PARKS, wfs.parse(WfsFixture.collection(1, invalid)), clock.instant()));
        String point = WfsFixture.point("id", "{\"ANL_NAME\":\"Park\"}", 16, 48);
        assertThrows(IllegalArgumentException.class, () -> cache.validate(Dataset.PARKS, wfs.parse(WfsFixture.collection(2, point, point)), clock.instant()));
        assertThrows(IllegalArgumentException.class, () -> wfs.parse(WfsFixture.collection(1, point).replace("4326", "31256")));
        assertThrows(IllegalArgumentException.class, () -> cache.validate(Dataset.BOUNDARY, wfs.parse(WfsFixture.collection(0)), clock.instant()));
    }

    @Test void unnamedPointsAreRetainedAndDisclosed() {
        var snapshot = cache.validate(Dataset.TRANSIT, wfs.parse(WfsFixture.collection(1,
                WfsFixture.point("stop.1", "{\"BEZEICHNUNG\":\" \"}", 16, 48))), clock.instant());
        assertEquals(1, snapshot.features().size());
        assertEquals(1, snapshot.unnamedFeatures());
        assertEquals("Unnamed feature (stop.1)", snapshot.features().getFirst().name());
    }

    static class MutableClock extends Clock {
        Instant time = Instant.parse("2026-09-17T07:00:00Z");
        void advance(Duration duration) { time = time.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return time; }
    }
}
