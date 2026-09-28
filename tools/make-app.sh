#!/bin/bash
# make-app.sh – builds a self-contained macOS ARM .app bundle for ReNameo
# Usage: tools/make-app.sh   (after ant fatjar)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$SCRIPT_DIR"

APP_NAME="ReNameo"
APP_VERSION="1.0.0"
APP_BUNDLE="dist/${APP_NAME}.app"
ICON="packaging/macos/AppIcon.png"

JFX_LIB="${JFX_LIB:-$SCRIPT_DIR/lib/javafx/javafx-sdk-21.0.6/lib}"
JAR="dist/${APP_NAME}_${APP_VERSION}.jar"
DATA_DIR="data"

JFX_JARS="javafx.base.jar javafx.graphics.jar javafx.controls.jar javafx.swing.jar javafx.web.jar javafx.media.jar javafx.fxml.jar"

# check every input before touching the existing bundle
if [ ! -f "$JFX_LIB/javafx.base.jar" ]; then
  echo "ERROR: JavaFX SDK not found at $JFX_LIB" >&2
  echo "Download https://download2.gluonhq.com/openjfx/21.0.6/openjfx-21.0.6_osx-aarch64_bin-sdk.zip into lib/javafx/ or set JFX_LIB." >&2
  exit 1
fi
if [ ! -f "$JAR" ]; then
  echo "ERROR: $JAR not found, run 'ant fatjar' first" >&2
  exit 1
fi

if [ ! -f "$ICON" ]; then
  echo "ERROR: $ICON not found" >&2
  exit 1
fi

echo "== clean old bundle =="
rm -rf "$APP_BUNDLE"

mkdir -p "$APP_BUNDLE/Contents/MacOS"
mkdir -p "$APP_BUNDLE/Contents/Resources"
mkdir -p "$APP_BUNDLE/Contents/Java/javafx"
mkdir -p "$APP_BUNDLE/Contents/Java/data"

echo "== bundle jar + javafx =="
cp "$JAR" "$APP_BUNDLE/Contents/Java/${APP_NAME}.jar"
for j in $JFX_JARS; do
  cp "$JFX_LIB/$j" "$APP_BUNDLE/Contents/Java/javafx/"
