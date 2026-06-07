#!/usr/bin/env bash
set -euo pipefail

home_file="${1:-}"
seconds="${2:-6}"
display_name="${DISPLAY:-}"

if [[ -z "$home_file" ]]; then
  echo "Usage: $0 <home.sh3d> [fps-seconds]" >&2
  exit 2
fi
if [[ -z "$display_name" ]]; then
  echo "DISPLAY is required. Run from a VS Code WSL terminal with WSLg enabled." >&2
  exit 1
fi
if [[ ! -f "$home_file" ]]; then
  echo "Home file not found: $home_file" >&2
  exit 1
fi

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing command '$1'. Install the host packages listed in AGENTS.md." >&2
    exit 1
  fi
}

extract_glx_value() {
  local label="$1"
  awk -F': ' -v label="$label" '$1 == label {print $2; exit}'
}

require_command xdpyinfo
require_command glxinfo

if ! DISPLAY="$display_name" xdpyinfo >/dev/null 2>&1; then
  echo "DISPLAY=$display_name does not provide a usable X11 display." >&2
  exit 1
fi

glx_output="$(DISPLAY="$display_name" LIBGL_ALWAYS_SOFTWARE="${LIBGL_ALWAYS_SOFTWARE:-0}" glxinfo -B 2>&1)"
printf '%s\n' "$glx_output" |
  grep -E "direct rendering:|OpenGL vendor string:|OpenGL renderer string:|OpenGL core profile version string:|OpenGL version string:" |
  sed 's/^/  /'

direct_rendering="$(printf '%s\n' "$glx_output" | extract_glx_value "direct rendering")"
vendor="$(printf '%s\n' "$glx_output" | extract_glx_value "OpenGL vendor string")"
renderer="$(printf '%s\n' "$glx_output" | extract_glx_value "OpenGL renderer string")"

if [[ "$direct_rendering" != "Yes" && "${WSL_GPU_ALLOW_INDIRECT:-0}" != "1" ]]; then
  echo "Expected direct rendering but found '$direct_rendering'." >&2
  echo "Set WSL_GPU_ALLOW_INDIRECT=1 only when deliberately testing indirect GLX." >&2
  exit 1
fi
if grep -Eiq "llvmpipe|softpipe|software rasterizer|swrast" <<<"$renderer"; then
  echo "OpenGL is using a software renderer: $renderer" >&2
  exit 1
fi

is_wsl=0
if grep -qi "microsoft" /proc/version 2>/dev/null; then
  is_wsl=1
fi
if [[ "$is_wsl" == "1" ]]; then
  if [[ ! -e /dev/dxg && "${WSL_GPU_ALLOW_NO_DXG:-0}" != "1" ]]; then
    echo "WSL GPU device /dev/dxg is missing." >&2
    echo "Update WSL and the Windows GPU driver, or set WSL_GPU_ALLOW_NO_DXG=1 for diagnostics only." >&2
    exit 1
  fi
  if [[ "${WSL_GPU_REQUIRE_D3D12:-1}" == "1" && "$renderer" != *D3D12* ]]; then
    echo "Expected WSLg's D3D12 renderer, but GLX reported: $renderer" >&2
    echo "Set WSL_GPU_REQUIRE_D3D12=0 only for non-WSLg Linux diagnostics." >&2
    exit 1
  fi
fi

if [[ -x /usr/lib/wsl/lib/nvidia-smi ]]; then
  /usr/lib/wsl/lib/nvidia-smi \
    --query-gpu=name,driver_version,utilization.gpu,memory.used,memory.total \
    --format=csv,noheader 2>/dev/null | sed 's/^/  nvidia-smi: /' || true
fi

echo "Running Java 3D scene-update smoke test on DISPLAY=$display_name"
if [[ -n "${CONDA_ACTIVATE:-}" ]]; then
  eval "$CONDA_ACTIVATE"
fi
DISPLAY="$display_name" scripts/profile-home-3d.sh \
  "$home_file" update "${WSL_GPU_UPDATE_ITERATIONS:-3}"

echo "Running on-screen Java 3D FPS smoke test on DISPLAY=$display_name"
fps_output="$(DISPLAY="$display_name" scripts/profile-home-3d-fps.sh \
  --smoke "$seconds" 2>&1)"
printf '%s\n' "$fps_output"

java_renderer="$(printf '%s\n' "$fps_output" | awk -F= '/^gpu=/ {print $2; exit}')"
samples="$(printf '%s\n' "$fps_output" | sed -n 's/.*samples=\([0-9][0-9]*\).*/\1/p' | tail -1)"
fps_avg="$(printf '%s\n' "$fps_output" | sed -n 's/.*fps_avg=\([0-9][0-9]*\).*/\1/p' | tail -1)"

if [[ -z "$java_renderer" ]]; then
  echo "The Java 3D FPS benchmark did not report a Canvas3D renderer." >&2
  exit 1
fi
if [[ "$is_wsl" == "1" && "${WSL_GPU_REQUIRE_D3D12:-1}" == "1" && "$java_renderer" != *D3D12* ]]; then
  echo "Java 3D rendered through '$java_renderer', expected WSLg D3D12." >&2
  exit 1
fi
if [[ -z "$samples" || "$samples" -lt 1 ]]; then
  echo "The FPS benchmark did not collect any rendered-frame samples." >&2
  exit 1
fi

echo "WSL GPU smoke test passed."
echo "  GLX vendor: $vendor"
echo "  GLX renderer: $renderer"
echo "  Java 3D renderer: $java_renderer"
echo "  FPS avg: ${fps_avg:-unknown}, samples: $samples"
