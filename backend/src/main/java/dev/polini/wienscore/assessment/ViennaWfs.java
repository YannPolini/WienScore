package dev.polini.wienscore.assessment;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
class ViennaWfs {
    private final String endpoint;
    private final Duration timeout;
    private final HttpClient client;
    private final JsonMapper mapper;
    record Collection(List<JsonNode> features, long total, String raw) {}

    ViennaWfs(@Value("${wienscore.wfs-url:https://data.wien.gv.at/daten/geo}") String endpoint,
              @Value("${wienscore.request-timeout:PT15S}") Duration timeout, JsonMapper mapper) {
        this.endpoint = endpoint;
        this.timeout = timeout;
        this.mapper = mapper;
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    Collection fetch(String layer, int limit, String filter) {
        String url = endpoint + "?service=WFS&version=1.1.0&request=GetFeature"
                + "&outputFormat=application/json&srsName=EPSG:4326&typeName=ogdwien:"
                + layer + "&maxFeatures=" + limit;
        if (filter != null) url += "&CQL_FILTER=" + URLEncoder.encode(filter, StandardCharsets.UTF_8);
        try {
            var request = HttpRequest.newBuilder(URI.create(url)).timeout(timeout)
                    .header("Accept", "application/json").GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) throw new IllegalStateException("WFS HTTP " + response.statusCode());
            return parse(response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Data request interrupted", ex);
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Data request failed", ex);
        }
    }

    Collection parse(String raw) {
        JsonNode root = mapper.readTree(raw);
        JsonNode count = root.path("totalFeatures");
        String crs = root.path("crs").path("properties").path("name").asText();
        if (!"FeatureCollection".equals(root.path("type").asText())
                || !root.path("features").isArray() || !count.isIntegralNumber() || count.asLong() < 0
                || (count.asLong() != 0 && !("urn:ogc:def:crs:EPSG::4326".equals(crs) || "EPSG:4326".equals(crs)))) {
            throw new IllegalArgumentException("Unusable WFS response or coordinate system");
        }
        var features = root.path("features").valueStream().toList();
        if (features.size() > count.asLong()) throw new IllegalArgumentException("Inconsistent feature count");
        return new Collection(features, count.asLong(), raw);
    }

    static String requiredText(JsonNode node, String key) {
        JsonNode value = node.path(key);
        if (!value.isString() || value.asText().isBlank()) throw new IllegalArgumentException("Missing " + key);
        return value.asText();
    }
}
