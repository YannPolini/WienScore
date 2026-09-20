package dev.polini.wienscore.assessment;

import org.locationtech.jts.geom.*;
import tools.jackson.databind.JsonNode;

final class Geo {
    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), 4326);
    record Location(double longitude, double latitude) {
        Location {
            if (!Double.isFinite(longitude) || !Double.isFinite(latitude)
                    || longitude < -180 || longitude > 180 || latitude < -90 || latitude > 90) {
                throw new IllegalArgumentException("Invalid coordinates");
            }
        }
    }

    static Location coordinate(JsonNode coordinates) {
        if (!coordinates.isArray() || coordinates.size() < 2
                || !coordinates.get(0).isNumber() || !coordinates.get(1).isNumber()) {
            throw new IllegalArgumentException("Missing coordinates");
        }
        return new Location(coordinates.get(0).asDouble(), coordinates.get(1).asDouble());
    }

    static Location point(JsonNode feature) {
        JsonNode geometry = feature.path("geometry");
        if (!"Point".equals(geometry.path("type").asText())) {
            throw new IllegalArgumentException("Expected point geometry");
        }
        return coordinate(geometry.path("coordinates"));
    }

    static Geometry polygonGeometry(JsonNode geometry) {
        JsonNode coordinates = geometry.path("coordinates");
        Geometry result = switch (geometry.path("type").asText()) {
            case "Polygon" -> polygon(coordinates);
            case "MultiPolygon" -> {
                Polygon[] polygons = new Polygon[coordinates.size()];
                for (int i = 0; i < polygons.length; i++) polygons[i] = polygon(coordinates.get(i));
                yield FACTORY.createMultiPolygon(polygons);
            }
            default -> throw new IllegalArgumentException("Expected polygon geometry");
        };
        if (result.isEmpty() || !result.isValid()) throw new IllegalArgumentException("Invalid boundary");
        return result;
    }

    private static Polygon polygon(JsonNode rings) {
        if (!rings.isArray() || rings.isEmpty()) throw new IllegalArgumentException("Missing polygon rings");
        LinearRing[] holes = new LinearRing[rings.size() - 1];
        for (int i = 1; i < rings.size(); i++) holes[i - 1] = ring(rings.get(i));
        return FACTORY.createPolygon(ring(rings.get(0)), holes);
    }

    private static LinearRing ring(JsonNode ring) {
        if (!ring.isArray() || ring.size() < 4) throw new IllegalArgumentException("Invalid ring");
        Coordinate[] points = new Coordinate[ring.size()];
        for (int i = 0; i < points.length; i++) {
            Location p = coordinate(ring.get(i));
            points[i] = new Coordinate(p.longitude(), p.latitude());
        }
        return FACTORY.createLinearRing(points);
    }

    static boolean covers(Geometry polygon, Location location) {
        return polygon.covers(FACTORY.createPoint(new Coordinate(location.longitude(), location.latitude())));
    }

    static double distanceMetres(Location a, Location b) {
        double lat = Math.toRadians(b.latitude() - a.latitude());
        double lon = Math.toRadians(b.longitude() - a.longitude());
        double h = Math.pow(Math.sin(lat / 2), 2) + Math.cos(Math.toRadians(a.latitude()))
                * Math.cos(Math.toRadians(b.latitude())) * Math.pow(Math.sin(lon / 2), 2);
        return 6_371_000 * 2 * Math.asin(Math.sqrt(Math.clamp(h, 0, 1)));
    }
}
