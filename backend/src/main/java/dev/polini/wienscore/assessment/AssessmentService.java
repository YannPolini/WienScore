package dev.polini.wienscore.assessment;

import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
class AssessmentService {
    private final AddressLookup lookup;
    private final DatasetCache cache;
    record Source(String category, String layer, String url, String attribution, String license,
                  Instant retrievedAt, String sha256, Integer featureCount, Integer unnamedFeatures, String state, String message, String limitation) {}
    record Factor(String category, String nearestId, String nearestName, double distanceMetres,
                  double distanceLimitMetres, double score, double weight, double contribution) {}
    record Assessment(String status, String message, String profile, String profileDescription, String modelVersion,
                      AddressLookup.Match location, List<AddressLookup.Match> candidates, Instant addressRetrievedAt,
                      Double score, Integer grade, List<Factor> factors, List<Source> sources) {}

    AssessmentService(AddressLookup lookup, DatasetCache cache) { this.lookup = lookup; this.cache = cache; }

    Assessment assess(String address, ScoringModel.Profile profile) {
        var resolution = lookup.resolve(address);
        if (!resolution.status().equals("RESOLVED")) {
            return result(profile, resolution.status(), resolution.message(), null, resolution.matches(), resolution.retrievedAt(), null, List.of(), List.of());
        }
        var location = resolution.matches().getFirst();
        var sources = new ArrayList<Source>();
        var boundary = cache.get(Dataset.BOUNDARY);
        sources.add(source(Dataset.BOUNDARY, boundary));
        if (boundary.snapshot() == null) return result(profile, "DATA_UNAVAILABLE", "Vienna's boundary could not be verified. No grade is available.", location, List.of(), resolution.retrievedAt(), null, List.of(), sources);
        boolean inside = boundary.snapshot().features().stream().anyMatch(f -> Geo.covers(f.polygon(), location.point()));
        if (!inside) return result(profile, "OUTSIDE_VIENNA", "The resolved location is outside Vienna. No Vienna grade is available.", location, List.of(), resolution.retrievedAt(), null, List.of(), sources);

        var factors = new ArrayList<Factor>();
        var missing = new ArrayList<String>();
        for (var rule : ScoringModel.rules(profile)) {
            var view = cache.get(rule.dataset());
            sources.add(source(rule.dataset(), view));
            if (view.snapshot() == null) { missing.add(rule.dataset().label); continue; }
            var nearest = view.snapshot().features().stream().min(
                    Comparator.comparingDouble((DatasetCache.Feature f) -> Geo.distanceMetres(location.point(), f.point()))
                            .thenComparing(DatasetCache.Feature::id)).orElseThrow();
            double distance = Geo.distanceMetres(location.point(), nearest.point());
            double categoryScore = ScoringModel.categoryScore(distance, rule.distanceLimitMetres());
            factors.add(new Factor(rule.dataset().label, nearest.id(), nearest.name(), distance,
                    rule.distanceLimitMetres(), categoryScore, rule.weight(), categoryScore * rule.weight()));
        }
        if (!missing.isEmpty()) return result(profile, "DATA_UNAVAILABLE", "No overall grade: unavailable data for " + String.join(", ", missing) + ". Available factors below are not a partial overall score.", location, List.of(), resolution.retrievedAt(), null, factors, sources);
        double score = factors.stream().mapToDouble(Factor::contribution).sum();
        return result(profile, "OK", "Draft " + profile.label.toLowerCase(Locale.ROOT) + " estimate using straight-line distances. Grade 1 is best; 5 is worst.", location, List.of(), resolution.retrievedAt(), score, factors, sources);
    }

    private Assessment result(ScoringModel.Profile profile, String status, String message, AddressLookup.Match location, List<AddressLookup.Match> candidates,
                              Instant addressTime, Double score, List<Factor> factors, List<Source> sources) {
        return new Assessment(status, message, profile.label, profile.description, ScoringModel.VERSION, location, candidates, addressTime,
                score, score == null ? null : ScoringModel.grade(score), List.copyOf(factors), List.copyOf(sources));
    }

    private Source source(Dataset dataset, DatasetCache.View view) {
        var snapshot = view.snapshot();
        return new Source(dataset.label, dataset.layer, dataset.sourceUrl(), "Datenquelle: Stadt Wien – https://data.wien.gv.at",
                "CC BY 4.0", snapshot == null ? null : snapshot.retrievedAt(),
                snapshot == null ? null : snapshot.sha256(), snapshot == null ? null : snapshot.features().size(),
                snapshot == null ? null : snapshot.unnamedFeatures(),
                view.state(), view.message(), dataset.limitation);
    }
}
