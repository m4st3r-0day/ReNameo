# ReNameo in Docker and on TrueNAS SCALE

The image contains the ReNameo command line, a small Java runtime and MediaInfo. It runs on `linux/amd64` and `linux/arm64`.

You need a free TheMovieDB API key: create an account on [themoviedb.org](https://www.themoviedb.org/signup) and copy the **API Key** from [Settings → API](https://www.themoviedb.org/settings/api).

## Run a command once

```bash
docker run --rm \
  -e TMDB_API_KEY=your-key \
  -v /path/to/media:/media \
  -v ./config:/config \
  ghcr.io/m4st3r-0day/renameo -rename /media/downloads -r --action test -non-strict
```

Everything after the image name is a normal `renameo` command, see the [command line guide](../docs/USER_GUIDE_CLI.md). Start with `--action test`, which only prints what would happen.

## Watch a folder

With `watch` as the command, the container renames what arrives in `WATCH_DIR` at regular intervals. Files changed in the last `WATCH_SETTLE` seconds are skipped, so downloads in progress are left alone.

```bash
docker compose -f docker/docker-compose.yml up -d
```

| Variable | Default | Meaning |
| --- | --- | --- |
| `TMDB_API_KEY` | (required) | TheMovieDB API key |
| `WATCH_DIR` | (required) | folder to watch, e.g. `/media/downloads` |
| `WATCH_INTERVAL` | `300` | seconds between runs |
| `WATCH_SETTLE` | `120` | skip files changed in the last N seconds |
| `RENAMEO_ACTION` | `move` | `test`, `move`, `copy`, `hardlink`, `symlink` |
| `RENAMEO_NAMING` | | `plex`, `jellyfin`, `emby`, `kodi` |
| `OUTPUT_DIR` | | with `RENAMEO_NAMING`: build `Movies/` and `Shows/` there; without it files are renamed in place |
| `RENAMEO_FORMAT` | | custom format instead of `RENAMEO_NAMING`, e.g. `{n} ({y})/{n} ({y})` |
| `RENAMEO_LANG` | `en` | language of the titles, e.g. `it` |
| `RENAMEO_CONFLICT` | `skip` | `skip`, `override`, `auto`, `index` |
| `RENAMEO_DB` | auto | force a source, e.g. `TheMovieDB::TV` |
| `PUID` / `PGID` | `1000` | user and group that own the media files (`0` runs as root) |
| `TZ` | `Etc/UTC` | time zone of the log |
| `OMDB_API_KEY`, `FANARTTV_API_KEY`, `ACOUSTID_API_KEY` | | optional keys |
| `OPENSUBTITLES_API_KEY`, `OPENSUBTITLES_USER`, `OPENSUBTITLES_PASSWORD` | | for `-get-subtitles` |

Settings, cache, rename history and `logs/watch.log` are kept in `/config`, so `-revert` still works after the container restarts:

```bash
docker run --rm -v ./config:/config -v /path/to/media:/media ghcr.io/m4st3r-0day/renameo -revert /media/downloads
```

**Hard links instead of moves:** with `RENAMEO_ACTION=hardlink` the downloads stay where they are (for seeding) and the library gets a second name for the same data, without using more space. Downloads and library must be on the same file system, which on TrueNAS means the same dataset.

## TrueNAS SCALE

TrueNAS SCALE runs Docker containers as *Custom Apps*.

1. **Datasets.** Create a dataset for the configuration, e.g. `tank/apps/renameo`. Note the owner of your media dataset: *Datasets → media → Permissions* (often the `apps` user, UID 568).
2. **Install.** *Apps → Discover Apps → Custom App* (or *Install via YAML*, then paste an adapted `docker-compose.yml`).
   - **Image:** `ghcr.io/m4st3r-0day/renameo`, tag `latest`.
   - **Command:** `watch`.
   - **Environment:** `TMDB_API_KEY`, `WATCH_DIR=/media/downloads`, `RENAMEO_NAMING=jellyfin` (or `plex`), `RENAMEO_ACTION=test` for the first run, `PUID`/`PGID` = owner of the media dataset (e.g. `568`), `TZ`.
   - **Storage:** host path `/mnt/tank/apps/renameo` → `/config`, and host path `/mnt/tank/media` → `/media`.
3. **Check.** Open the app's logs, or `/mnt/tank/apps/renameo/logs/watch.log`. When the planned names look right, change `RENAMEO_ACTION` to `move` (or `hardlink`) and update the app.

## Build the image yourself

```bash
docker build -t renameo .
```

The build compiles ReNameo from source. `profile.properties` is excluded by `.dockerignore`, so no API keys end up in the image.
