package dev.polini.wienscore.assessment;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;
import org.junit.jupiter.api.*;
import tools.jackson.databind.json.JsonMapper;

class AddressLookupTest {
    WfsFixture source;
    AddressLookup lookup;
    @BeforeEach void setUp() throws Exception {
        source = new WfsFixture();
        lookup = new AddressLookup(new ViennaWfs(source.url(), Duration.ofSeconds(2), new JsonMapper()));
    }
    @AfterEach void tearDown() { source.close(); }

    @Test void resolvesAndIncludesPostcodeInExactFilter() {
        var result = lookup.resolve("  Hoher  Markt 1, 1010 Wien  ");
        assertEquals("RESOLVED", result.status());
        assertEquals("Hoher Markt 1, 1010 Wien", result.matches().getFirst().label());
        assertEquals("NAME = 'Hoher Markt 1' AND PLZ = '1010'", source.requests.getFirst().get("CQL_FILTER"));
        assertEquals("EPSG:4326", source.requests.getFirst().get("srsName"));
    }
    @Test void apostrophesRemainWithinTheCqlStringLiteral() {
        lookup.resolve("O'Brien-Gasse 1");
        assertEquals("NAME = 'O''Brien-Gasse 1'", source.requests.getFirst().get("CQL_FILTER"));
    }
    @Test void englishCitySuffixUsesTheSameExactQuery() {
        assertEquals("RESOLVED", lookup.resolve("Hoher Markt 1, 1010 Vienna").status());
        assertEquals("NAME = 'Hoher Markt 1' AND PLZ = '1010'", source.requests.getFirst().get("CQL_FILTER"));
    }
    @Test void apartmentDetailsHaveActionableFeedbackWithoutGuessingTheBuilding() {
        for (String input : new String[]{"Marchfeldstraße 10 Top 2 1200 Vienna", "Hoher Markt 1, Tür 3", "Hoher Markt 1 Stiege 2"}) {
            var error = assertThrows(IllegalArgumentException.class, () -> lookup.resolve(input));
            assertTrue(error.getMessage().contains("Leave out apartment"));
        }
        assertTrue(source.requests.isEmpty());
    }
    @Test void emptyAndMalformedInputsDoNotCallSource() {
        for (String input : new String[]{"", " ", "Wien", "Hoher Markt", "Hoher Markt 1, Berlin", "x".repeat(161), "Hoher\nMarkt 1", "123 456", "Hoher Markt 1\u0000"}) {
            assertThrows(IllegalArgumentException.class, () -> lookup.resolve(input));
        }
        assertTrue(source.requests.isEmpty());
    }
    @Test void emptyResponseWithNullCrsMeansNoMatch() {
        source.handler = q -> new WfsFixture.Response(200, "{\"type\":\"FeatureCollection\",\"totalFeatures\":0,\"features\":[],\"crs\":null}", 0);
        assertEquals("NO_MATCH", lookup.resolve("Nonexistent 999").status());
    }
    @Test void totalCountPreventsSilentlySelectingTruncatedAmbiguousMatches() {
        source.handler = q -> new WfsFixture.Response(200, WfsFixture.collection(11, WfsFixture.address(16.37, 48.21)), 0);
        assertEquals("AMBIGUOUS", lookup.resolve("Hoher Markt 1").status());
    }
    @Test void incompleteUniqueResponseIsUnavailable() {
        source.handler = q -> new WfsFixture.Response(200, WfsFixture.collection(1), 0);
        assertEquals("LOOKUP_UNAVAILABLE", lookup.resolve("Hoher Markt 1").status());
    }
    @Test void aSourceIgnoringTheExactFilterCannotSelectAnUnrelatedAddress() {
        assertEquals("LOOKUP_UNAVAILABLE", lookup.resolve("Another street 1").status());
        assertEquals("LOOKUP_UNAVAILABLE", lookup.resolve("Hoher Markt 1, 1020 Wien").status());
    }
    @Test void upstreamFailureAndInvalidCoordinatesAreUnavailable() {
        source.handler = q -> new WfsFixture.Response(503, "down", 0);
        assertEquals("LOOKUP_UNAVAILABLE", lookup.resolve("Hoher Markt 1").status());
        source.handler = q -> new WfsFixture.Response(200, WfsFixture.collection(1, WfsFixture.address(16, 200)), 0);
        assertEquals("LOOKUP_UNAVAILABLE", lookup.resolve("Hoher Markt 1").status());
    }

