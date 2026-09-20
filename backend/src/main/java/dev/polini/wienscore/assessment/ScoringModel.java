package dev.polini.wienscore.assessment;

import java.util.List;

/** Provisional engineering assumptions from Dataset and Geocoding Check.md, not validated research. */
final class ScoringModel {
    static final String VERSION = "student-draft-v1";
    record Rule(Dataset dataset, double distanceLimitMetres, double weight) {}
    static final List<Rule> RULES = List.of(
            new Rule(Dataset.PARKS, 1000, .25),
            new Rule(Dataset.TRANSIT, 800, .40),
            new Rule(Dataset.PHARMACIES, 1000, .10),
            new Rule(Dataset.LIBRARIES, 2000, .25));

    static double categoryScore(double distance, double limit) {
        if (!Double.isFinite(distance) || distance < 0 || !Double.isFinite(limit) || limit <= 0) {
            throw new IllegalArgumentException("Invalid distance or threshold");
        }
        return 100 * Math.clamp(1 - distance / limit, 0, 1);
    }

    static int grade(double score) {
        if (!Double.isFinite(score) || score < 0 || score > 100) throw new IllegalArgumentException("Invalid score");
        if (score >= 80) return 1;
        if (score >= 60) return 2;
        if (score >= 40) return 3;
        if (score >= 20) return 4;
        return 5;
    }
}
