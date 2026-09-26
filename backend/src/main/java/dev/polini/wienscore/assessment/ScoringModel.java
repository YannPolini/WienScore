package dev.polini.wienscore.assessment;

import java.util.List;
import java.util.Locale;

/** Provisional engineering assumptions from Dataset and Geocoding Check.md, not validated research. */
final class ScoringModel {
    static final String VERSION = "profiles-draft-v1";
    enum Profile {
        STUDENT("Student", "This draft gives more weight to public transport and municipal libraries as proxies for getting around and access to study resources."),
        SENIOR("Senior", "This draft gives more weight to parks and pharmacies as proxies for nearby outdoor space and pharmacy access. It does not assess mobility needs or medical care.");

        final String label;
        final String description;
        Profile(String label, String description) { this.label = label; this.description = description; }

        static Profile parse(String value) {
            // Missing profile keeps the original address-only API usable. Invalid values never silently default.
            if (value == null) return STUDENT;
            try { return valueOf(value.strip().toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ex) { throw new IllegalArgumentException("Choose Student or Senior as the profile."); }
        }
    }
    record Rule(Dataset dataset, double distanceLimitMetres, double weight) {}
    private static final List<Rule> STUDENT_RULES = List.of(
            new Rule(Dataset.PARKS, 1000, .25),
            new Rule(Dataset.TRANSIT, 800, .40),
            new Rule(Dataset.PHARMACIES, 1000, .10),
            new Rule(Dataset.LIBRARIES, 2000, .25));
    private static final List<Rule> SENIOR_RULES = List.of(
            new Rule(Dataset.PARKS, 1000, .35),
            new Rule(Dataset.TRANSIT, 800, .25),
            new Rule(Dataset.PHARMACIES, 1000, .35),
            new Rule(Dataset.LIBRARIES, 2000, .05));

    static List<Rule> rules(Profile profile) {
        return switch (profile) {
            case STUDENT -> STUDENT_RULES;
            case SENIOR -> SENIOR_RULES;
        };
    }

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
