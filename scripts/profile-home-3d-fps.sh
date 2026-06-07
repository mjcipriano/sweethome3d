#!/usr/bin/env bash
set -euo pipefail

home_file="${1:-}"
seconds="${2:-15}"
jfr_file="${HOME_3D_FPS_JFR:-}"
java_command="${HOME_3D_JAVA:-java}"

if [[ -z "$home_file" ]]; then
  echo "Usage: $0 <home.sh3d|--smoke> [seconds]" >&2
  exit 2
fi
if [[ -z "${DISPLAY:-}" ]]; then
  echo "DISPLAY is required: this benchmark drives the on-screen 3D view." >&2
  exit 1
fi
if [[ ! -x "$java_command" ]] && ! command -v "$java_command" >/dev/null 2>&1; then
  echo "HOME_3D_JAVA must name an executable Java command: $java_command" >&2
  exit 1
fi

java_version="$("$java_command" -version 2>&1)"
printf '%s\n' "$java_version"
if grep -q "JBR-" <<<"$java_version"; then
  echo "JetBrains Runtime is unsupported for Java 3D profiling under Linux/WSL." >&2
  echo "Set HOME_3D_JAVA to an OpenJDK 17 java executable." >&2
  exit 1
fi

java_options=(
  -Xms1g
  -Xmx4g
  --add-opens=java.desktop/java.awt=ALL-UNNAMED
  --add-opens=java.desktop/sun.awt=ALL-UNNAMED
  --add-exports=java.desktop/sun.awt=ALL-UNNAMED
  -Djava.library.path=lib/linux/x64:lib/java3d-1.6/linux/amd64:lib/yafaray/linux/x64
  -Djogamp.gluegen.UseTempJarCache=false
)
if [[ -n "$jfr_file" ]]; then
  mkdir -p "$(dirname "$jfr_file")"
  java_options+=("-XX:StartFlightRecording=filename=$jfr_file,settings=profile,dumponexit=true")
fi

exec "$java_command" "${java_options[@]}" \
  -cp "build/performance-classes:build/SweetHome3D.jar:build/Furniture.jar:build/Textures.jar:build/Examples.jar:build/Help.jar:lib/*:lib/java3d-1.6/*" \
  com.eteks.sweethome3d.performance.Home3DFpsBenchmark \
  "$home_file" "$seconds"
