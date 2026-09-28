#!/bin/bash
# -----------------------------------------------------------------------
# renameo.sh – ReNameo 1.0.0 launcher for macOS (sw-dark-mode)
# -----------------------------------------------------------------------
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAVA_HOME="${JAVA_HOME:-/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home}"

# -----------------------------------------------------------------------
# JVM properties
# -----------------------------------------------------------------------
JVM_OPTS=""

# JVM tuning (string deduplication can massively reduce heap usage for large media libraries)
JVM_OPTS="$JVM_OPTS -XX:+UseStringDeduplication"

# Use the native (platform) file chooser by default
JVM_OPTS="$JVM_OPTS -Dnet.renameo.UserFiles.fileChooser=AWT"

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
  -cp "$SCRIPT_DIR/dist/ReNameo_1.0.0.jar" \
  net.renameo.Main "$@"
