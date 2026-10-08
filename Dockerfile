# ReNameo command line for servers, NAS (TrueNAS SCALE, Unraid, Synology) and Docker.
#
#   docker build -t renameo .
#   docker run --rm -e TMDB_API_KEY=... -v /path/to/media:/media renameo -rename /media/downloads --action test -non-strict
#
# See docker/README.md for the watch mode and docker-compose.

# ---------------------------------------------------------------------------
# build the jar and a small Java runtime
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk AS build

RUN apt-get update \
 && apt-get install -y --no-install-recommends ant ivy \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /src
COPY ivy.xml build.xml ./
RUN ant -lib /usr/share/java/ivy.jar resolve

COPY . .
RUN ant -lib /usr/share/java/ivy.jar fatjar

# same Java modules and languages as the macOS app
RUN jlink \
      --add-modules java.base,java.compiler,java.desktop,java.instrument,java.logging,java.management,java.management.rmi,java.naming,java.net.http,java.prefs,java.scripting,java.sql,java.xml,jdk.charsets,jdk.crypto.ec,jdk.localedata,jdk.unsupported,jdk.zipfs \
      --include-locales=en,it,de,fr,es,pt,nl \
      --strip-debug --no-header-files --no-man-pages --compress=zip-9 \
      --output /opt/runtime

# ---------------------------------------------------------------------------
# runtime image
# ---------------------------------------------------------------------------
FROM debian:bookworm-slim

RUN apt-get update \
 && apt-get install -y --no-install-recommends libmediainfo0v5 ca-certificates tzdata gosu fontconfig \
 && rm -rf /var/lib/apt/lists/*

COPY --from=build /opt/runtime /opt/java
COPY --from=build /src/dist/ReNameo_1.0.0.jar /opt/renameo/renameo.jar
COPY docker/renameo /usr/local/bin/renameo
COPY docker/entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/renameo /usr/local/bin/entrypoint.sh

ENV PATH="/opt/java/bin:${PATH}" \
    PUID=1000 \
    PGID=1000 \
    TZ=Etc/UTC \
    RENAMEO_CONFIG=/config

VOLUME ["/config"]

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
CMD ["-help"]

LABEL org.opencontainers.image.title="ReNameo" \
      org.opencontainers.image.description="Rename movies, TV shows, anime, music and subtitles with Plex / Jellyfin / Emby / Kodi naming" \
      org.opencontainers.image.source="https://github.com/m4st3r-0day/ReNameo" \
      org.opencontainers.image.licenses="LicenseRef-MDBADPL"
