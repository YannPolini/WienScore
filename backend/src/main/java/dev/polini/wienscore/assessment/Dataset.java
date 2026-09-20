package dev.polini.wienscore.assessment;

enum Dataset {
    PARKS("Parks", "PARKINFOOGD", "ANL_NAME", 5000,
            "Mapped park points, not entrances or park boundaries; access and opening hours are not assessed."),
    TRANSIT("Public transport stops", "HALTESTELLEWLOGD", "BEZEICHNUNG", 10000,
            "Wiener Linien stop proximity only; no service frequency, reliability or step-free access assessment."),
    PHARMACIES("Pharmacies", "APOTHEKEOGD", "BEZEICHNUNG", 2000,
            "Pharmacy locations only; no opening hours or out-of-hours availability assessment."),
    LIBRARIES("Municipal libraries", "BUECHEREIOGD", "NAME", 1000,
            "Municipal library network only; excludes university and other libraries, opening hours and study-space capacity."),
    BOUNDARY("Vienna district boundaries", "BEZIRKSGRENZEOGD", "NAMEK", 100,
            "The resolved point must be covered by one of Vienna's 23 district polygons, including polygon edges.");

    final String label;
    final String layer;
    final String nameField;
    final int limit;
    final String limitation;
    Dataset(String label, String layer, String nameField, int limit, String limitation) {
        this.label = label; this.layer = layer; this.nameField = nameField; this.limit = limit; this.limitation = limitation;
    }
    String sourceUrl() {
        return "https://data.wien.gv.at/daten/geo?service=WFS&version=1.1.0&request=GetFeature"
                + "&outputFormat=application/json&srsName=EPSG:4326&typeName=ogdwien:" + layer + "&maxFeatures=" + limit;
    }
}
