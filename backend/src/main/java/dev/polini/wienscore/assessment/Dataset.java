package dev.polini.wienscore.assessment;

enum Dataset {
    PARKS("Public green space", "OEFFGRUENFLOGD", "T_LANG", 5000,
            "Public green-space polygons from the 2013–2015 survey. Distances reach mapped boundaries, not entrances. Sizes use individual mapped areas; fragmented sites may be underestimated. No opening hours or vegetation-cover measurement."),
    TRANSIT("Public transport", "OEFFHALTESTOGD", "HTXT", 20000,
            "Daytime bus/tram and U-Bahn/regional rail points, classified by the catalog LTYP codes 1–6. Night/on-demand codes 9–13 excluded. No walking routes, frequency, reliability or step-free access assessment."),
    PHARMACIES("Pharmacies", "APOTHEKEOGD", "BEZEICHNUNG", 2000,
            "Pharmacy locations only; no opening hours or out-of-hours availability assessment."),
    DOCTORS("General practitioners", "ARZTOGD", "NAME", 15000,
            "Only Allgemeinmedizin and Allgemein- und Familienmedizin records are used. No insurance, appointment availability, capacity or opening-hours assessment."),
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
