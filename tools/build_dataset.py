#!/usr/bin/env python3
"""Builds the offline dataset bundled in the app (Galicia plus a border margin).

Downloads the same features the app queries live, tile by tile, keeps only the tags the
app reads and writes an Overpass-shaped gzip JSON, so the app parses it with the same
OverpassParser it uses for live responses.

Usage: python3 tools/build_dataset.py [output_dir]
"""
import gzip
import json
import sys
import time
import urllib.parse
import urllib.request
from datetime import date
from pathlib import Path

# Galicia plus ~40 km so views towards Asturias, León, Zamora and Portugal are covered.
SOUTH, WEST, NORTH, EAST = 41.40, -9.70, 44.00, -6.20
TILE_ROWS, TILE_COLS = 4, 4

ENDPOINTS = [
    "https://overpass-api.de/api/interpreter",
    "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
    "https://overpass.private.coffee/api/interpreter",
    "https://overpass.kumi.systems/api/interpreter",
]
USER_AGENT = "GalMap-dataset-builder/0.1"
KEPT_TAGS = {
    "name", "name:gl", "natural", "place", "waterway", "mountain_pass", "ele", "wikidata", "wikipedia",
}
# Must match OverpassClient.buildQuery.
QUERY = """[out:json][timeout:180][bbox:{bbox}];
(
  nwr["natural"~"^(peak|volcano|saddle|cape)$"]["name"];
  nwr["mountain_pass"="yes"]["name"];
  nwr["place"~"^(island|islet)$"]["name"];
  nwr["waterway"="waterfall"]["name"];
);
out center tags;"""


def fetch_tile(s, w, n, e, attempts=6):
    data = urllib.parse.urlencode({"data": QUERY.format(bbox=f"{s:.4f},{w:.4f},{n:.4f},{e:.4f}")}).encode()
    for attempt in range(attempts):
        endpoint = ENDPOINTS[attempt % len(ENDPOINTS)]
        try:
            req = urllib.request.Request(endpoint, data=data, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(req, timeout=200) as resp:
                return json.load(resp)["elements"]
        except Exception as exc:  # noqa: BLE001 - any failure means "try the next mirror"
            print(f"    {endpoint.split('/')[2]}: {exc}", flush=True)
            time.sleep(10 * (attempt + 1))
    raise RuntimeError(f"Tile {s},{w},{n},{e} failed after {attempts} attempts")


def slim(element):
    out = {k: element[k] for k in ("type", "id", "lat", "lon", "center") if k in element}
    out["tags"] = {k: v for k, v in element.get("tags", {}).items() if k in KEPT_TAGS}
    return out


def main():
    out_dir = Path(sys.argv[1] if len(sys.argv) > 1 else "app/src/main/assets")
    out_dir.mkdir(parents=True, exist_ok=True)
    d_lat = (NORTH - SOUTH) / TILE_ROWS
    d_lon = (EAST - WEST) / TILE_COLS
    elements = {}
    for row in range(TILE_ROWS):
        for col in range(TILE_COLS):
            s, w = SOUTH + row * d_lat, WEST + col * d_lon
            print(f"tile {row * TILE_COLS + col + 1}/{TILE_ROWS * TILE_COLS} ({s:.2f},{w:.2f})", flush=True)
            for el in fetch_tile(s, w, s + d_lat, w + d_lon):
                elements[(el["type"], el["id"])] = slim(el)
            print(f"  total so far: {len(elements)}", flush=True)

    body = json.dumps({"elements": list(elements.values())}, ensure_ascii=False, separators=(",", ":"))
    with gzip.open(out_dir / "dataset.json.gz", "wt", encoding="utf-8") as f:
        f.write(body)
    meta = {
        "south": SOUTH, "west": WEST, "north": NORTH, "east": EAST,
        "generated": date.today().isoformat(), "elements": len(elements),
    }
    (out_dir / "dataset_meta.json").write_text(json.dumps(meta, indent=2) + "\n")
    print(f"wrote {len(elements)} elements to {out_dir}", flush=True)


if __name__ == "__main__":
    main()
