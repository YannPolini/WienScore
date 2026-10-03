package dev.polini.wienscore.assessment;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.function.Function;

/** A local HTTP source exercises the real WFS request/JSON path without live-network test dependencies. */
final class WfsFixture implements AutoCloseable {
    record Response(int status, String body, long delayMillis) {}
    final HttpServer server;
    final java.util.concurrent.ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    final List<Map<String, String>> requests = new CopyOnWriteArrayList<>();
    volatile Function<Map<String, String>, Response> handler = query -> new Response(200, standard(query), 0);

    WfsFixture() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executor);
        server.createContext("/geo", exchange -> {
            var query = new HashMap<String, String>();
            for (String part : exchange.getRequestURI().getRawQuery().split("&")) {
                String[] pair = part.split("=", 2);
                query.put(pair[0], URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
            }
            requests.add(query);
            Response response = handler.apply(query);
            try {
                if (response.delayMillis() > 0) Thread.sleep(response.delayMillis());
                byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(response.status(), body.length);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        server.start();
    }

    String url() { return "http://127.0.0.1:" + server.getAddress().getPort() + "/geo"; }
    static String collection(int total, String... features) {
        return "{\"type\":\"FeatureCollection\",\"totalFeatures\":" + total
                + ",\"crs\":{\"type\":\"name\",\"properties\":{\"name\":\"urn:ogc:def:crs:EPSG::4326\"}},\"features\":["
                + String.join(",", features) + "]}";
    }
    static String point(String id, String properties, double lon, double lat) {
        return "{\"type\":\"Feature\",\"id\":\"" + id + "\",\"properties\":" + properties
                + ",\"geometry\":{\"type\":\"Point\",\"coordinates\":[" + lon + "," + lat + "]}}";
    }
    static String address(double lon, double lat) {
        return point("address.1", "{\"NAME\":\"Hoher Markt 1\",\"PLZ\":\"1010\"}", lon, lat);
    }
    static String boundary() {
        String[] features = new String[23];
        for (int i = 0; i < features.length; i++) {
            double west = 16 + i * .02;
            features[i] = "{\"type\":\"Feature\",\"id\":\"district." + (i + 1)
                    + "\",\"properties\":{\"NAMEK\":\"Fixture district\",\"BEZNR\":" + (i + 1)
                    + "},\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[[" + west + ",48],[" + (west + .02)
                    + ",48],[" + (west + .02) + ",49],[" + west + ",49],[" + west + ",48]]]}}";
        }
        return collection(23, features);
    }
    static String green(String id, double area, double west) {
        return """
                {"type":"Feature","id":"%s","properties":{"T_LANG":"Near feature","FLAECHE":%s},
                 "geometry":{"type":"Polygon","coordinates":[[[%s,48.211],[%s,48.211],[%s,48.216],[%s,48.216],[%s,48.211]]]}}
                """.formatted(id, area, west, west + .006, west + .006, west, west);
    }
    static String standard(Map<String, String> query) {
        String layer = query.get("typeName").replace("ogdwien:", "");
        if (layer.equals("ADRESSENOGD")) return collection(1, address(16.37307549, 48.21053772));
        if (layer.equals("BEZIRKSGRENZEOGD")) return boundary();
        Dataset dataset = Arrays.stream(Dataset.values()).filter(d -> d.layer.equals(layer)).findFirst().orElseThrow();
        if (dataset == Dataset.PARKS) return collection(2, green("green.far", 120000, 16.45), green("green.near", 120000, 16.374));
        if (dataset == Dataset.TRANSIT) return collection(3,
                point("stop.night", "{\"HTXT\":\"Night only\",\"LTYP\":10}", 16.37307549, 48.21053772),
                point("stop.bus", "{\"HTXT\":\"Near feature\",\"LTYP\":2}", 16.374, 48.211),
                point("stop.rail", "{\"HTXT\":\"Near feature\",\"LTYP\":4}", 16.374, 48.211));
        if (dataset == Dataset.DOCTORS) return collection(3,
                point("doctor.surgeon", "{\"NAME\":\"Surgeon\",\"FACH\":\"Allgemeine Chirurgie u. Viszeralchirurgie\"}", 16.37307549, 48.21053772),
                point("doctor.near", "{\"NAME\":\"Near feature\",\"FACH\":\"Allgemeinmedizin\"}", 16.374, 48.211),
                point("doctor.far", "{\"NAME\":\"Far feature\",\"FACH\":\"Allgemein- und Familienmedizin\"}", 16.4, 48.25));
        return collection(2,
                point(layer + ".far", "{\"" + dataset.nameField + "\":\"Far feature\"}", 16.4, 48.25),
                point(layer + ".near", "{\"" + dataset.nameField + "\":\"Near feature\"}", 16.374, dataset == Dataset.LIBRARIES ? 48.224 : 48.211));
    }
    @Override public void close() { server.stop(0); executor.shutdownNow(); }
}