    @Test void suggestionsSupportGermanTransliterationsAndPostcodes() {
        source.handler = q -> new WfsFixture.Response(200, WfsFixture.collection(1,
                WfsFixture.point("ADRESSENOGD.123", "{\"NAME\":\"Marchfeldstraße 10\",\"PLZ\":\"1200\"}", 16.37, 48.21)), 0);
        var result = lookup.suggest("marchfeldstrasse 10, 1200 Vienna");
        assertEquals("SUGGESTIONS", result.status());
        assertEquals("Marchfeldstraße 10, 1200 Wien", result.matches().getFirst().label());
        String filter = source.requests.getFirst().get("CQL_FILTER");
        assertTrue(filter.contains("NAME ILIKE 'marchfeldstraße 10%'"));
        assertTrue(filter.contains("NAME ILIKE 'marchfeldstrasse 10%'"));
        assertTrue(filter.endsWith("AND PLZ = '1200'"));
        assertEquals("8", source.requests.getFirst().get("maxFeatures"));
    }

    @Test void suggestionsRejectWildcardInjectionAndSkipShortQueries() {
        assertTrue(lookup.suggest("Ho").matches().isEmpty());
        for (String input : new String[]{"Ho%", "Ho_", "Ho\\", "Ho\n", "x".repeat(161), "Hoher Markt 1 Top 2"})
            assertThrows(IllegalArgumentException.class, () -> lookup.suggest(input));
        assertTrue(source.requests.isEmpty());
        lookup.suggest("O'Brien");
        assertTrue(source.requests.getFirst().get("CQL_FILTER").contains("o''brien%"));
    }

    @Test void suggestionsFailClosedOnUnrelatedIncompleteOrInvalidResults() {
        assertEquals("LOOKUP_UNAVAILABLE", lookup.suggest("Another street").status());
        source.handler = q -> new WfsFixture.Response(200, WfsFixture.collection(2, WfsFixture.address(16, 48)), 0);
        assertEquals("LOOKUP_UNAVAILABLE", lookup.suggest("Hoher").status());
        source.handler = q -> new WfsFixture.Response(200, WfsFixture.collection(1, WfsFixture.address(16, 200)), 0);
        assertEquals("LOOKUP_UNAVAILABLE", lookup.suggest("Hoher").status());
        source.handler = q -> new WfsFixture.Response(503, "down", 0);
        assertEquals("LOOKUP_UNAVAILABLE", lookup.suggest("Hoher").status());
        source.handler = q -> new WfsFixture.Response(200, WfsFixture.collection(0), 0);
        assertEquals("SUGGESTIONS", lookup.suggest("Hoher").status());
        assertTrue(lookup.suggest("Hoher").matches().isEmpty());
    }

    @Test void selectedIdResolvesAnnotatedNameAndRejectsSubstitution() {
        source.handler = q -> new WfsFixture.Response(200, WfsFixture.collection(1,
                WfsFixture.point("ADRESSENOGD.123", "{\"NAME\":\"Karlsplatz 13 Technische Universität Wien\",\"PLZ\":\"1040\"}", 16.37, 48.21)), 0);
        var suggestion = lookup.suggest("Karlsplatz 13").matches().getFirst();
        var resolved = lookup.resolveId(suggestion.id());
        assertEquals("RESOLVED", resolved.status());
        assertEquals(suggestion, resolved.matches().getFirst());
        assertEquals("ADRESSENOGD.123", source.requests.getLast().get("featureID"));
        assertFalse(source.requests.getLast().containsKey("CQL_FILTER"));
        assertEquals("LOOKUP_UNAVAILABLE", lookup.resolveId("ADRESSENOGD.456").status());
        assertThrows(IllegalArgumentException.class, () -> lookup.resolveId("OTHER.123"));
        source.handler = q -> new WfsFixture.Response(200, WfsFixture.collection(0), 0);
        assertEquals("NO_MATCH", lookup.resolveId("ADRESSENOGD.123").status());
    }
}
