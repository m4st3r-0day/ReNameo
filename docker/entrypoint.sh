#!/bin/sh
# ReNameo container entrypoint
#
#   <any renameo arguments>   run the command once, e.g. -rename /media/downloads --action test -non-strict
#   watch                     rename what arrives in $WATCH_DIR every $WATCH_INTERVAL seconds
set -e

CONFIG="${RENAMEO_CONFIG:-/config}"
PUID="${PUID:-1000}"
PGID="${PGID:-1000}"

mkdir -p "$CONFIG/logs"

# run as the owner of the media files (PUID/PGID), like other NAS containers
if [ "$PUID" = "0" ]; then
  RUN=""
else
  getent group "$PGID" >/dev/null 2>&1 || groupadd -o -g "$PGID" renameo
  getent passwd "$PUID" >/dev/null 2>&1 || useradd -o -u "$PUID" -g "$PGID" -d "$CONFIG" -s /usr/sbin/nologin renameo
  chown -R "$PUID:$PGID" "$CONFIG" 2>/dev/null || true
  RUN="gosu $PUID:$PGID"
fi

if [ "$1" != "watch" ]; then
  exec $RUN renameo "$@"
fi

# ---------------------------------------------------------------------------
# watch mode
# ---------------------------------------------------------------------------
if [ -z "$WATCH_DIR" ]; then
  echo "watch mode needs WATCH_DIR (the folder to watch, e.g. /media/downloads)" >&2
  exit 1
fi
if [ -z "$TMDB_API_KEY" ]; then
  echo "watch mode needs TMDB_API_KEY (free key from https://www.themoviedb.org/settings/api)" >&2
  exit 1
fi

INTERVAL="${WATCH_INTERVAL:-300}"
SETTLE="${WATCH_SETTLE:-120}"
ACTION="${RENAMEO_ACTION:-move}"
CONFLICT="${RENAMEO_CONFLICT:-skip}"
LANGUAGE="${RENAMEO_LANG:-en}"

set -- -rename -r "$WATCH_DIR" --action "$ACTION" --conflict "$CONFLICT" --lang "$LANGUAGE" -non-strict \
  --file-filter "f.lastModified() < new Date().time - $SETTLE * 1000" \
  --log-file "$CONFIG/logs/watch.log"

if [ -n "$RENAMEO_FORMAT" ]; then
  # custom format, e.g. "{n} ({y})/{n} ({y})"
  set -- "$@" --format "$RENAMEO_FORMAT"
  [ -n "$OUTPUT_DIR" ] && set -- "$@" --output "$OUTPUT_DIR"
elif [ -n "$RENAMEO_NAMING" ] && [ -n "$OUTPUT_DIR" ]; then
  # move into a library: Movies/..., Shows/... under OUTPUT_DIR
  set -- "$@" --naming "$RENAMEO_NAMING" --output "$OUTPUT_DIR"
elif [ -n "$RENAMEO_NAMING" ]; then
  # rename in place with the media server convention: the folder of a series or movie gets its proper name, episodes go into season folders
  set -- "$@" --format "{$RENAMEO_NAMING.tidy}"
fi

[ -n "$RENAMEO_DB" ] && set -- "$@" --db "$RENAMEO_DB"

echo "ReNameo watch mode: $WATCH_DIR every ${INTERVAL}s (files untouched for ${SETTLE}s), action $ACTION"
while true; do
  $RUN renameo "$@" || true
  sleep "$INTERVAL"
done
