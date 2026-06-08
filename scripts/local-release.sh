#!/usr/bin/env bash
# Local release packaging for Sweet Home 3D.
# Usage: scripts/local-release.sh <version>
# Example: scripts/local-release.sh 7.6.0-beta.2
#
# Requires: ant, jpackage (JDK 17+), gh CLI authenticated, git.
# jpackage is in the sweethome3d conda environment.

set -euo pipefail

VERSION="${1:?Usage: $0 <version>}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUTPUT="$ROOT/release"
STAGING="$OUTPUT/staging-linux-x64"
INPUT="$STAGING/input"
APP_IMAGES="$STAGING/app-images"

# Numeric core for jpackage metadata
APP_VERSION="${VERSION%%-*}"

echo "=== Building executable JAR for $VERSION ==="
cd "$ROOT"
ant "-Dversion=$VERSION" jarExecutable

JAR_NAME="SweetHome3D-$VERSION.jar"
JAR_PATH="$ROOT/install/$JAR_NAME"
if [ ! -f "$JAR_PATH" ]; then
  echo "ERROR: Expected JAR not produced: $JAR_PATH" >&2
  exit 1
fi

echo "=== Packaging with jpackage ==="
rm -rf "$STAGING"
mkdir -p "$INPUT" "$APP_IMAGES"
cp "$JAR_PATH" "$INPUT/"

jpackage \
  --type app-image \
  --name "Sweet Home 3D" \
  --dest "$APP_IMAGES" \
  --input "$INPUT" \
  --main-jar "$JAR_NAME" \
  --main-class com.eteks.sweethome3d.SweetHome3DBootstrap \
  --app-version "$APP_VERSION" \
  --vendor "Space Mushrooms" \
  --description "Interior design application" \
  --java-options "-Xmx2g" \
  --java-options "--add-opens=java.desktop/java.awt=ALL-UNNAMED" \
  --java-options "--add-opens=java.desktop/sun.awt=ALL-UNNAMED" \
  --java-options "--add-opens=java.desktop/com.apple.eio=ALL-UNNAMED" \
  --java-options "--add-opens=java.desktop/com.apple.eawt=ALL-UNNAMED" \
  --java-options "-Djogamp.gluegen.UseTempJarCache=false" \
  --java-options "-Dcom.eteks.sweethome3d.applicationId=SweetHome3D#GitHubRelease" \
  --java-options "-Dcom.eteks.sweethome3d.applicationVersion=$VERSION" \
  --icon "$ROOT/src/com/eteks/sweethome3d/swing/resources/aboutIcon.png"

APP_IMAGE="$APP_IMAGES/Sweet Home 3D"
if [ ! -d "$APP_IMAGE" ]; then
  echo "ERROR: Expected app-image not produced: $APP_IMAGE" >&2
  exit 1
fi

# Copy license files
find "$ROOT" -maxdepth 1 -name '*LICENSE*' -exec cp {} "$APP_IMAGE/" \;
cp "$ROOT/COPYING.TXT" "$APP_IMAGE/"

echo "=== Creating release archive ==="
mkdir -p "$OUTPUT"
ARCHIVE_BASE="SweetHome3D-$VERSION-linux-x64"
ARCHIVE="$OUTPUT/$ARCHIVE_BASE.tar.gz"
rm -f "$ARCHIVE"
tar -C "$APP_IMAGES" -czf "$ARCHIVE" "Sweet Home 3D"

# Copy executable JAR
cp "$JAR_PATH" "$OUTPUT/$JAR_NAME"

echo "=== Verifying artifacts ==="
# Verify JAR manifest version
MANIFEST_VERSION=$(unzip -p "$OUTPUT/$JAR_NAME" META-INF/MANIFEST.MF | grep '^Implementation-Version:' | awk '{print $2}' | tr -d '\r')
if [ "$MANIFEST_VERSION" != "$VERSION" ]; then
  echo "ERROR: JAR manifest version '$MANIFEST_VERSION' != '$VERSION'" >&2
  exit 1
fi
echo "  Manifest version: $MANIFEST_VERSION OK"

# Check archive size
ARCHIVE_SIZE=$(stat -c%s "$ARCHIVE" 2>/dev/null || stat -f%z "$ARCHIVE")
if [ "$ARCHIVE_SIZE" -lt 50000000 ]; then
  echo "WARNING: Archive is smaller than 50MB ($((ARCHIVE_SIZE / 1024 / 1024))MB)" >&2
fi
echo "  Archive: $ARCHIVE ($((ARCHIVE_SIZE / 1024 / 1024))MB)"
echo "  Executable JAR: $OUTPUT/$JAR_NAME"

echo ""
echo "=== Release artifacts ready ==="
echo "Archive: $ARCHIVE"
echo "JAR:     $OUTPUT/$JAR_NAME"
echo ""
echo "Next steps (run manually):"
echo "  git tag v$VERSION"
echo "  git push origin v$VERSION"
echo "  gh release create v$VERSION --prerelease --title \"Sweet Home 3D $VERSION\" --generate-notes"
echo "  gh release upload v$VERSION release/*"
echo ""
echo "Or to do all of the above automatically, run with --publish:"
echo "  $0 $VERSION --publish"
