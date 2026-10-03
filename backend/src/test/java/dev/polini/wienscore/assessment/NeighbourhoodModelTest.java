package dev.polini.wienscore.assessment;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.time.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class NeighbourhoodModelTest {
    @TempDir Path directory;
    WfsFixture source;
    ViennaWfs wfs;
    DatasetCache cache;
    AssessmentService service;
    @BeforeEach void setup() throws Exception {
        source = new WfsFixture();
        var mapper = new JsonMapper();
        wfs = new ViennaWfs(source.url(), Duration.ofSeconds(2), mapper);
        cache = new DatasetCache(wfs, mapper, directory, Clock.systemUTC(), Duration.ofHours(24), Duration.ofDays(7), Duration.ofMinutes(5));
        service = new AssessmentService(new AddressLookup(wfs), cache);
    }
    @AfterEach void stop() { source.close(); }

    @Test void gpSelectionExcludesCloserSurgeonAndIncludesBothGpSpecialties() {
        var view = cache.get(Dataset.DOCTORS);
        assertEquals(2, view.snapshot().features().size());
        assertTrue(view.snapshot().features().stream().noneMatch(f -> f.id().contains("surgeon")));
        var health = service.assess("Hoher Markt 1", ScoringModel.Profile.STUDENT).factors().get(2);
        assertEquals("Primary healthcare", health.category());
        assertEquals("doctor.near", health.measures().get(1).nearestId());
        assertTrue(health.score() > 80 && health.score() < 100);
        assertTrue(health.measures().stream().allMatch(AssessmentService.Measure::withinTarget));
    }

    @Test void transitUsesBetterModeScoreAndExcludesNightOnlyStops() {
        source.handler = q -> new WfsFixture.Response(200, q.get("typeName").endsWith("OEFFHALTESTOGD")
                ? WfsFixture.collection(3,
                    WfsFixture.point("night", "{\"HTXT\":\"\",\"LTYP\":10}", 16.37307549, 48.21053772),
                    WfsFixture.point("bus", "{\"HTXT\":\"Bus\",\"LTYP\":2}", 16.37307549, 48.22),
                    WfsFixture.point("rail", "{\"HTXT\":\"Rail\",\"LTYP\":5}", 16.37307549, 48.217)) : WfsFixture.standard(q), 0);
        var result = service.assess("Hoher Markt 1", ScoringModel.Profile.STUDENT);
        var transport = result.factors().get(1);
        assertEquals("MAX", transport.aggregation());
        assertTrue(transport.measures().getFirst().score() < 50);
        assertEquals(transport.measures().get(1).score(), transport.score());
        assertTrue(transport.score() > 80 && transport.score() < 100);
        assertEquals("rail", transport.measures().get(1).nearestId());
        assertEquals(2, cache.get(Dataset.TRANSIT).snapshot().features().size());
        assertEquals(0, cache.get(Dataset.TRANSIT).snapshot().unnamedFeatures());
        assertThrows(IllegalArgumentException.class, () -> cache.validate(Dataset.TRANSIT,
                wfs.parse(WfsFixture.collection(1, WfsFixture.point("invalid", "{\"LTYP\":99}", 16.3, 48.2))), Instant.now()));
    }

    @Test void twoAddressesMeetingEveryBenchmarkDoNotCollapseToPerfectScores() {
        boolean[] secondAddress = {false};
        source.handler = q -> {
            String layer = q.get("typeName");
            String body = layer.endsWith("BUECHEREIOGD")
                    ? WfsFixture.collection(1, WfsFixture.point("library", "{\"NAME\":\"Library\"}", 16.374, 48.211))
                    : layer.endsWith("ADRESSENOGD") && secondAddress[0]
                    ? WfsFixture.collection(1, WfsFixture.address(16.375, 48.212)) : WfsFixture.standard(q);
            return new WfsFixture.Response(200, body, 0);
        };
        for (var profile : ScoringModel.Profile.values()) {
            secondAddress[0] = false;
            var first = service.assess("Hoher Markt 1", profile);
            secondAddress[0] = true;
            var second = service.assess("Hoher Markt 1", profile);
            for (var result : java.util.List.of(first, second)) {
                assertEquals("OK", result.status());
                assertTrue(result.factors().stream().flatMap(f -> f.measures().stream()).allMatch(AssessmentService.Measure::withinTarget));
                assertTrue(result.score() < 100);
                assertEquals(result.score(), result.sensitivity().stricterScore());
                assertEquals(result.score(), result.sensitivity().looserScore());
            }
            assertNotEquals(first.score(), second.score());
        }
    }

    @Test void missingDoctorsNeverLeavesAPharmacyOnlyGrade() {
        source.handler = q -> new WfsFixture.Response(200, q.get("typeName").endsWith("ARZTOGD")
                ? WfsFixture.collection(2) : WfsFixture.standard(q), 0);
        var result = service.assess("Hoher Markt 1", ScoringModel.Profile.SENIOR);
        assertEquals("DATA_UNAVAILABLE", result.status());
        assertNull(result.score());
        assertNull(result.sensitivity());
        assertEquals(3, result.factors().size());
        assertTrue(result.sources().stream().anyMatch(s -> s.layer().equals("ARZTOGD") && s.state().equals("UNAVAILABLE")));
    }

    @Test void greenScalesUseAreaAndDoNotDropUnmetTargetsFromAverage() {
        source.handler = q -> new WfsFixture.Response(200, q.get("typeName").endsWith("OEFFGRUENFLOGD")
                ? WfsFixture.collection(1, WfsFixture.green("small.1", 10000, 16.374)) : WfsFixture.standard(q), 0);
        var result = service.assess("Hoher Markt 1", ScoringModel.Profile.STUDENT);
        var green = result.factors().getFirst();
        double greenDistance = green.measures().getFirst().distanceMetres();
        double expectedGreen = ((100 - 20 * greenDistance / 250) + (100 - 20 * greenDistance / 500)) / 4;
        assertEquals(expectedGreen, green.score(), 1e-10);
        assertTrue(green.measures().getFirst().withinTarget());
        assertFalse(green.measures().get(2).withinTarget());
        assertEquals(4, green.measures().size());
        assertEquals("NO_QUALIFYING_FEATURE", green.measures().get(2).state());
        assertNull(green.measures().get(2).distanceMetres());
        double libraryDistance = Geo.distanceMetres(result.location().point(), new Geo.Location(16.374, 48.224));
        double libraryScore = 80 * (3000 - libraryDistance) / 2000;
        double nearDistance = Geo.distanceMetres(result.location().point(), new Geo.Location(16.374, 48.211));
        double transportScore = 100 - 20 * nearDistance / 1000;
        double healthScore = ((100 - 20 * nearDistance / 500) + (100 - 20 * nearDistance / 1000)) / 2;
        assertEquals(.3125 * expectedGreen + .375 * transportScore + .1875 * healthScore + .125 * libraryScore, result.score(), 1e-9);
        assertTrue(result.sensitivity().stricterScore() < result.score());
        assertTrue(result.sensitivity().looserScore() > result.score());
        assertTrue(result.sensitivity().stricterGrade() >= result.grade());
        assertTrue(result.sensitivity().looserGrade() <= result.grade());
    }

    @Test void missingAreaOrSpecialtyIsInvalidNotAZeroOrPerfectScore() {
        assertThrows(IllegalArgumentException.class, () -> cache.validate(Dataset.PARKS,
                wfs.parse(WfsFixture.collection(1, WfsFixture.green("invalid", -1, 16.374))), Instant.now()));
        assertThrows(IllegalArgumentException.class, () -> cache.validate(Dataset.DOCTORS,
                wfs.parse(WfsFixture.collection(1, WfsFixture.point("invalid", "{}", 16.3, 48.2))), Instant.now()));
    }

    @Test void metricPolygonDistancesRespectInteriorAndHoles() {
        var polygon = Geo.metricGeometry(Geo.polygonGeometry(new JsonMapper().readTree("""
                {"type":"Polygon","coordinates":[[[16.37,48.2],[16.38,48.2],[16.38,48.21],[16.37,48.21],[16.37,48.2]],
                [[16.372,48.202],[16.372,48.208],[16.378,48.208],[16.378,48.202],[16.372,48.202]]]}
                """)));
        assertEquals(0, polygon.distance(Geo.metricPoint(new Geo.Location(16.371, 48.201))));
        assertTrue(polygon.distance(Geo.metricPoint(new Geo.Location(16.375, 48.205))) > 200);
        assertEquals(111.195, polygon.distance(Geo.metricPoint(new Geo.Location(16.371, 48.199))), .01);
    }

    @Test void allDecayScenariosAreMonotonicAndBounded() {
        for (double target : new double[]{250, 500, 1000, 1500}) for (double multiplier : new double[]{2, 3, 4}) {
            double previous = 100;
            for (double distance = 0; distance < 10_000; distance += 10) {
                double score = ScoringModel.categoryScore(distance, target, multiplier);
                assertTrue(score >= 0 && score <= previous);
                previous = score;
            }
        }
    }
}
