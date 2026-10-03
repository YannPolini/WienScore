package dev.polini.wienscore.assessment;

import java.util.List;
import java.util.Locale;

/** Evidence-informed proximity indicators; weights and decay are explicit design assumptions. */
final class ScoringModel {
    static final String VERSION = "neighbourhood-proximity-v3";
    enum Profile {
        STUDENT("Student", "Prioritises transport and local learning resources. Weights express preferences, not measured scientific effects."),
        SENIOR("Senior", "Prioritises nearby green space and primary healthcare. Weights express preferences; mobility needs and care availability are not assessed.");

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
    record Rule(Dataset dataset, double targetMetres, double weight) {}
    // Fixed four-domain version: the proposed five-domain weights divided by 0.8.
    // Noise is outside this version, not a missing factor dynamically redistributed at runtime.
    private static final List<Rule> STUDENT_RULES = List.of(
            new Rule(Dataset.PARKS, 250, .3125),
            new Rule(Dataset.TRANSIT, 500, .375),
            new Rule(Dataset.PHARMACIES, 500, .1875),
            new Rule(Dataset.LIBRARIES, 1000, .125));
    private static final List<Rule> SENIOR_RULES = List.of(
            new Rule(Dataset.PARKS, 250, .3125),
            new Rule(Dataset.TRANSIT, 500, .25),
            new Rule(Dataset.PHARMACIES, 500, .375),
            new Rule(Dataset.LIBRARIES, 1000, .0625));

    static List<Rule> rules(Profile profile) {
        return switch (profile) {
            case STUDENT -> STUDENT_RULES;
            case SENIOR -> SENIOR_RULES;
        };
    }

    static double categoryScore(double distance, double target) {
        return categoryScore(distance, target, 3);
    }

    static double categoryScore(double distance, double target, double cutoffMultiplier) {
        if (!Double.isFinite(distance) || distance < 0 || !Double.isFinite(target) || target <= 0
                || !Double.isFinite(cutoffMultiplier) || cutoffMultiplier <= 1) {
            throw new IllegalArgumentException("Invalid distance or threshold");
        }
        // A benchmark is a reference distance, not a full-score plateau.
        // 80 at the benchmark is a declared proximity-utility convention, not a research finding.
        double ratio = distance / target;
        if (ratio <= 1) return 100 - 20 * ratio;
        return 80 * Math.clamp((cutoffMultiplier - ratio) / (cutoffMultiplier - 1), 0, 1);
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
