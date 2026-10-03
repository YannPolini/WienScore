package dev.polini.wienscore.assessment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.*;
import java.util.*;
import org.locationtech.jts.geom.Geometry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
class DatasetCache {
    private static final Logger LOG = LoggerFactory.getLogger(DatasetCache.class);
    private final ViennaWfs wfs;
    private final JsonMapper mapper;
    private final Path directory;
    private final Clock clock;
    private final Duration refreshAfter;
    private final Duration maxAge;
    private final Duration retryAfter;
    private final Map<Dataset, Entry> entries = new EnumMap<>(Dataset.class);

    record Feature(String id, String name, Geo.Location point, Geometry polygon, double areaSquareMetres, int transportType) {
        Feature(String id, String name, Geo.Location point, Geometry polygon) { this(id, name, point, polygon, 0, 0); }
        double distanceFrom(Geo.Location location) {
            return point == null ? polygon.distance(Geo.metricPoint(location)) : Geo.distanceMetres(location, point);
        }
    }
    record Snapshot(List<Feature> features, Instant retrievedAt, String sha256, int unnamedFeatures) {}
    record View(Snapshot snapshot, String state, String message) {}
    record Stored(String retrievedAt, String sha256, String geoJson) {}
    private static class Entry {
        boolean loaded;
        Snapshot snapshot;
        Instant nextAttempt = Instant.MIN;
    }

    @Autowired
    DatasetCache(ViennaWfs wfs, JsonMapper mapper,
                 @Value("${wienscore.cache-directory:./data-cache}") String directory,
                 @Value("${wienscore.cache-refresh:PT24H}") Duration refreshAfter,
                 @Value("${wienscore.cache-max-age:P7D}") Duration maxAge,
                 @Value("${wienscore.cache-retry:PT5M}") Duration retryAfter) {
        this(wfs, mapper, Path.of(directory), Clock.systemUTC(), refreshAfter, maxAge, retryAfter);
    }

    DatasetCache(ViennaWfs wfs, JsonMapper mapper, Path directory, Clock clock,
                 Duration refreshAfter, Duration maxAge, Duration retryAfter) {
        if (refreshAfter.isNegative() || refreshAfter.isZero() || maxAge.compareTo(refreshAfter) < 0
                || retryAfter.isNegative() || retryAfter.isZero()) throw new IllegalArgumentException("Invalid cache durations");
        this.wfs = wfs; this.mapper = mapper; this.directory = directory; this.clock = clock;
        this.refreshAfter = refreshAfter; this.maxAge = maxAge; this.retryAfter = retryAfter;
        for (Dataset dataset : Dataset.values()) entries.put(dataset, new Entry());
    }

    View get(Dataset dataset) {
        Entry entry = entries.get(dataset);
        // One refresh per layer at a time; readers can never see a half-loaded snapshot.
        synchronized (entry) {
            Instant now = clock.instant();
            if (!entry.loaded) {
                entry.snapshot = read(dataset);
                entry.loaded = true;
            }
            if (usable(entry.snapshot, now, refreshAfter)) return new View(entry.snapshot, "CACHED", "Validated cached snapshot.");
            if (!now.isBefore(entry.nextAttempt)) {
                try {
                    var response = wfs.fetch(dataset.layer, dataset.limit, null);
                    Snapshot snapshot = validate(dataset, response, clock.instant());
                    entry.snapshot = snapshot;
                    write(dataset, response.raw(), snapshot);
                    entry.nextAttempt = Instant.MIN;
                    return new View(snapshot, "LIVE", "Complete snapshot downloaded and validated.");
                } catch (RuntimeException ex) {
                    LOG.warn("Dataset {} refresh failed: {}", dataset.layer, ex.toString());
                    entry.nextAttempt = clock.instant().plus(retryAfter);
                }
            }
            if (usable(entry.snapshot, clock.instant(), maxAge)) {
                return new View(entry.snapshot, "STALE_FALLBACK", "Refresh failed; using an older validated snapshot (maximum age " + maxAge.toDays() + " days).");
            }
            return new View(null, "UNAVAILABLE", "No usable complete snapshot. The source may be unavailable, incomplete or invalid; please try again later.");
        }
    }

    private boolean usable(Snapshot snapshot, Instant now, Duration ageLimit) {
        return snapshot != null && !snapshot.retrievedAt().isAfter(now)
                && now.isBefore(snapshot.retrievedAt().plus(ageLimit));
    }

