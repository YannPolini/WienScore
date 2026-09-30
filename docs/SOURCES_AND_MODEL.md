# Sources and provisional profile model

WienScore is a student project estimate of access to selected amenities. It is
not an official City of Vienna rating and does not measure overall quality of life.
The factors, weights and distance limits below are engineering starting assumptions
from the project dataset check dated 14 September 2026. They are **not validated
research findings**. Sprint 3's [research and validation](VALIDATION.md) records
factor rationale, six contrasting addresses and sensitivity analysis. Numeric
calibration against independent outcomes remains open; the model stays provisional.

## Source manifest

**Datenquelle: Stadt Wien – https://data.wien.gv.at**. Each of these datasets'
JSON distributions declares **[CC BY 4.0](https://creativecommons.org/licenses/by/4.0/)**
in the Austrian catalog, checked on 17 September 2026. The exact metadata URLs,
retrieval times, license URLs and update-frequency labels are preserved in
[source-metadata.json](source-metadata.json). This is per-dataset evidence,
rather than relying on the WFS capabilities' older service-wide CC BY 3.0 string.
See also the [City's data terms](https://digitales.wien.gv.at/ogd-nutzungsbedingungen/).

| Dataset and catalog | WFS layer | Role / name field | Catalog update frequency |
|---|---|---|---|
| [Adressen Standorte Wien](https://www.data.gv.at/datasets/1d5c2411-9719-4c8f-b99d-57a5f4a4ae41) | ADRESSENOGD | Exact address lookup; NAME, PLZ | Continuous |
| [Parkanlagen Standorte Wien](https://www.data.gv.at/datasets/22add642-d849-48ff-9913-8c7ba2d99b46) | PARKINFOOGD | Nearest park point; ANL_NAME | Continuous |
| [Haltestellen Standorte Wien](https://www.data.gv.at/datasets/21fca925-12ac-4215-ba1a-a9c73cb3b082) | HALTESTELLEWLOGD | Nearest Wiener Linien stop; BEZEICHNUNG | Continuous |
| [Apotheken Standorte Wien](https://www.data.gv.at/datasets/cf00fe06-e4b2-4319-af00-85230e7b2f1b) | APOTHEKEOGD | Nearest pharmacy; BEZEICHNUNG | Continuous |
| [Büchereien Standorte Wien](https://www.data.gv.at/datasets/2adc5a86-faa3-4b63-acff-a66128b811b7) | BUECHEREIOGD | Nearest municipal library; NAME | Continuous |
| [Bezirksgrenzen Wien](https://www.data.gv.at/datasets/2ee6b8bf-6292-413c-bb8b-bd22dbb2ad4b) | BEZIRKSGRENZEOGD | Check resolved location; BEZNR, polygon geometry | Irregular |

Catalog modification dates and frequency labels do not establish when every
feature was last observed. Runtime retrieval timestamps are recorded separately.

Requests use the [official WFS](https://digitales.wien.gv.at/open-data/geowebservices/)
at `https://data.wien.gv.at/daten/geo`, version 1.1.0, `GetFeature`, JSON output and
explicit `srsName=EPSG:4326`. Returned coordinates are interpreted as longitude,
latitude. Download caps: parks 5,000, stops 10,000, pharmacies 2,000, libraries 1,000,
districts 100. A cap is not pagination: a larger advertised total fails closed
until the implementation is revised. No live source is contacted in automated tests.

## Model profiles-draft-v1

| Factor | Student weight | Senior weight | Zero-score distance D | Provisional rationale |
|---|---:|---:|---:|---|
| Parks | 25% | 35% | 1,000 m | Access to outdoor recreation |
| Public transport stops | 40% | 25% | 800 m | Access to local public transport |
| Pharmacies | 10% | 35% | 1,000 m | Access to an everyday health service |
| Municipal libraries | 25% | 5% | 2,000 m | Access to library services |

1. Find the nearest mapped point in each complete dataset using haversine distance
   with mean Earth radius 6,371,000 m. Exact distance ties use source ID ordering.
2. Category score = `100 * clamp(1 - distance / D, 0, 1)`.
3. Contribution = category score × weight; total = sum of all four contributions.
4. Total ≥80 gives grade 1; ≥60 grade 2; ≥40 grade 3; ≥20 grade 4; otherwise grade 5.

Calculations and grade comparisons use unrounded values. Only the interface rounds
distances/scores. Consequently displayed contributions can differ slightly from
the displayed total when summed. The rules and version are centralized in
`ScoringModel.java`; change the version whenever rules change. The controlled
example (100, 50, 0, 100) produces Student score 70 / grade 2 and Senior score
52.5 / grade 3. Both are tested. Identical real locations may receive the same
integer grade under both profiles; selection changes weights, not measurements.
The student rules are numerically unchanged from `student-draft-v1`; the new model
version identifies the addition of the documented senior rules. Historical Sprint 1
evidence retains its original model identifier. The profile descriptions are draft
design rationales, not research findings or claims about individual preferences.

## Validation and cache policy

Full layer downloads must have a recognized CRS, nonzero numeric total matching
the returned count, unique feature IDs and valid geometry for **every** record.
Required coordinates must be numeric, finite and in longitude/latitude bounds.
One invalid coordinate rejects the layer; features are not silently dropped.
Boundary validation additionally requires the unique district numbers 1–23 and
valid Polygon/MultiPolygon geometry. JTS `covers` includes the city boundary;
holes are respected. The address label/postcode alone never authorizes scoring.

Missing amenity names are a display limitation, not missing distance measurements:
retain their valid coordinates, label them `Unnamed feature (source ID)` and
disclose the count in the source details. The live transit snapshot had seven
blank names on 17 September 2026. No substitute coordinates or scores are invented.

Snapshots are loaded lazily, held in memory and saved as one atomic JSON envelope
per layer containing retrieval time, raw GeoJSON and SHA-256. Disk snapshots are
revalidated on process startup/first use; checksum failures are ignored. A complete
new snapshot replaces the old one only after validation. Concurrent requests share
one refresh per layer. Defaults: refresh after 24 h; on failure allow an explicitly
identified fallback younger than 7 days; retry failed sources after 5 min. Exactly
at the maximum age the snapshot is no longer usable. A failed disk write is logged;
the validated in-memory snapshot remains usable for the running process.

The 24 h / 7 day policy is a provisional operational choice, not a source freshness
guarantee. Each network call has a 15 s timeout and connection establishment has a
5 s timeout. Address queries are live and are not persisted. Up to six sequential
requests are needed on the first assessment. No source availability guarantee is
assumed. Missing boundary or any mandatory amenity layer with no usable fallback
means **no overall score or grade**. No silent zero distances, partial grades or
weight renormalization are used.

For repeatability retain the response (resolved coordinates, model, measurements,
source IDs and hashes) and cache envelopes together. The API does not provide
historical replay. Identical address coordinates, model and snapshot contents
produce the same numerical result; live source refreshes may change it. The cache
is operational, not a permanent archive. Do not equate a new retrieval hash with
changed amenities: the raw response also contains a service timestamp.

## Limits and remaining research

- Straight-line point distances are not walking distance, route accessibility,
  park entrance distance or an assessment of park size/quality.
- Stops describe proximity, not routes, frequency, reliability or step-free access.
- Pharmacies are locations; opening hours and night duty are excluded.
- Only municipal libraries are included, not university libraries or all study spaces.
- Source mistakes can remain despite format/completeness checks. There is no
  independent real-world validation of every point or assurance that all amenities
  are mapped. Regionally unusual but globally valid coordinates are retained;
  review such cases before the final model.
- Affordability, housing, safety, noise, air pollution and subjective preferences
  are not measured. A nearby air-monitoring station is not an air-quality score.
- Exact-address coverage and usability need broader testing. A failed Vienna-only
  search cannot reliably distinguish a typo from an outside-Vienna address.

No account, database, map, air-quality integration or profile beyond Student/Senior is included.
