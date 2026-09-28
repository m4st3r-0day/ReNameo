#!/bin/bash
# -----------------------------------------------------------------------
# renameo.sh – ReNameo 1.0.0 launcher for macOS (sw-dark-mode)
# -----------------------------------------------------------------------
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAVA_HOME="${JAVA_HOME:-/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home}"
JFX_DIR="${JFX_DIR:-$SCRIPT_DIR/lib/javafx/javafx-sdk-21.0.6/lib}"

JFX_CP="$JFX_DIR/javafx.base.jar:$JFX_DIR/javafx.graphics.jar:$JFX_DIR/javafx.controls.jar:$JFX_DIR/javafx.swing.jar:$JFX_DIR/javafx.web.jar:$JFX_DIR/javafx.media.jar:$JFX_DIR/javafx.fxml.jar"

# JavaFX is required at runtime – start with a clear message if missing
if [ ! -f "$JFX_DIR/javafx.base.jar" ]; then
  echo "ERROR: JavaFX 21+ SDK not found at $JFX_DIR" >&2
  echo "Set JFX_DIR to the lib/ folder of the Gluon JavaFX SDK." >&2
  exit 1
fi

# -----------------------------------------------------------------------
# Local TMDB-based series/movie index (fast offline cross-reference)
# -----------------------------------------------------------------------
DATA_DIR="$SCRIPT_DIR/data"
DATA_PROPS=""
if [ -f "$DATA_DIR/thetvdb.txt.xz" ]; then
  DATA_PROPS="$DATA_PROPS -Durl.thetvdb-index=file://$DATA_DIR/thetvdb.txt.xz"
fi
if [ -f "$DATA_DIR/moviedb.txt.xz" ]; then
  DATA_PROPS="$DATA_PROPS -Durl.movie-list=file://$DATA_DIR/moviedb.txt.xz"
fi

# -----------------------------------------------------------------------
# JVM properties
# -----------------------------------------------------------------------
JVM_OPTS=""

# JVM tuning (string deduplication can massively reduce heap usage for large media libraries)
JVM_OPTS="$JVM_OPTS -XX:+UseStringDeduplication"

# Avoid stale remote cache when switching to local index files
JVM_OPTS="$JVM_OPTS -Durl.refresh=PT0S"

# Use the native (platform) file chooser by default
JVM_OPTS="$JVM_OPTS -Dnet.renameo.UserFiles.fileChooser=AWT"

# JavaFX module path (required for Mac accessibility and dark mode)
JVM_OPTS="$JVM_OPTS -Djava.library.path=$JFX_DIR"

# native libraries such as libmediainfo ({vf}, {vc}, -mediainfo) come from Homebrew
NATIVE_PATH="$SCRIPT_DIR/lib/native"
for d in /opt/homebrew/lib /usr/local/lib; do
  [ -d "$d" ] && NATIVE_PATH="$NATIVE_PATH:$d"
done
JVM_OPTS="$JVM_OPTS -Djna.library.path=$NATIVE_PATH"

# User-defined JVM options (RENAMEO_OPTS is appended as-is)
JVM_OPTS="$JVM_OPTS ${RENAMEO_OPTS:-}"

# -----------------------------------------------------------------------
# Execute
# -----------------------------------------------------------------------
exec "$JAVA_HOME/bin/java" \
  ${JAVA_OPTS:-} \
  $JVM_OPTS \
  $DATA_PROPS \
  -cp "$SCRIPT_DIR/dist/ReNameo_1.0.0.jar:$JFX_CP" \
  net.renameo.Main "$@"
