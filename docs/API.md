# Assessment API

All coordinates are WGS84 / EPSG:4326, longitude then latitude internally.
The browser uses relative `/api` URLs; development forwards them to port 8081.
Docker serves Angular and the API from the same origin.

## Requests

`POST /api/assessments`, `Content-Type: application/json`:

```json
{"address":"Hoher Markt 1, 1010 Wien","profile":"SENIOR"}
```

`profile` accepts `STUDENT` or `SENIOR` (case-insensitive, surrounding spaces ignored).
Omitted/null profile defaults to Student for compatibility with the original API.
Empty or unknown values receive HTTP 400, never a silently substituted profile.
There is no coordinate or weight override. `POST /api/addresses/resolve` accepts
an address-only body and exposes just the address resolution step for development.

Input is limited to 160 characters. Leading/trailing spaces and repeated ordinary
whitespace are normalized. Supported forms: `Hoher Markt 1`, `Hoher Markt 1 Wien`,
`Hoher Markt 1, 1010 Wien`, `Hoher Markt 1 1010`, `Hoher Markt 1, 1010 Vienna`. Simple house-letter suffixes and
number ranges/slashes are accepted syntactically; they must exist exactly in the
source table. Street spelling/case must match the source. No fuzzy geocoder,
autocomplete, street aliases or arbitrary city suffixes are promised. Explicit
Top/Tür/Wohnung/Apartment/Apt/Stiege number markers are rejected with guidance to
enter the building address. They are not silently stripped; a slash remains part
of the exact house number, not an assumed apartment separator.
The postcode, when supplied, participates in the query; it is not a boundary check.

The backend uses an exact `NAME = '...'` filter and optional `PLZ = '...'`.
Apostrophes are doubled inside CQL string literals, then the complete parameter
is URL-encoded. It requests at most 10 address records and inspects `totalFeatures`
before accepting a unique match. More than one match asks for clearer input,
even when the response is truncated. It never chooses the first match.
Returned street/house labels and any supplied postcode are checked against the
query too; a source that ignores the filter cannot silently substitute an address.

## Assessment response

HTTP 200 carries an assessment outcome. HTTP 400 carries
`{"status":"INVALID_INPUT","message":"..."}` for malformed JSON or invalid input.
Infrastructure failures can still return ordinary HTTP errors; the browser handles them.

| Outcome `status` | Meaning |
|---|---|
| `OK` | Unique address, verified boundary and all four usable datasets; numeric score and grade present. |
| `NO_MATCH` | No exact match in the Vienna address table; could be spelling, an unsupported form or a location outside Vienna. |
| `AMBIGUOUS` | Multiple matches; candidates are informational and no location is selected. |
| `LOOKUP_UNAVAILABLE` | Address service timed out, failed or returned an unusable/incomplete response. |
| `OUTSIDE_VIENNA` | Resolved coordinates are outside the union of the 23 district polygons. |
| `DATA_UNAVAILABLE` | Boundary cannot be checked or at least one mandatory amenity dataset is unusable. |

Fields:

- `status`, `message`, `profile: "Student" | "Senior"`, `profileDescription`,
  `modelVersion: "profiles-draft-v1"`. Description explains provisional priorities,
  not a validated claim about every member of a demographic group.
- `location`: source ID, resolved label, longitude and latitude; null unless uniquely resolved.
- `candidates`: up to 10 records on ambiguity; otherwise empty.
- `addressRetrievedAt`: UTC retrieval time for the lookup, or null if unavailable.
- `score`, `grade`: unrounded numeric total and integer 1–5, both null unless `OK`.
- `factors`: category, nearest feature ID/name, straight-line distance in metres,
  distance limit, category score, fractional weight and contribution.
- `sources`: boundary plus amenity layer identifiers/URLs, attribution/license,
  snapshot retrieval times, full SHA-256, record counts, unnamed record counts,
  state, fallback messages and limitations.

Source states are `LIVE` (just fetched), `CACHED` (validated and younger than
the refresh interval), `STALE_FALLBACK` (refresh failed; still within maximum age),
and `UNAVAILABLE`. They describe retrieval/cache state, not verified freshness
of the underlying feature observations. A non-OK response has no overall grade.
Available factors in a data failure are shown only as individual measurements;
weights are never renormalized.

## Interface behavior

One labeled profile selector, address field and submit button lead to a loading message, followed
by either a result or actionable explanation. The result identifies the actual
location, grade, profile, factors and source snapshots. Changing the input clears
all previous output, including source details, on either address or profile changes.
During a request the controls are disabled to avoid racing requests. Duplicate
submissions are ignored. After 120 seconds a client timeout cancels the subscription
and enables retry. Source failures/fallbacks are expanded by default; fallback dates
also appear near the result. Missing datasets never show an overall-score explanation.
Cards explain each contribution and zero-score distance limits. Cards stack on narrow
screens; controls and source disclosures support keyboard navigation. A persistent
polite live region announces outcomes; HTTP errors use an alert.
