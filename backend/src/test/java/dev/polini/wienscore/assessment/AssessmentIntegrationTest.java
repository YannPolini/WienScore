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
            assertEquals("Near feature", factor.path("nearestName").asText());
            assertTrue(factor.path("distanceMetres").asDouble() > 70);
            assertTrue(factor.path("distanceMetres").asDouble() < 100);
            contributions += factor.path("contribution").asDouble();
        }
        assertEquals(contributions, first.path("score").asDouble(), 1e-10);
        var second = mapper.readTree(mvc.perform(post("/api/assessments").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(first.path("score"), second.path("score"));
        assertEquals(first.path("factors"), second.path("factors"));
        for (int i = 0; i < 5; i++) {
            assertEquals(first.path("sources").get(i).path("sha256"), second.path("sources").get(i).path("sha256"));
        }
    }

    @Test void apiRejectsBlankAndMalformedBodies() throws Exception {
        for (String body : new String[]{"{\"address\":\" \"}", "{}", "not json"}) {
            mvc.perform(post("/api/assessments").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value("INVALID_INPUT"));
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
}
