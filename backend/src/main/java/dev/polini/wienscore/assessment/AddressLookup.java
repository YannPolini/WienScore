package dev.polini.wienscore.assessment;

import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
class AddressLookup {
    private static final Logger LOG = LoggerFactory.getLogger(AddressLookup.class);
    // A deliberately small, exact-address interface; do not strip arbitrary city names.
    private static final Pattern ADDRESS = Pattern.compile(
            "^(.+?\\s+\\d+[\\p{L}]?(?:[-/]\\d+[\\p{L}]?)?)(?:\\s*,?\\s+(\\d{4}))?(?:\\s*,?\\s+Wien)?$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private final ViennaWfs wfs;
    record Match(String id, String label, double longitude, double latitude) {
        Geo.Location point() { return new Geo.Location(longitude, latitude); }
    }
    record Result(String status, String message, List<Match> matches, Instant retrievedAt) {}

    AddressLookup(ViennaWfs wfs) { this.wfs = wfs; }

    Result resolve(String input) {
        if (input == null || input.isBlank()) throw new IllegalArgumentException("Enter a Vienna street and house number.");
        if (input.length() > 160 || input.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Use a single address of at most 160 characters.");
        }
        String normalized = input.strip().replaceAll("\\s+", " ");
        var match = ADDRESS.matcher(normalized);
        if (!match.matches()) throw new IllegalArgumentException("Use street and house number, optionally followed by postcode and Wien (for example Hoher Markt 1, 1010 Wien).");
        String filter = "NAME = '" + match.group(1).replace("'", "''") + "'";
        if (match.group(2) != null) filter += " AND PLZ = '" + match.group(2) + "'";
        try {
            var response = wfs.fetch("ADRESSENOGD", 10, filter);
            var matches = response.features().stream().map(feature -> {
                var properties = feature.path("properties");
                String name = ViennaWfs.requiredText(properties, "NAME");
                String postcode = ViennaWfs.requiredText(properties, "PLZ");
                if (!name.equals(match.group(1)) || (match.group(2) != null && !postcode.equals(match.group(2)))) {
                    throw new IllegalArgumentException("Source returned an unrelated address");
                }
                var point = Geo.point(feature);
                return new Match(ViennaWfs.requiredText(feature, "id"),
                        name + ", " + postcode + " Wien",
                        point.longitude(), point.latitude());
            }).toList();
            if (response.total() == 0) return new Result("NO_MATCH",
                    "No exact address found in Vienna. Check the street spelling, house number and postcode.", matches, Instant.now());
            if (response.total() > 1) return new Result("AMBIGUOUS",
                    "Several addresses match. Add or correct the postcode and house number; no location was selected. Showing up to 10 matches.", matches, Instant.now());
            if (matches.size() != 1) throw new IllegalArgumentException("Incomplete address response");
            return new Result("RESOLVED", "Address found.", matches, Instant.now());
        } catch (RuntimeException ex) {
            // Do not log the user's address or a request URI containing it.
            LOG.warn("Address source could not be used ({})", ex.getClass().getSimpleName());
            return new Result("LOOKUP_UNAVAILABLE", "Vienna address lookup is unavailable or incomplete. Please try again later.", List.of(), null);
        }
    }
}
