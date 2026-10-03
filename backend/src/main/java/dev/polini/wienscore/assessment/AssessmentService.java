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
    record Measure(String label, String nearestId, String nearestName, Double distanceMetres,
                   double targetMetres, double distanceLimitMetres, double score, boolean withinTarget, String state) {}
    record Factor(String category, double score, double weight, double contribution, String aggregation, String method, List<Measure> measures) {}
    record Sensitivity(double stricterScore, int stricterGrade, double looserScore, int looserGrade) {}
    record Assessment(String status, String message, String profile, String profileDescription, String modelVersion,
                      AddressLookup.Match location, List<AddressLookup.Match> candidates, Instant addressRetrievedAt,
                      Double score, Integer grade, List<Factor> factors, List<Source> sources, Sensitivity sensitivity) {}

    AssessmentService(AddressLookup lookup, DatasetCache cache) { this.lookup = lookup; this.cache = cache; }

    Assessment assess(String address, ScoringModel.Profile profile) {
        return assess(address, null, profile);
    }

    Assessment assess(String address, String addressId, ScoringModel.Profile profile) {
        var resolution = addressId == null ? lookup.resolve(address) : lookup.resolveId(addressId);
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
            var measures = new ArrayList<Measure>();
            String category = rule.dataset().label;
            String method;
            if (rule.dataset() == Dataset.PARKS) {
                measures.add(measure("Any public green space", view.snapshot(), location.point(), 250, 0));
                measures.add(measure("Green space ≥ 1 ha", view.snapshot(), location.point(), 500, 10_000));
                measures.add(measure("Green space ≥ 3 ha", view.snapshot(), location.point(), 1000, 30_000));
                measures.add(measure("Green space ≥ 10 ha", view.snapshot(), location.point(), 1500, 100_000));
                method = "Mean of four size-and-distance scores. Distances reach mapped boundaries, not entrances; one space may meet several size targets.";
            } else if (rule.dataset() == Dataset.PHARMACIES) {
                category = "Primary healthcare";
                var doctors = cache.get(Dataset.DOCTORS);
                sources.add(source(Dataset.DOCTORS, doctors));
                if (doctors.snapshot() == null) { missing.add(Dataset.DOCTORS.label); continue; }
                measures.add(measure("Pharmacy", view.snapshot(), location.point(), 500, 0));
                measures.add(measure("General practitioner", doctors.snapshot(), location.point(), 1000, 0));
                method = "Mean of pharmacy and general-practitioner proximity. Does not measure care quality or availability.";
            } else if (rule.dataset() == Dataset.TRANSIT) {
                measures.add(measure("Bus / tram", view.snapshot(), location.point(), 500, 0,
                        f -> Set.of(1, 2, 3, 6).contains(f.transportType())));
                measures.add(measure("U-Bahn / regional rail", view.snapshot(), location.point(), 1000, 0,
                        f -> Set.of(4, 5).contains(f.transportType())));
                method = "Better of the bus/tram and rail proximity scores. Straight-line proxies for UN walking-distance targets; no service-frequency assessment.";
            } else {
                measures.add(measure(rule.dataset().label, view.snapshot(), location.point(), rule.targetMetres(), 0));
                method = "Nearest municipal library; other libraries and study-space capacity are not measured.";
            }
            String aggregation = rule.dataset() == Dataset.TRANSIT ? "MAX" : "MEAN";
            double categoryScore = aggregate(measures.stream().mapToDouble(Measure::score), aggregation);
            factors.add(new Factor(category, categoryScore, rule.weight(), categoryScore * rule.weight(), aggregation, method, List.copyOf(measures)));
        }
        if (!missing.isEmpty()) return result(profile, "DATA_UNAVAILABLE", "No overall grade: unavailable data for " + String.join(", ", missing) + ". Available factors below are not a partial overall score.", location, List.of(), resolution.retrievedAt(), null, factors, sources);
        double score = factors.stream().mapToDouble(Factor::contribution).sum();
        return result(profile, "OK", "Neighbourhood proximity estimate using straight-line distances. Grade 1 is best; 5 is worst. Noise and walking routes are not measured.", location, List.of(), resolution.retrievedAt(), score, factors, sources);
    }

    private Assessment result(ScoringModel.Profile profile, String status, String message, AddressLookup.Match location, List<AddressLookup.Match> candidates,
                              Instant addressTime, Double score, List<Factor> factors, List<Source> sources) {
        return new Assessment(status, message, profile.label, profile.description, ScoringModel.VERSION, location, candidates, addressTime,
                score, score == null ? null : ScoringModel.grade(score), List.copyOf(factors), List.copyOf(sources),
                score == null ? null : sensitivity(factors));
    }

    private Measure measure(String label, DatasetCache.Snapshot snapshot, Geo.Location location, double target, double minimumArea) {
        return measure(label, snapshot, location, target, minimumArea, f -> true);
    }

    private Measure measure(String label, DatasetCache.Snapshot snapshot, Geo.Location location, double target, double minimumArea,
                            java.util.function.Predicate<DatasetCache.Feature> eligible) {
        var nearest = snapshot.features().stream().filter(eligible).filter(f -> f.areaSquareMetres() >= minimumArea)
                .min(Comparator.comparingDouble((DatasetCache.Feature f) -> f.distanceFrom(location))
                        .thenComparing(DatasetCache.Feature::id));
        // Complete data but no qualifying area is distinct from a failed or incomplete source.
        if (nearest.isEmpty()) return new Measure(label, null, null, null, target, 3 * target, 0, false, "NO_QUALIFYING_FEATURE");
        var feature = nearest.orElseThrow();
        double distance = feature.distanceFrom(location);
        return new Measure(label, feature.id(), feature.name(), distance, target, 3 * target,
                ScoringModel.categoryScore(distance, target), distance <= target, "MEASURED");
    }

    private Sensitivity sensitivity(List<Factor> factors) {
        double strict = alternativeScore(factors, 2);
        double loose = alternativeScore(factors, 4);
        return new Sensitivity(strict, ScoringModel.grade(strict), loose, ScoringModel.grade(loose));
    }

    private double alternativeScore(List<Factor> factors, double multiplier) {
        return factors.stream().mapToDouble(f -> f.weight() * aggregate(f.measures().stream()
                .mapToDouble(m -> m.distanceMetres() == null ? 0 : ScoringModel.categoryScore(m.distanceMetres(), m.targetMetres(), multiplier)),
                f.aggregation())).sum();
    }

    private double aggregate(java.util.stream.DoubleStream values, String aggregation) {
        return aggregation.equals("MAX") ? values.max().orElseThrow() : values.average().orElseThrow();
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
