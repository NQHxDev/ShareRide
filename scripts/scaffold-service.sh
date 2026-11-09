#!/usr/bin/env bash
set -e

# ShareRide Microservice Scaffolder Wrapper

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PYTHON_BIN="$(which python3 || which python)"

if [ -z "$PYTHON_BIN" ]; then
    echo "Error: python3 is required to run the scaffolding script."
    exit 1
fi

"$PYTHON_BIN" "$SCRIPT_DIR/scaffold-service.py" "$@"

