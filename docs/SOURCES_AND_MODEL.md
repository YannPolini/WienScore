# Sources and neighbourhood proximity model

Current model: **`neighbourhood-proximity-v3`**, implemented 23 September 2026.
WienScore estimates proximity to selected neighbourhood amenities. It is not an
individual wellbeing assessment, official Vienna rating or empirically validated
quality-of-life index. Planning evidence informs some targets; preferences and
scoring transformations remain explicit design choices.

## Measurements and evidence

All distances are straight-line proxies, never walking routes or travel times.

| Domain | Measurements and distance benchmarks | Aggregation | Basis and limitations |
|---|---|---|---|
| Public green space | Any mapped area within 250 m; ≥1 ha within 500 m; ≥3 ha within 1,000 m; ≥10 ha within 1,500 m | Mean of four scores | Adapted from Vienna planning benchmarks; distance to polygon boundary, not a validated entrance. Uses each record's FLAECHE, not tree canopy or a raster pixel count. |
| Public transport | Bus/tram within 500 m; U-Bahn/regional rail within 1,000 m | Better of the two scores | UN-Habitat distinguishes low/high-capacity transport using walking-network distance. Our straight-line substitution is explicitly a proxy, not an SDG-compliant calculation. |
| Primary healthcare | Pharmacy within 500 m; general practitioner within 1,000 m | Equal mean | Project hypotheses about useful proximity, not universal WHO standards. No insurance, opening-hours or appointment-capacity assessment. |
| Municipal libraries | Within 1,000 m | Single score | Project hypothesis for local learning access. Does not cover university libraries or all study resources. |

Primary references, consulted 23 September 2026:

- [Vienna's current green/open-space planning benchmarks](https://www.wien.gv.at/spezial/wien-plan/stadtentwicklung-steuern-und-gestalten/grun-und-freiraum/): local distance and size scales. These are planning benchmarks, not a validated personal QoL formula.
- [City green-space survey methodology](https://www.wien.gv.at/umwelt/umweltgut-oeffentlich): public, free access; 2013–2015 survey; excludes residential grounds and cemeteries. Some landscapes are represented by path corridors. A fresh download does not make these observations current.
- [UN-Habitat indicator 11.2.1 metadata](https://unhabitat.org/sites/default/files/2020/11/metadata_on_sdg_indicator_11.2.1_2020_1.pdf): 500/1,000 m walking-network access benchmarks.
- [WHO age-friendly environments handbook](https://www.who.int/europe/publications/i/item/9789289056076): motivates outdoor-space, transport and healthcare dimensions; does not establish profile weights.
- [OECD/JRC composite-indicator handbook](https://www.oecd.org/en/publications/handbook-on-constructing-composite-indicators-methodology-and-user-guide_9789264043466-en.html): transparent normalization, weighting and sensitivity analysis. It does not validate this application.

## Formula and profiles

For measured distance d and target a:

```text
A(d, a) = 100 − 20 × d / a              when 0 ≤ d ≤ a
          80 × (3a − d) / (2a)           when a < d < 3a
          0                              when d ≥ 3a
```

The v2 plateau assigned identical 100-point scores to all distances inside each
benchmark. This erased proximity differences and made profile weights irrelevant
when every domain saturated. V3 removes that plateau without moving the distance
benchmarks or changing weights to fit sample addresses.

The score now continuously measures proximity convenience: 100 at zero distance,
80 at the benchmark, and zero at 3×. Mapping the benchmark to 80 (the existing
highest-grade boundary) is an explicit engineering convention, not a scientifically
established wellbeing effect. The breakpoint is continuous. The separate
`withinTarget` flag reports benchmark attainment even when the score is below 100.
A closer point yields more proximity credit, not a claim of extra health benefits.
Linear interpolation, the 80-point anchor and the 3× cutoff remain design assumptions. No qualifying feature in an otherwise complete dataset earns zero
for that measurement, with null distance/feature ID and an explicit state. A
failed, malformed or incomplete dataset is a different case and withholds the
overall grade. Unmet green-size targets remain in the four-measure average.

| Domain | Student | Senior |
|---|---:|---:|
| Green space | 31.25% | 31.25% |
| Transport | 37.5% | 25% |
| Healthcare | 18.75% | 37.5% |
| Libraries | 12.5% | 6.25% |

These fixed four-domain presets preserve the ratios of the research proposal's
non-noise weights: Student 25/30/15/10 and Senior 25/20/30/5, each divided by 80.
Noise is outside this separately named version. This is a declared model design,
not runtime redistribution of missing data. Weights represent illustrative
preferences, not effect sizes from research or surveyed demographic preferences.

Domain contribution = domain score × profile weight. Overall score = sum of all
four contributions. Grade thresholds remain ≥80 → 1, ≥60 → 2, ≥40 → 3, ≥20 → 4,
otherwise 5. These display conventions are not externally validated. Grade and
aggregation use unrounded values; UI rounding can slightly change displayed sums.
Controlled scores (100, 50, 0, 100) give Student 62.5 / grade 2 and Senior 50 /
grade 3. Identical measurements can legitimately produce identical grades or
scores if their weighted measurements coincide; distinct profiles are not forced
to disagree. Full unrounded 100 requires zero distance in every contributing
measurement (or a zero-distance winning transport mode), not merely meeting
benchmarks. Rounded display values can still coincide. Grade 1 may legitimately
be common for well-served areas; this is not a percentile ranking.

Every successful response includes cutoff sensitivity: recompute with zero at
2a and 4a, keeping the same 100-to-80 segment through a, domains, weights and snapshots.
The UI shows both scores and grades. This tests one modelling assumption; it is
not a confidence interval, empirical validation or a complete uncertainty analysis.
Weight/equal-domain comparisons are recorded separately in the validation evidence.

## Source manifest

**Datenquelle: Stadt Wien – https://data.wien.gv.at**; selected distributions are
[CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). Per-dataset catalog
responses, licenses, retrieval times and update labels are recorded in
[source-metadata.json](source-metadata.json). New sources were checked on
23 September 2026; historical sources retain their original check dates.

| Catalog | WFS layer | Role / required properties | Download cap |
|---|---|---|---:|
| [Addresses](https://www.data.gv.at/datasets/1d5c2411-9719-4c8f-b99d-57a5f4a4ae41) | ADRESSENOGD | NAME, PLZ, official feature ID; live lookup/suggestions | 8 suggestions / 10 exact candidates |
| [Public green space](https://www.data.gv.at/datasets/d0145df8-7f6d-46e1-9bc6-ee7897054104) | OEFFGRUENFLOGD | T_LANG, FLAECHE, valid Polygon/MultiPolygon | 5,000 |
| [Transport network stops](https://www.data.gv.at/datasets/f1f6f15d-2faa-4b62-b78b-80599dd1c66e) | OEFFHALTESTOGD | HTXT, LTYP, point | 20,000 |
| [Doctors](https://www.data.gv.at/datasets/fdcaabe5-0efd-4701-8fd9-49935c0d9cff) | ARZTOGD | NAME, FACH, point | 15,000 |
| [Pharmacies](https://www.data.gv.at/datasets/cf00fe06-e4b2-4319-af00-85230e7b2f1b) | APOTHEKEOGD | BEZEICHNUNG, point | 2,000 |
| [Libraries](https://www.data.gv.at/datasets/2adc5a86-faa3-4b63-acff-a66128b811b7) | BUECHEREIOGD | NAME, point | 1,000 |
| [District boundaries](https://www.data.gv.at/datasets/2ee6b8bf-6292-413c-bb8b-bd22dbb2ad4b) | BEZIRKSGRENZEOGD | NAMEK, BEZNR, polygon | 100 |

WFS 1.1.0 requests explicitly select EPSG:4326 and JSON, at
`https://data.wien.gv.at/daten/geo`. Coordinates are longitude then latitude.
The complete raw count must match the response and fit the cap; caps are not
pagination. IDs must be unique. Unknown transport codes, missing specialties,
invalid eligible geometry or invalid green-space sizes reject the snapshot.
Names may be blank; valid features remain with their ID and a disclosed unnamed count.

GP selection uses exact FACH values `Allgemeinmedizin` and
`Allgemein- und Familienmedizin` after trimming. A substring search for
`Allgemein` would wrongly include general surgery and is deliberately not used.
The audit found 2,005 eligible records among 7,592 physician records. Several
records can share coordinates; nearest-distance scoring does not give extra
credit for duplicate practices or imply a count of independent providers.

Transport follows the catalog codebook: 1 tram, 2 bus, 3 regional bus, 4 U-Bahn,
5 S-/regional rail, 6 Badner Bahn. Codes 1/2/3/6 use the 500 m proxy, 4/5 the
1,000 m proxy; 9–13 are excluded from daytime scoring. The live data included
WLB-named rows under code 9 despite the catalog describing 9 as Astax. These are
excluded rather than silently reclassified by line name. This source inconsistency
limits completeness for Badner Bahn and should be resolved with the publisher.
No arbitrary name parsing overrides the authoritative field definitions.

Point distances use haversine with radius 6,371,000 m. Green polygons are projected
once into a local equirectangular metric plane centred at 16.37°E, 48.2°N. Distance
to their full filled geometry respects holes and is zero inside/on the polygon.
This is an approximation suitable for Vienna-scale proximity, not survey accuracy.
Ties are broken by source ID. Sizes use source FLAECHE square metres, not degrees.
Polygon records are not merged: disconnected/truncated-key groups are not assumed
to be a single accessible park. Fragmented sites may therefore be underestimated.
The entrance layer was audited (6,562 points), but unmatched keys, fragmented
polygons and unresolved point-type semantics prevent a reliable entrance join.

## Validation, caching and failure policy

The address must be covered by one of all 23 validated district polygons, including
edges; holes are respected. A postcode/name alone never proves the location is in Vienna.

Complete snapshots are checksum-protected raw JSON envelopes, atomically saved,
revalidated after restart and shared by concurrent requests. Refresh after 24 h;
if refresh fails, explicitly labelled fallback may be used for less than 7 days.
Exactly at 7 days it expires. Failed refresh retries back off 5 minutes. A failed
disk write does not discard valid in-memory data. These are operational choices,
not guarantees of observation freshness. Source featureCount reports eligible
records after documented filters; raw snapshot hashes cover the complete response.

Each network request times out after 15 seconds (connection timeout 5 seconds).
First assessment can require the address plus six source downloads. Missing
boundary blocks all scoring. Missing any required domain layer, including either
healthcare layer, withholds the overall score/grade and sensitivity. Available
factors may be shown individually; no partial grade or weight redistribution.
Addresses remain live, unpersisted lookups. Source states distinguish live,
cached, stale fallback and unavailable data.

Retain response and cache envelopes for reproducibility. The model identifier,
measurements, source IDs and SHA-256 identify the calculation inputs. No historical
replay endpoint is provided. Old `profiles-draft-v1` evidence remains historical
and is not presented as validation of v3. V2 evidence also remains historical.

## Explicitly deferred from the research proposal

- **Noise:** official 2022 road/rail GeoPackages were located, but source coverage,
  absent-polygon semantics and band censoring were not established. No quietness
  score is invented. A validated Vienna extract and coverage audit are prerequisites.
- **Walking routes and green entrances:** no verified pedestrian network or reliable
  entrance-to-area join is loaded. Straight-line/boundary distances remain visible.
- **Raster greenery/heat/air quality:** raw analytical coverage is not established.
  Styled map pixels and a nearby monitoring station are not substitutes.
- Housing affordability, safety, service quality, individual needs and subjective
  wellbeing remain unmeasured. Suggestions still do not handle arbitrary typos or aliases.

These limits are part of the model definition, not a claim that the entire proposed
five-domain quality-of-life model is complete.
