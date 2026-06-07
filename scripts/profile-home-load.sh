#!/usr/bin/env bash
set -euo pipefail

home_file="${1:-}"
mode="${2:-recorder}"
iterations="${3:-1}"
jfr_file="${HOME_LOAD_JFR:-}"

if [[ -z "$home_file" ]]; then
  echo "Usage: $0 <home.sh3d> [recorder|direct] [iterations]" >&2
  exit 2
fi

java_options=(
  -Xms512m
  -Xmx4g
  -Djava.awt.headless=true
)
if [[ -n "$jfr_file" ]]; then
  mkdir -p "$(dirname "$jfr_file")"
  java_options+=("-XX:StartFlightRecording=filename=$jfr_file,settings=profile,dumponexit=true")
fi

exec java "${java_options[@]}" \
  -cp "build/performance-classes:build/SweetHome3D.jar:build/Furniture.jar:build/Textures.jar:build/Examples.jar:build/Help.jar:lib/*:lib/java3d-1.6/*" \
  com.eteks.sweethome3d.performance.HomeLoadBenchmark \
  "$home_file" "$mode" "$iterations"
