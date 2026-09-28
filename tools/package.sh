#!/bin/bash
# Build a native installer with jpackage (Java is included, nothing else to install).
#
#   tools/package.sh deb    Ubuntu / Debian, run on Linux (needs fakeroot and dpkg-deb)
#   tools/package.sh msi    Windows, run on Windows (needs the WiX Toolset)
#
# macOS uses tools/make-app.sh instead. Run `ant fatjar` first.
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

TYPE="${1:?usage: tools/package.sh deb|msi}"
VERSION="1.0.0"
JPACKAGE="${JAVA_HOME:+$JAVA_HOME/bin/}jpackage"
MODULES="java.base,java.compiler,java.desktop,java.instrument,java.logging,java.management,java.management.rmi,java.naming,java.prefs,java.scripting,java.sql,java.xml,jdk.accessibility,jdk.charsets,jdk.crypto.ec,jdk.localedata,jdk.unsupported,jdk.zipfs"

INPUT="build/package-input"
rm -rf "$INPUT"
mkdir -p "$INPUT" dist/packages
cp "dist/ReNameo_${VERSION}.jar" "$INPUT/ReNameo.jar"

COMMON=(
  --name ReNameo
  --app-version "$VERSION"
  --vendor "ReNameo"
  --description "Rename movies, TV shows, anime, music and subtitles"
  --copyright "Based on FileBot 4.8.0 by Reinhard Pointner"
  --input "$INPUT"
  --main-jar ReNameo.jar
  --main-class net.renameo.Main
  --add-modules "$MODULES"
  --jlink-options "--strip-debug --no-man-pages --no-header-files --compress=zip-9 --include-locales=en,it,de,fr,es,pt,nl"
  --java-options "-XX:+UseStringDeduplication"
  --dest dist/packages
)

case "$TYPE" in
deb)
  "$JPACKAGE" --type deb "${COMMON[@]}" \
    --icon packaging/linux/renameo.png \
    --linux-package-name renameo \
    --linux-app-category video \
    --linux-menu-group "AudioVideo;Video;Utility" \
    --linux-shortcut \
    --linux-package-deps "libmediainfo0v5"
  ;;
msi)
  "$JPACKAGE" --type msi "${COMMON[@]}" \
    --icon packaging/windows/ReNameo.ico \
    --win-menu --win-menu-group ReNameo \
    --win-shortcut \
    --win-dir-chooser \
    --win-upgrade-uuid 6c0b8a3e-2f4d-4d7e-9c1a-5e3b7a9d2f10 \
    --add-launcher renameo=packaging/windows/cli-launcher.properties
  ;;
*)
  echo "unknown package type: $TYPE" >&2
  exit 1
  ;;
esac

ls -la dist/packages
