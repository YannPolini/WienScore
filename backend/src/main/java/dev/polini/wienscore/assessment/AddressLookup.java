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
            "^(.+?\\s+\\d+[\\p{L}]?(?:[-/]\\d+[\\p{L}]?)?)(?:\\s*,?\\s+(\\d{4}))?(?:\\s*,?\\s+(?:Wien|Vienna))?$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern APARTMENT = Pattern.compile("\\b(?:Top|T[üu]r|Wohnung|Apartment|Apt\\.?|Stiege)\\s*\\d+", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private final ViennaWfs wfs;
    record Match(String id, String label, double longitude, double latitude) {
        Geo.Location point() { return new Geo.Location(longitude, latitude); }
    }
    record Result(String status, String message, List<Match> matches, Instant retrievedAt) {}

    AddressLookup(ViennaWfs wfs) { this.wfs = wfs; }

    private static String folded(String value) {
        return value.toLowerCase(java.util.Locale.ROOT).replace("ß", "ss")
                .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue");
    }

    Result suggest(String input) {
        if (input == null || input.length() > 160 || input.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Use an address of at most 160 characters.");
        String query = input.strip().replaceAll("\\s+", " ");
        if (query.length() < 3) return new Result("SUGGESTIONS", "Type at least 3 characters.", List.of(), null);
        if (!query.matches("[\\p{L}\\p{N} ,.'’/\\-]+"))
            throw new IllegalArgumentException("Use street letters and house numbers, without special search symbols.");
        if (APARTMENT.matcher(query).find())
            throw new IllegalArgumentException("Leave out apartment, Top, Tür or Stiege numbers.");
        query = query.replaceFirst("(?iu)\\s*,?\\s+(Wien|Vienna)$", "");
        var postal = Pattern.compile("^(.*?)(?:\\s*,?\\s+)(\\d{4})$").matcher(query);
        String postcode = postal.matches() ? postal.group(2) : null;
        String prefix = (postcode == null ? query : postal.group(1)).strip().replaceFirst(",$", "").strip();
        if (prefix.length() < 3 || !prefix.codePoints().anyMatch(Character::isLetter))
            return new Result("SUGGESTIONS", "Start with the street name.", List.of(), null);
        // Query both German spellings and common transliterations in one bounded request.
        var variants = new java.util.LinkedHashSet<String>();
        variants.add(folded(prefix));
        for (String[] pair : new String[][]{{"ss", "ß"}, {"ae", "ä"}, {"oe", "ö"}, {"ue", "ü"}}) {
            for (String variant : List.copyOf(variants)) variants.add(variant.replace(pair[0], pair[1]));
        }
        variants.add(prefix.toLowerCase(java.util.Locale.ROOT));
        String filter = variants.stream().map(v -> "NAME ILIKE '" + v.replace("'", "''") + "%'")
                .collect(java.util.stream.Collectors.joining(" OR ", "(", ")"));
        if (postcode != null) filter += " AND PLZ = '" + postcode + "'";
        try {
            var response = wfs.fetch("ADRESSENOGD", 8, filter);
            if (response.features().size() != Math.min(8L, response.total()))
                throw new IllegalArgumentException("Incomplete suggestions");
            var matches = response.features().stream().map(feature -> {
                String name = ViennaWfs.requiredText(feature.path("properties"), "NAME");
                String zip = ViennaWfs.requiredText(feature.path("properties"), "PLZ");
                if (!folded(name).startsWith(folded(prefix)) || (postcode != null && !postcode.equals(zip)))
                    throw new IllegalArgumentException("Unrelated suggestion");
                return toMatch(feature);
            }).distinct().sorted(java.util.Comparator.comparing(Match::label)).toList();
            return new Result("SUGGESTIONS", matches.isEmpty() ? "No suggestions. Check the street name and house number."
                    : response.total() > 8 ? "More matches available. Add a house number or postcode." : "Choose an address.", matches, Instant.now());
        } catch (RuntimeException ex) { return unavailable(); }
    }

    Result resolveId(String id) {
        if (id == null || !id.matches("ADRESSENOGD\\.[A-Za-z0-9_-]{1,80}"))
            throw new IllegalArgumentException("Choose an address from the suggestions again.");
        try {
            var response = wfs.fetchById(id);
            if (response.total() == 0) return new Result("NO_MATCH", "This address is no longer available. Choose another suggestion.", List.of(), Instant.now());
            if (response.total() != 1 || response.features().size() != 1)
                throw new IllegalArgumentException("Incomplete selected address");
            var match = toMatch(response.features().getFirst());
            if (!id.equals(match.id())) throw new IllegalArgumentException("Unrelated selected address");
            return new Result("RESOLVED", "Address found.", List.of(match), Instant.now());
        } catch (RuntimeException ex) { return unavailable(); }
    }

    private Match toMatch(tools.jackson.databind.JsonNode feature) {
        var properties = feature.path("properties");
        var point = Geo.point(feature);
        return new Match(ViennaWfs.requiredText(feature, "id"),
                ViennaWfs.requiredText(properties, "NAME") + ", " + ViennaWfs.requiredText(properties, "PLZ") + " Wien",
                point.longitude(), point.latitude());
    }

    private Result unavailable() {
        return new Result("LOOKUP_UNAVAILABLE", "Address suggestions are unavailable. Try again or enter the exact address.", List.of(), null);
    }

    Result resolve(String input) {
        if (input == null || input.isBlank()) throw new IllegalArgumentException("Enter a Vienna street and house number.");
        if (input.length() > 160 || input.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Use a single address of at most 160 characters.");
        }
        String normalized = input.strip().replaceAll("\\s+", " ");
        if (APARTMENT.matcher(normalized).find()) {
            throw new IllegalArgumentException("Leave out apartment, Top, Tür or Stiege numbers. Enter the building address, for example Marchfeldstraße 10, 1200 Wien.");
        }
        var match = ADDRESS.matcher(normalized);
        if (!match.matches() || !match.group(1).codePoints().anyMatch(Character::isLetter)) throw new IllegalArgumentException("Use street and house number, optionally followed by postcode and Wien or Vienna (for example Hoher Markt 1, 1010 Wien).");
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
                    "No exact address found in Vienna. Check the official street spelling (including ß and umlauts), house number and postcode. Leave out apartment or Top numbers. Spelling variants and addresses outside Vienna may not be found.", matches, Instant.now());
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
