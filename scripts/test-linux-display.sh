#!/usr/bin/env bash
set -euo pipefail

action="${1:-run}"
mode="${TEST_DISPLAY_MODE:-auto}"
xvfb_pid=""
xvfb_log=""

cleanup_xvfb() {
  if [[ -n "${xvfb_pid:-}" ]]; then
    kill "$xvfb_pid" 2>/dev/null || true
    wait "$xvfb_pid" 2>/dev/null || true
  fi
  if [[ -n "${xvfb_log:-}" ]]; then
    rm -f "$xvfb_log"
  fi
}

trap cleanup_xvfb EXIT

if [[ "$action" != "run" && "$action" != "check" ]]; then
  echo "Usage: $0 [run|check]" >&2
  exit 2
fi

case "$mode" in
  auto|display|xvfb) ;;
  *)
    echo "TEST_DISPLAY_MODE must be auto, display, or xvfb." >&2
    exit 2
    ;;
esac

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing command '$1'. Install the packages listed in AGENTS.md." >&2
    exit 1
  fi
}

check_display() {
  local display_name="$1"
  DISPLAY="$display_name" xdpyinfo >/dev/null 2>&1
}

check_glx() {
  local display_name="$1"
  local renderer
  renderer="$(DISPLAY="$display_name" LIBGL_ALWAYS_SOFTWARE="${LIBGL_ALWAYS_SOFTWARE:-0}" \
    glxinfo -B 2>/dev/null)"
  grep -q "OpenGL renderer string:" <<<"$renderer"
  printf '%s\n' "$renderer" |
    grep -E "direct rendering:|OpenGL vendor string:|OpenGL renderer string:|OpenGL core profile version string:" |
    sed 's/^/  /'
}

run_make_test() {
  local display_name="$1"
  local make_command="exec make test"
  local setup="${CONDA_ACTIVATE:-}"
  local java_command="${TEST_JAVA:-java}"
  if [[ -n "${TEST_JAVA:-}" ]]; then
    if [[ ! -x "$TEST_JAVA" ]]; then
      echo "TEST_JAVA must point to an executable java command: $TEST_JAVA" >&2
      exit 1
    fi
    make_command="exec make JAVA='${TEST_JAVA}' test"
  fi
  if [[ -n "${TEST_JAVA_HOME:-}" ]]; then
    if [[ ! -x "$TEST_JAVA_HOME/bin/java" || ! -x "$TEST_JAVA_HOME/bin/javac" ]]; then
      echo "TEST_JAVA_HOME must point to a complete JDK: $TEST_JAVA_HOME" >&2
      exit 1
    fi
    setup="${setup:+$setup && }export JAVA_HOME='${TEST_JAVA_HOME}' && export PATH=\"\$JAVA_HOME/bin:\$PATH\""
  fi
  if [[ -n "$setup" || -n "${TEST_JAVA:-}" ]]; then
    setup="${setup:+$setup && }java_version=\"\$('$java_command' -version 2>&1)\" && printf '%s\\n' \"\$java_version\""
    if [[ "${ALLOW_UNSUPPORTED_JBR:-0}" != "1" ]]; then
      setup="$setup && if grep -q 'JBR-' <<<\"\$java_version\"; then
        echo 'JetBrains Runtime is unsupported for Java 3D graphics tests under Linux/WSL.' >&2
        echo 'Install OpenJDK 17 and set TEST_JAVA_HOME, as documented in AGENTS.md.' >&2
        exit 1
      fi"
    fi
    local -a command=(bash -lc "$setup && $make_command")
  else
    local java_version
    java_version="$(java -version 2>&1)"
    printf '%s\n' "$java_version"
    if [[ "${ALLOW_UNSUPPORTED_JBR:-0}" != "1" ]] && grep -q "JBR-" <<<"$java_version"; then
      echo "JetBrains Runtime is unsupported for Java 3D graphics tests under Linux/WSL." >&2
      echo "Install OpenJDK 17 and set TEST_JAVA_HOME, as documented in AGENTS.md." >&2
      exit 1
    fi
    local -a command=(make test)
  fi

  echo "Running complete test suite on DISPLAY=$display_name"
  DISPLAY="$display_name" \
    LIBGL_ALWAYS_SOFTWARE="${LIBGL_ALWAYS_SOFTWARE:-0}" \
    "${command[@]}"
}

use_display() {
  local display_name="$1"
  echo "Checking X11 display $display_name"
  if ! check_display "$display_name"; then
    return 1
  fi
  echo "Checking GLX/OpenGL"
  if ! check_glx "$display_name"; then
    return 1
  fi

  if [[ "$action" == "run" ]]; then
    run_make_test "$display_name"
  else
    echo "Display and OpenGL checks passed."
  fi
}

use_xvfb() {
  require_command Xvfb

  local display_number="${TEST_XVFB_DISPLAY:-}"
  local display_found=0
  if [[ -n "$display_number" && ! "$display_number" =~ ^[0-9]+$ ]]; then
    echo "TEST_XVFB_DISPLAY must be a display number such as 99." >&2
    exit 2
  fi
  if [[ -z "$display_number" ]]; then
    for display_number in $(seq 90 120); do
      if ! check_display ":$display_number"; then
        display_found=1
        break
      fi
    done
  else
    display_found=1
  fi
  if [[ "$display_found" != "1" ]]; then
    echo "Unable to find a free X display for Xvfb." >&2
    exit 1
  fi

  local display_name=":$display_number"
  xvfb_log="$(mktemp /tmp/sweethome3d-xvfb.XXXXXX.log)"

  Xvfb "$display_name" -screen 0 1920x1080x24 -ac +extension GLX +render -noreset \
    >"$xvfb_log" 2>&1 &
  xvfb_pid=$!

  for _ in {1..50}; do
    if check_display "$display_name"; then
      break
    fi
    if ! kill -0 "$xvfb_pid" 2>/dev/null; then
      echo "Xvfb exited before accepting connections:" >&2
      cat "$xvfb_log" >&2
      exit 1
    fi
    sleep 0.1
  done

  echo "Checking X11 display $display_name"
  if ! check_display "$display_name"; then
    echo "Xvfb did not accept X11 connections." >&2
    cat "$xvfb_log" >&2
    exit 1
  fi
  echo "Checking GLX/OpenGL"
  if ! check_glx "$display_name"; then
    echo "Xvfb started, but a usable GLX configuration was not available." >&2
    echo "Prefer WSLg with TEST_DISPLAY_MODE=display or install Mesa GLX packages." >&2
    cat "$xvfb_log" >&2
    exit 1
  fi
  if [[ "$action" == "run" ]]; then
    run_make_test "$display_name"
  else
    echo "Display and OpenGL checks passed."
  fi
}

require_command xdpyinfo
require_command glxinfo

if [[ "$mode" == "display" ]]; then
  if [[ -z "${DISPLAY:-}" ]]; then
    echo "DISPLAY is unset." >&2
    exit 1
  fi
  if ! check_display "$DISPLAY"; then
    echo "DISPLAY is unset or does not provide X11 with GLX." >&2
    exit 1
  fi
  if ! check_glx "$DISPLAY" >/dev/null; then
    echo "DISPLAY=$DISPLAY does not provide usable GLX/OpenGL." >&2
    exit 1
  fi
  use_display "$DISPLAY"
elif [[ "$mode" == "xvfb" ]]; then
  use_xvfb
elif [[ -n "${DISPLAY:-}" ]] && check_display "$DISPLAY" && check_glx "$DISPLAY" >/dev/null; then
  use_display "$DISPLAY"
else
  echo "The current display is unavailable; trying Xvfb."
  use_xvfb
fi
