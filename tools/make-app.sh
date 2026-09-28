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

JAR="dist/${APP_NAME}_${APP_VERSION}.jar"

# Java runtime bundled into the app, so it runs on Macs without Java installed
JDK="${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || true)}"
# jdeps modules plus the ones only loaded at runtime (subtitle charsets, locale data, TLS, accessibility, zip archives)
JAVA_MODULES="java.base,java.compiler,java.desktop,java.instrument,java.logging,java.management,java.management.rmi,java.naming,java.prefs,java.scripting,java.sql,java.xml,jdk.accessibility,jdk.charsets,jdk.crypto.ec,jdk.localedata,jdk.unsupported,jdk.zipfs"

# check every input before touching the existing bundle
if [ ! -f "$JAR" ]; then
  echo "ERROR: $JAR not found, run 'ant fatjar' first" >&2
  exit 1
fi

if [ ! -x "$JDK/bin/jlink" ]; then
  echo "ERROR: JDK 21 with jlink not found, set JAVA_HOME" >&2
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
mkdir -p "$APP_BUNDLE/Contents/Java"

echo "== bundle java runtime (jlink) =="
# locale data only for the most common languages (subtitle language names come from the app itself)
"$JDK/bin/jlink" --add-modules "$JAVA_MODULES" --include-locales=en,it,de,fr,es,pt,nl --strip-debug --no-header-files --no-man-pages --compress=zip-9 --output "$APP_BUNDLE/Contents/runtime"

echo "== bundle jar =="
cp "$JAR" "$APP_BUNDLE/Contents/Java/${APP_NAME}.jar"

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
if [ -z "$JAVA_BIN" ] && [ -x "$CONTENTS/runtime/bin/java" ]; then
  JAVA_BIN="$CONTENTS/runtime/bin/java"
fi
if [ -z "$JAVA_BIN" ]; then
  if /usr/libexec/java_home >/dev/null 2>&1; then
    JAVA_BIN="$(/usr/libexec/java_home)/bin/java"
  else
    JAVA_BIN="/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home/bin/java"
  fi
fi


# native libraries such as libmediainfo ({vf}, {vc}, -mediainfo) come from Homebrew
NATIVE_PATH="$CONTENTS/Java/native"
for d in /opt/homebrew/lib /usr/local/lib; do
  [ -d "$d" ] && NATIVE_PATH="$NATIVE_PATH:$d"
done

# Finder starts apps in /, but on the command line relative paths must keep working
if [ $# -eq 0 ]; then
  cd "$HOME"
fi
exec "$JAVA_BIN" \
  ${JAVA_OPTS:-} \
  -XX:+UseStringDeduplication \
  -Dnet.renameo.UserFiles.fileChooser=AWT \
  -Djna.library.path="$NATIVE_PATH" \
  -cp "$CONTENTS/Java/ReNameo.jar" \
  net.renameo.Main "$@"
LAUNCHER
chmod +x "$APP_BUNDLE/Contents/MacOS/ReNameo"

echo "== ad-hoc codesign =="
codesign --force --deep --sign - "$APP_BUNDLE"

echo "== zip + dmg =="
rm -f "dist/${APP_NAME}-mac-arm64.zip" "dist/${APP_NAME}-mac-arm64.dmg"
ditto -c -k --keepParent "$APP_BUNDLE" "dist/${APP_NAME}-mac-arm64.zip"
DMG_DIR="dist/dmg"
rm -rf "$DMG_DIR" && mkdir -p "$DMG_DIR"
cp -R "$APP_BUNDLE" "$DMG_DIR/"
ln -s /Applications "$DMG_DIR/Applications"
hdiutil create -quiet -volname "$APP_NAME" -srcfolder "$DMG_DIR" -ov -format UDZO "dist/${APP_NAME}-mac-arm64.dmg"
rm -rf "$DMG_DIR"

echo "done: $APP_BUNDLE"