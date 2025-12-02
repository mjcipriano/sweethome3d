#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR_PATH="${ROOT_DIR}/install/SweetHome3D-7.5.jar"

if [ ! -f "$JAR_PATH" ]; then
  echo "Missing $JAR_PATH. Run 'make jar' first." >&2
  exit 1
fi

JAVA_OPTS=${JAVA_OPTS:-"-Xmx1024m --add-opens=java.desktop/java.awt=ALL-UNNAMED --add-opens=java.desktop/sun.awt=ALL-UNNAMED --add-opens=java.desktop/com.apple.eio=ALL-UNNAMED --add-opens=java.desktop/com.apple.eawt=ALL-UNNAMED"}
JAVA_LIB_PATH=${JAVA_LIB_PATH:-"${ROOT_DIR}/lib/linux/x64:${ROOT_DIR}/lib/java3d-1.6/linux/amd64:${ROOT_DIR}/lib/yafaray/linux/x64"}

exec java $JAVA_OPTS \
  -Djava.library.path="$JAVA_LIB_PATH" \
  -Djogamp.gluegen.UseTempJarCache=false \
  -jar "$JAR_PATH" "$@"
