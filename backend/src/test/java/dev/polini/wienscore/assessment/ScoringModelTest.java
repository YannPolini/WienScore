package dev.polini.wienscore.assessment;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

class ScoringModelTest {
    @ParameterizedTest
    @CsvSource({"0,5", "19.99,5", "20,4", "39.99,4", "40,3", "59.99,3", "60,2", "79.99,2", "80,1", "100,1"})
    void gradeBoundaries(double score, int grade) { assertEquals(grade, ScoringModel.grade(score)); }

    @Test void continuousScoresAtEveryBenchmark() {
        for (var profile : ScoringModel.Profile.values()) for (var rule : ScoringModel.rules(profile)) {
            double d = rule.targetMetres();
            assertEquals(100, ScoringModel.categoryScore(0, d));
            assertEquals(90, ScoringModel.categoryScore(d / 2, d));
            assertEquals(80, ScoringModel.categoryScore(d, d));
            assertEquals(40, ScoringModel.categoryScore(d * 2, d));
            assertEquals(0, ScoringModel.categoryScore(d * 3, d));
            assertEquals(0, ScoringModel.categoryScore(d * 4, d));
        }
    }

    @ParameterizedTest
    @CsvSource({"STUDENT,62.5,2", "SENIOR,50,3"})
    void profileWeightsMatchDocumentedControlledExample(ScoringModel.Profile profile, double expected, int grade) {
        double[] categoryScores = {100, 50, 0, 100};
        var rules = ScoringModel.rules(profile);
        double result = 0;
        for (int i = 0; i < categoryScores.length; i++) result += categoryScores[i] * rules.get(i).weight();
        assertEquals(1, rules.stream().mapToDouble(ScoringModel.Rule::weight).sum(), 1e-12);
        assertEquals(expected, result, 1e-12);
        assertEquals(grade, ScoringModel.grade(result));
        assertEquals(4, rules.stream().map(ScoringModel.Rule::dataset).distinct().count());
        assertTrue(rules.stream().allMatch(rule -> rule.weight() > 0));
    }

    @Test void withinBenchmarkDistancesRemainDistinctAndContinuous() {
        assertEquals(99.2, ScoringModel.categoryScore(20, 500), 1e-10);
        assertEquals(84, ScoringModel.categoryScore(400, 500), 1e-10);
        for (double cutoff : new double[]{2, 3, 4}) {
            double previous = 100;
            for (int d = 1; d < 500 * cutoff; d++) {
                double score = ScoringModel.categoryScore(d, 500, cutoff);
                assertTrue(score < previous, "Every additional metre before the cutoff must reduce the score");
                previous = score;
            }
            assertEquals(80, ScoringModel.categoryScore(500 - 1e-6, 500, cutoff), 1e-6);
            assertEquals(80, ScoringModel.categoryScore(500 + 1e-6, 500, cutoff), 1e-6);
        }
    }

    @Test void invalidMeasurementsCannotBecomeFavourableScores() {
        assertThrows(IllegalArgumentException.class, () -> ScoringModel.categoryScore(Double.NaN, 1000));
        assertThrows(IllegalArgumentException.class, () -> ScoringModel.categoryScore(-1, 1000));
        assertThrows(IllegalArgumentException.class, () -> ScoringModel.categoryScore(1, 0));
        assertThrows(IllegalArgumentException.class, () -> ScoringModel.grade(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> ScoringModel.grade(101));
    }

    @Test void haversineHasKnownScaleAndSymmetry() {
        var a = new Geo.Location(0, 0);
        var b = new Geo.Location(0, 1);
        assertEquals(0, Geo.distanceMetres(a, a));
        assertEquals(111_194.93, Geo.distanceMetres(a, b), .01);
        assertEquals(Geo.distanceMetres(a, b), Geo.distanceMetres(b, a));
    }

    @Test void polygonHolesAndEdgesAreRespected() {
        var polygon = Geo.polygonGeometry(new JsonMapper().readTree("""
                {"type":"Polygon","coordinates":[[[0,0],[4,0],[4,4],[0,4],[0,0]],[[1,1],[1,3],[3,3],[3,1],[1,1]]]}
                """));
        assertTrue(Geo.covers(polygon, new Geo.Location(.5, .5)));
        assertTrue(Geo.covers(polygon, new Geo.Location(0, 2)));
        assertFalse(Geo.covers(polygon, new Geo.Location(2, 2)));
        assertFalse(Geo.covers(polygon, new Geo.Location(5, 2)));
    }
}