done
# JavaFX native libraries (required for the QuantumRenderer/prism pipeline)
cp "$JFX_LIB"/*.dylib "$APP_BUNDLE/Contents/Java/javafx/" 2>/dev/null || true
cp "$JFX_LIB/javafx.properties" "$APP_BUNDLE/Contents/Java/javafx/" 2>/dev/null || true

echo "== bundle local index data =="
if [ -d "$DATA_DIR" ]; then
  cp "$DATA_DIR"/* "$APP_BUNDLE/Contents/Java/data/" 2>/dev/null || true
fi

echo "== build .icns =="
ICONSET="dist/AppIcon.iconset"
rm -rf "$ICONSET"
mkdir -p "$ICONSET"
sips -z 16 16   "$ICON" --out "$ICONSET/icon_16x16.png"   >/dev/null
sips -z 32 32   "$ICON" --out "$ICONSET/icon_16x16@2x.png" >/dev/null
sips -z 32 32   "$ICON" --out "$ICONSET/icon_32x32.png"   >/dev/null
sips -z 64 64   "$ICON" --out "$ICONSET/icon_32x32@2x.png" >/dev/null
sips -z 128 128 "$ICON" --out "$ICONSET/icon_128x128.png"   >/dev/null
sips -z 256 256 "$ICON" --out "$ICONSET/icon_128x128@2x.png" >/dev/null
sips -z 256 256 "$ICON" --out "$ICONSET/icon_256x256.png"   >/dev/null
sips -z 512 512 "$ICON" --out "$ICONSET/icon_256x256@2x.png" >/dev/null
sips -z 512 512 "$ICON" --out "$ICONSET/icon_512x512.png"   >/dev/null
sips -z 1024 1024 "$ICON" --out "$ICONSET/icon_512x512@2x.png" >/dev/null
iconutil -c icns "$ICONSET" -o "$APP_BUNDLE/Contents/Resources/AppIcon.icns"
rm -rf "$ICONSET"

echo "== Info.plist =="
cat > "$APP_BUNDLE/Contents/Info.plist" <<'PLIST'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
	<key>CFBundleDevelopmentRegion</key>
	<string>en</string>
	<key>CFBundleExecutable</key>
	<string>ReNameo</string>
	<key>CFBundleIconFile</key>
	<string>AppIcon</string>
	<key>CFBundleIconName</key>
	<string>AppIcon</string>
	<key>CFBundleIdentifier</key>
	<string>net.renameo.app</string>
	<key>CFBundleInfoDictionaryVersion</key>
	<string>6.0</string>
	<key>CFBundleName</key>
	<string>ReNameo</string>
	<key>CFBundleDisplayName</key>
	<string>ReNameo</string>
	<key>CFBundlePackageType</key>
	<string>APPL</string>
	<key>CFBundleShortVersionString</key>
	<string>1.0.0</string>
	<key>CFBundleVersion</key>
	<string>1.0.0</string>
	<key>LSApplicationCategoryType</key>
	<string>public.app-category.productivity</string>
	<key>LSMinimumSystemVersion</key>
	<string>11.0</string>
	<key>NSHighResolutionCapable</key>
	<true/>
	<key>NSPrincipalClass</key>
	<string>NSApplication</string>
</dict>
</plist>
PLIST

echo "== launcher script =="
cat > "$APP_BUNDLE/Contents/MacOS/ReNameo" <<'LAUNCHER'
#!/bin/bash
# ReNameo.app – macOS ARM launcher (self-contained)
set -euo pipefail

# resolve symlinks so the launcher also works as a `renameo` command in the PATH
SELF="${BASH_SOURCE[0]}"
while [ -L "$SELF" ]; do
  LINK="$(readlink "$SELF")"
  case "$LINK" in /*) SELF="$LINK" ;; *) SELF="$(dirname "$SELF")/$LINK" ;; esac
done
CONTENTS="$(cd "$(dirname "$SELF")/.." && pwd)"

JAVA_BIN="${RENAMEO_JAVA:-}"
if [ -z "$JAVA_BIN" ]; then
  if /usr/libexec/java_home >/dev/null 2>&1; then
    JAVA_BIN="$(/usr/libexec/java_home)/bin/java"
  else
    JAVA_BIN="/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home/bin/java"
  fi
fi

JFX="$CONTENTS/Java/javafx"
JFX_CP="$JFX/javafx.base.jar:$JFX/javafx.graphics.jar:$JFX/javafx.controls.jar:$JFX/javafx.swing.jar:$JFX/javafx.web.jar:$JFX/javafx.media.jar:$JFX/javafx.fxml.jar"

# native libraries such as libmediainfo ({vf}, {vc}, -mediainfo) come from Homebrew
NATIVE_PATH="$CONTENTS/Java/native"
for d in /opt/homebrew/lib /usr/local/lib; do
  [ -d "$d" ] && NATIVE_PATH="$NATIVE_PATH:$d"
done

DATA="$CONTENTS/Java/data"
DATA_PROPS=""
if [ -f "$DATA/thetvdb.txt.xz" ]; then
  DATA_PROPS="$DATA_PROPS -Durl.thetvdb-index=file://$DATA/thetvdb.txt.xz"
fi
if [ -f "$DATA/moviedb.txt.xz" ]; then
  DATA_PROPS="$DATA_PROPS -Durl.movie-list=file://$DATA/moviedb.txt.xz"
fi

# Finder starts apps in /, but on the command line relative paths must keep working
if [ $# -eq 0 ]; then
  cd "$HOME"
fi
exec "$JAVA_BIN" \
  ${JAVA_OPTS:-} \
  -XX:+UseStringDeduplication \
  -Durl.refresh=PT0S \
  -Dnet.renameo.UserFiles.fileChooser=AWT \
  -Djava.library.path="$JFX" \
  -Djna.library.path="$NATIVE_PATH" \
  $DATA_PROPS \
  -cp "$CONTENTS/Java/ReNameo.jar:$JFX_CP" \
  net.renameo.Main "$@"
LAUNCHER
chmod +x "$APP_BUNDLE/Contents/MacOS/ReNameo"

echo "== ad-hoc codesign =="
codesign --force --deep --sign - "$APP_BUNDLE"

echo "== zip =="
rm -f "dist/${APP_NAME}-mac-arm64.zip"
ditto -c -k --keepParent "$APP_BUNDLE" "dist/${APP_NAME}-mac-arm64.zip"

echo "done: $APP_BUNDLE"