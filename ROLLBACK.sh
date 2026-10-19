#!/usr/bin/env bash
set -eu
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
TARGET="${1:?Usage: ROLLBACK.sh target-project-copy}"
exec "${PYTHON:-python}" "$SCRIPT_DIR/delivery/restore_source.py" "$TARGET"
