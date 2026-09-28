#!/usr/bin/env python3
"""Build local TMDB-based series/movie search index for FileBot 4.8.

Produces lzma (XZ) compressed tab-separated data files compatible with
ReleaseInfo.parseSeries / parseMovie:

  series: {tmdb_id}\t{name}\t{alias...}
  movie : {imdb_id(-1)}\t{tmdb_id}\t{year}\t{name}\t{alias...}

Primary names come from the en-US language (English stays the default).
Italian names are added as aliases so Italian video files still match.
"""
import io
import json
import lzma
import sys
import urllib.request

TMDB_API = "https://api.themoviedb.org/3"
PAGES = {"popular": 8, "top_rated": 8, "on_the_air": 3, "airing_today": 3}
MOVIE_PAGES = {"popular": 8, "top_rated": 8, "now_playing": 3, "upcoming": 3}
LANGS = ("en-US", "it-IT")


def api_key():
    with open("app.properties", encoding="utf-8") as f:
        for line in f:
            if line.startswith("apikey.themoviedb:"):
                return line.split(":", 1)[1].strip()
    raise SystemExit("apikey.themoviedb not found in app.properties")


def fetch(path, lang, page):
    url = f"{TMDB_API}{path}?api_key={KEY}&language={lang}&page={page}"
    with urllib.request.urlopen(url, timeout=30) as r:
        return json.load(r)


def fetch_all(path, lang, pages=None):
    out = []
    pages = pages or PAGES
    for page in range(1, pages.get(path.split("/")[-1], 2) + 1):
        try:
            data = fetch(path, lang, page)
        except Exception as e:  # noqa: BLE001 - network errors must not abort the whole run
            print(f"  ! {path} {lang} page {page}: {e}", file=sys.stderr)
            continue
        out.extend(data.get("results", []))
    return out


def compressed(rows):
    raw = "\n".join(rows).encode("utf-8")
    return lzma.compress(raw, preset=6 | lzma.PRESET_EXTREME)


def series_index():
    shows = {}
    for kind in ("popular", "top_rated", "on_the_air", "airing_today"):
        for lang in LANGS:
            print(f"tv/{kind} [{lang}] ...", file=sys.stderr)
            for it in fetch_all(f"/tv/{kind}", lang):
                shows.setdefault(it["id"], {}).update(kind=it)
                shows[it["id"]][lang] = it.get("name")
    rows = []
    for sid, data in sorted(shows.items(), key=lambda kv: kv[1]["kind"].get("popularity", 0), reverse=True):
        en = data.get("en-US") or data["kind"].get("original_name") or data.get("it-IT")
        if not en:
            continue
        aliases = [a for a in (data["kind"].get("original_name"), data.get("it-IT")) if a and a != en]
        rows.append("\t".join([str(sid), en] + aliases))
    return rows


def movie_index():
    movies = {}
    for kind in MOVIE_PAGES:
        for lang in LANGS:
            print(f"movie/{kind} [{lang}] ...", file=sys.stderr)
            for it in fetch_all(f"/movie/{kind}", lang, MOVIE_PAGES):
                movies.setdefault(it["id"], {}).update(kind=it)
                movies[it["id"]][lang] = it.get("title")
    rows = []
    for mid, data in sorted(movies.items(), key=lambda kv: kv[1]["kind"].get("popularity", 0), reverse=True):
        en = data.get("en-US") or data["kind"].get("original_title") or data.get("it-IT")
        if not en:
            continue
        year = (data["kind"].get("release_date") or "")[:4]
        aliases = [a for a in (data["kind"].get("original_title"), data.get("it-IT")) if a and a != en]
        rows.append("\t".join(["-1", str(mid), year or "", en] + aliases))
    return rows


if __name__ == "__main__":
    KEY = api_key()
    import os
    outdir = sys.argv[1] if len(sys.argv) > 1 else "data"
    os.makedirs(outdir, exist_ok=True)

    srows = series_index()
    with open(f"{outdir}/thetvdb.txt.xz", "wb") as f:
        f.write(compressed(srows))
    print(f"series index: {len(srows)} entries -> {outdir}/thetvdb.txt.xz", file=sys.stderr)

    mrows = movie_index()
    with open(f"{outdir}/moviedb.txt.xz", "wb") as f:
        f.write(compressed(mrows))
    print(f"movie index : {len(mrows)} entries -> {outdir}/moviedb.txt.xz", file=sys.stderr)