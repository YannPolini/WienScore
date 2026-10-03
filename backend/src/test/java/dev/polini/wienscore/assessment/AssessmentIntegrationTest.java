package dev.polini.wienscore.assessment;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.nio.file.Path;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
class AssessmentIntegrationTest {
    static final WfsFixture SOURCE;
    static { try { SOURCE = new WfsFixture(); } catch (Exception ex) { throw new ExceptionInInitializerError(ex); } }
    @TempDir static Path directory;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("wienscore.wfs-url", SOURCE::url);
        registry.add("wienscore.cache-directory", () -> directory.toString());
    }
    @AfterAll static void stopSource() { SOURCE.close(); }
    @BeforeEach void resetHandler() {
        SOURCE.handler = q -> new WfsFixture.Response(200, WfsFixture.standard(q), 0);
    }
    @Autowired MockMvc mvc;
    @Autowired JsonMapper mapper;

    @Test void wholeWorkflowUsesNearestFeaturesAndRepeatsExactlyWithSameSnapshots() throws Exception {
        String body = "{\"address\":\"Hoher Markt 1, 1010 Wien\"}";
        var first = mapper.readTree(mvc.perform(post("/api/assessments").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.profile").value("Student"))
                .andExpect(jsonPath("$.location.label").value("Hoher Markt 1, 1010 Wien"))
                .andExpect(jsonPath("$.grade").value(1)).andExpect(jsonPath("$.factors.length()").value(4))
                .andReturn().getResponse().getContentAsString());
        double contributions = 0;
        for (var factor : first.path("factors")) {
            for (var measure : factor.path("measures")) {
                assertEquals("Near feature", measure.path("nearestName").asText());
                assertTrue(measure.path("distanceMetres").asDouble() > 70);
                assertTrue(measure.path("distanceMetres").asDouble() < 1600);
            }
            contributions += factor.path("contribution").asDouble();
        }
        assertEquals(contributions, first.path("score").asDouble(), 1e-10);
        var second = mapper.readTree(mvc.perform(post("/api/assessments").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(first.path("score"), second.path("score"));
        assertEquals(first.path("factors"), second.path("factors"));
        for (int i = 0; i < first.path("sources").size(); i++) {
            assertEquals(first.path("sources").get(i).path("sha256"), second.path("sources").get(i).path("sha256"));
        }
    }

    @Test void apiRejectsBlankAndMalformedBodies() throws Exception {
        for (String body : new String[]{"{\"address\":\" \"}", "{}", "not json", "null", "[]", "{\"address\":null}"}) {
            mvc.perform(post("/api/assessments").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value("INVALID_INPUT"));
        }
    }

    @Test void profilesChangeWeightsButNotMeasurementsAndRepeatWithSameSnapshots() throws Exception {
        var student = mapper.readTree(mvc.perform(post("/api/assessments").contentType(MediaType.APPLICATION_JSON)
                .content("{\"address\":\"Hoher Markt 1\",\"profile\":\"STUDENT\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var senior = mapper.readTree(mvc.perform(post("/api/assessments").contentType(MediaType.APPLICATION_JSON)
                .content("{\"address\":\"Hoher Markt 1\",\"profile\":\" senior \"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.profile").value("Senior"))
                .andExpect(jsonPath("$.modelVersion").value("neighbourhood-proximity-v3"))
                .andExpect(jsonPath("$.profileDescription").isNotEmpty())
                .andReturn().getResponse().getContentAsString());
        double[] weights = {.3125, .25, .375, .0625};
        double expected = 0;
        for (int i = 0; i < weights.length; i++) {
            var factor = senior.path("factors").get(i);
            assertEquals(student.path("factors").get(i).path("measures"), factor.path("measures"));
            assertEquals(weights[i], factor.path("weight").asDouble());
            expected += factor.path("score").asDouble() * weights[i];
        }
        assertEquals(expected, senior.path("score").asDouble(), 1e-10);
        assertNotEquals(student.path("score"), senior.path("score"));
        var repeat = mapper.readTree(mvc.perform(post("/api/assessments").contentType(MediaType.APPLICATION_JSON)
                .content("{\"address\":\"Hoher Markt 1\",\"profile\":\"SENIOR\"}"))
                .andReturn().getResponse().getContentAsString());
        assertEquals(senior.path("score"), repeat.path("score"));
        for (int i = 0; i < student.path("sources").size(); i++) assertEquals(student.path("sources").get(i).path("sha256"), senior.path("sources").get(i).path("sha256"));
    }

    @Test void invalidProfilesNeverSilentlyBecomeStudent() throws Exception {
        SOURCE.requests.clear();
        for (String profile : new String[]{"", "Family", "STUDENT,SENIOR"}) {
            mvc.perform(post("/api/assessments").contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(java.util.Map.of("address", "Hoher Markt 1", "profile", profile))))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value("INVALID_INPUT"))
                    .andExpect(jsonPath("$.message").value("Choose Student or Senior as the profile."));
        }
        assertTrue(SOURCE.requests.isEmpty());
    }

    @Test void lookupFailureAndNoMatchPreserveSeniorProfileWithoutGrade() throws Exception {
        for (var response : new WfsFixture.Response[]{new WfsFixture.Response(503, "down", 0),
                new WfsFixture.Response(200, WfsFixture.collection(0), 0)}) {
            SOURCE.handler = q -> response;
            mvc.perform(post("/api/assessments").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"address\":\"Hoher Markt 1\",\"profile\":\"SENIOR\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.profile").value("Senior"))
                    .andExpect(jsonPath("$.score").isEmpty()).andExpect(jsonPath("$.grade").isEmpty())
                    .andExpect(jsonPath("$.factors").isEmpty());
        }
    }

    @Test void outsideResolvedCoordinateGetsNoGradeDespiteViennaAddressLabel() throws Exception {
        SOURCE.handler = q -> new WfsFixture.Response(200, q.get("typeName").endsWith("ADRESSENOGD")
                ? WfsFixture.collection(1, WfsFixture.address(15, 47)) : WfsFixture.standard(q), 0);
        mvc.perform(post("/api/assessments").contentType(MediaType.APPLICATION_JSON).content("{\"address\":\"Hoher Markt 1\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("OUTSIDE_VIENNA"))
                .andExpect(jsonPath("$.grade").isEmpty()).andExpect(jsonPath("$.factors").isEmpty());
    }

    @Test void ambiguousResponseProvidesCandidatesWithoutSelectingALocation() throws Exception {
        SOURCE.handler = q -> new WfsFixture.Response(200, WfsFixture.collection(11, WfsFixture.address(16.37, 48.21)), 0);
        mvc.perform(post("/api/assessments").contentType(MediaType.APPLICATION_JSON).content("{\"address\":\"Hoher Markt 1\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("AMBIGUOUS"))
                .andExpect(jsonPath("$.location").isEmpty()).andExpect(jsonPath("$.grade").isEmpty())
                .andExpect(jsonPath("$.candidates.length()").value(1));
    }

    @Test void selectedOfficialIdUsesServerCoordinatesAndStillChecksBoundary() throws Exception {
        for (double longitude : new double[]{16.37, 15}) {
            SOURCE.handler = q -> new WfsFixture.Response(200, q.get("typeName").endsWith("ADRESSENOGD")
                    ? WfsFixture.collection(1, WfsFixture.point("ADRESSENOGD.123",
                      "{\"NAME\":\"Karlsplatz 13 Technische Universität Wien\",\"PLZ\":\"1040\"}", longitude, 48.21))
                    : WfsFixture.standard(q), 0);
            mvc.perform(post("/api/addresses/suggest").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"address\":\"Karlsplatz 13\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.matches[0].id").value("ADRESSENOGD.123"));
            mvc.perform(post("/api/assessments").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"address\":\"ignored client label\",\"addressId\":\"ADRESSENOGD.123\",\"profile\":\"SENIOR\",\"latitude\":48.21,\"longitude\":16.37}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(longitude == 15 ? "OUTSIDE_VIENNA" : "OK"))
                    .andExpect(jsonPath("$.location.label").value("Karlsplatz 13 Technische Universität Wien, 1040 Wien"))
                    .andExpect(jsonPath("$.profile").value("Senior"));
        }
        mvc.perform(post("/api/assessments").contentType(MediaType.APPLICATION_JSON)
                .content("{\"addressId\":\"ADRESSENOGD.1,OTHER.2\"}"))
                .andExpect(status().isBadRequest());
    }
}