    Snapshot validate(Dataset dataset, ViennaWfs.Collection response, Instant retrievedAt) {
        if (response.total() == 0 || response.total() != response.features().size()
                || response.total() > dataset.limit) throw new IllegalArgumentException("Empty or incomplete dataset");
        var ids = new HashSet<String>();
        var districts = new HashSet<Integer>();
        var features = new ArrayList<Feature>();
        int unnamed = 0;
        for (var node : response.features()) {
            String id = ViennaWfs.requiredText(node, "id");
            if (!ids.add(id)) throw new IllegalArgumentException("Duplicate feature ID");
            if (dataset == Dataset.DOCTORS) {
                String specialty = ViennaWfs.requiredText(node.path("properties"), "FACH").strip();
                // Exact matches deliberately exclude Allgemeine Chirurgie (general surgery).
                if (!Set.of("Allgemeinmedizin", "Allgemein- und Familienmedizin").contains(specialty)) continue;
            }
            var nameNode = node.path("properties").path(dataset.nameField);
            String name = nameNode.isString() ? nameNode.asText().strip() : "";
            boolean unnamedFeature = name.isBlank();
            if (unnamedFeature) {
                name = "Unnamed feature (" + id + ")";
            }
            if (dataset == Dataset.BOUNDARY) {
                var district = node.path("properties").path("BEZNR");
                if (!district.isIntegralNumber() || district.asInt() < 1 || district.asInt() > 23
                        || !districts.add(district.asInt())) throw new IllegalArgumentException("Invalid district set");
                features.add(new Feature(id, name, null, Geo.polygonGeometry(node.path("geometry"))));
            } else if (dataset == Dataset.PARKS) {
                var area = node.path("properties").path("FLAECHE");
                if (!area.isNumber() || !Double.isFinite(area.asDouble()) || area.asDouble() <= 0)
                    throw new IllegalArgumentException("Missing or invalid green-space area");
                features.add(new Feature(id, name, null, Geo.metricGeometry(Geo.polygonGeometry(node.path("geometry"))), area.asDouble(), 0));
            } else if (dataset == Dataset.TRANSIT) {
                var type = node.path("properties").path("LTYP");
                if (!type.isIntegralNumber() || !Set.of(1, 2, 3, 4, 5, 6, 9, 10, 11, 12, 13).contains(type.asInt()))
                    throw new IllegalArgumentException("Unknown transport type");
                var point = Geo.point(node);
                if (type.asInt() > 6) continue;
                features.add(new Feature(id, name, point, null, 0, type.asInt()));
            } else {
                features.add(new Feature(id, name, Geo.point(node), null));
            }
            if (unnamedFeature) unnamed++;
        }
        if (dataset == Dataset.BOUNDARY && districts.size() != 23) throw new IllegalArgumentException("Not all 23 districts present");
        if (features.isEmpty()) throw new IllegalArgumentException("No eligible features in dataset");
        features.sort(Comparator.comparing(Feature::id));
        return new Snapshot(List.copyOf(features), retrievedAt, sha256(response.raw()), unnamed);
    }

    private Snapshot read(Dataset dataset) {
        Path file = directory.resolve(dataset.layer + ".json");
        if (!Files.exists(file)) return null;
        try {
            Stored stored = mapper.readValue(Files.readString(file), Stored.class);
            if (!sha256(stored.geoJson()).equals(stored.sha256())) throw new IllegalArgumentException("Cache checksum mismatch");
            return validate(dataset, wfs.parse(stored.geoJson()), Instant.parse(stored.retrievedAt()));
        } catch (IOException | RuntimeException ex) {
            LOG.warn("Ignoring invalid {} cache: {}", dataset.layer, ex.toString());
            return null;
        }
    }

    private void write(Dataset dataset, String raw, Snapshot snapshot) {
        try {
            Files.createDirectories(directory);
            Path target = directory.resolve(dataset.layer + ".json");
            Path temporary = directory.resolve(dataset.layer + ".tmp");
            Files.writeString(temporary, mapper.writeValueAsString(new Stored(snapshot.retrievedAt().toString(), snapshot.sha256(), raw)));
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException ex) {
            // A read-only disk must not discard a valid in-memory result.
            LOG.warn("Could not persist {} snapshot: {}", dataset.layer, ex.toString());
        }
    }

    static String sha256(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
