#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

PYTHON_BIN=""
if command -v python3 &>/dev/null; then
    PYTHON_BIN="python3"
elif command -v python &>/dev/null; then
    PYTHON_BIN="python"
fi

if [ -z "${PYTHON_BIN}" ]; then
    echo "Error: Python 3 is required to generate EdDSA cryptographic keys." >&2
    exit 1
fi

exec "${PYTHON_BIN}" "${SCRIPT_DIR}/generate_jwt_keys.py" "$@"
