#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
environment_file="$repo_root/environment.yml"

if command -v mamba >/dev/null 2>&1; then
  conda_command=mamba
elif command -v conda >/dev/null 2>&1; then
  conda_command=conda
else
  echo "Install Miniconda or Mambaforge before running this script." >&2
  exit 1
fi

if conda env list | awk '{print $1}' | grep -qx sweethome3d; then
  "$conda_command" env update -n sweethome3d -f "$environment_file"
else
  "$conda_command" env create -f "$environment_file"
fi

echo
echo "Activate the environment with:"
echo "  conda activate sweethome3d"
