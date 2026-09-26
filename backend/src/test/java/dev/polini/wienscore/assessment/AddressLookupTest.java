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
}
