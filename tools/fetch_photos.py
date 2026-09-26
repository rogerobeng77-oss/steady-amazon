#!/usr/bin/env python3
"""Fetch Steady's exercise photographs from Wikimedia Commons.

Commons rather than a stock site, because this submission goes public and every image
in it has to be one we can show a licence for. Commons returns the licence, the
photographer and the file page as machine-readable metadata on the same request that
returns the image, so ATTRIBUTION.md is generated from what the server said rather than
from a claim on a landing page. Anything whose licence forbids commercial use or
derivative works is rejected here rather than checked by hand later.

    python3 tools/fetch_photos.py --search "senior exercise chair"   # candidates
    python3 tools/fetch_photos.py --build                            # write the files
"""
from __future__ import annotations

import argparse
import json
import os
import urllib.parse
import urllib.request

API = "https://commons.wikimedia.org/w/api.php"
UA = "Steady-photo-fetch/1.0 (hackathon entry; contact via repo)"
RES = os.path.join(os.path.dirname(__file__), "..", "app/src/main/res/drawable-nodpi")

# Licences that permit redistribution and commercial use. Anything carrying NC or ND is
# not on this list and is dropped before download.
ALLOWED = (
    "cc0", "public domain", "pd-", "no restrictions",
    "cc by 4.0", "cc by 3.0", "cc by 2.0",
    "cc by-sa 4.0", "cc by-sa 3.0", "cc by-sa 2.0",
)

# Which Commons file stands for which exercise. Pinned by title rather than by search
# rank so that a re-run cannot silently change the picture on a card.
CHOSEN = json.load(open(os.path.join(os.path.dirname(__file__), "photos.json"))) \
    if os.path.exists(os.path.join(os.path.dirname(__file__), "photos.json")) else {}


def _get(params: dict) -> dict:
    url = f"{API}?{urllib.parse.urlencode(params)}"
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.load(r)


def plain(html: str) -> str:
    """Commons returns author and credit as HTML fragments."""
    out, depth = [], 0
    for ch in html:
        if ch == "<":
            depth += 1
        elif ch == ">":
            depth -= 1
        elif depth == 0:
            out.append(ch)
    return " ".join("".join(out).split())[:160]


def usable(licence: str) -> bool:
    low = licence.lower()
    if "nc" in low.replace("-", " ").split() or "-nd" in low:
        return False
    return any(low.startswith(ok) for ok in ALLOWED)


def search(query: str, limit: int = 40) -> list[dict]:
    data = _get({
        "action": "query", "format": "json", "formatversion": "2",
        "generator": "search", "gsrsearch": f"filetype:bitmap {query}",
        "gsrnamespace": "6", "gsrlimit": str(limit),
        "prop": "imageinfo", "iiprop": "url|extmetadata|size", "iiurlwidth": "420",
    })
    out = []
    for page in data.get("query", {}).get("pages", []):
        info = (page.get("imageinfo") or [{}])[0]
        meta = info.get("extmetadata", {})
        licence = (meta.get("LicenseShortName", {}).get("value") or "").strip()
        if not usable(licence) or info.get("width", 0) < 1200:
            continue
        out.append({
            "title": page["title"], "licence": licence,
            "author": plain(meta.get("Artist", {}).get("value", "")) or "unnamed",
            "page": info.get("descriptionurl"), "thumb": info.get("thumburl"),
        })
    return out


def build() -> None:
    """Download each chosen file, crop it to 16:9 and write it into res/."""
    from PIL import Image  # only needed for --build

    titles = sorted({v["title"] for v in CHOSEN.values()})
    data = _get({
        "action": "query", "format": "json", "formatversion": "2",
        "titles": "|".join(titles), "prop": "imageinfo",
        "iiprop": "url|extmetadata|size", "iiurlwidth": "2000",
    })
    meta = {}
    for page in data["query"]["pages"]:
        info = page["imageinfo"][0]
        em = info["extmetadata"]
        licence = em.get("LicenseShortName", {}).get("value", "")
        if not usable(licence):
            raise SystemExit(f"licence changed on {page['title']}: {licence}")
        meta[page["title"]] = {
            "licence": licence, "page": info["descriptionurl"],
            "author": plain(em.get("Artist", {}).get("value", "")) or "unnamed",
            "thumb": info["thumburl"],
        }

    os.makedirs(RES, exist_ok=True)
    for key, pick in CHOSEN.items():
        m = meta[pick["title"]]
        req = urllib.request.Request(m["thumb"], headers={"User-Agent": UA})
        raw = os.path.join("/tmp", f"steady-{key}.jpg")
        with urllib.request.urlopen(req, timeout=120) as r, open(raw, "wb") as f:
            f.write(r.read())
        im = Image.open(raw).convert("RGB")
        w, h = im.size
        target = int(w * 9 / 16)
        centre = int(h * pick.get("bias", 0.48))
        top = max(0, min(h - target, centre - target // 2))
        out = im.crop((0, top, w, top + target)).resize((1280, 720), Image.LANCZOS)
        out.save(os.path.join(RES, f"photo_{key}.jpg"), quality=82, optimize=True)
        print(f"{key:18s} {m['licence']:<14} {m['author']}")


if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("--search")
    p.add_argument("--build", action="store_true")
    a = p.parse_args()
    if a.search:
        for r in search(a.search):
            print(f"{r['licence']:<16} {r['title']}")
    if a.build:
        build()
