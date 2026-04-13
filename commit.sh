#!/usr/bin/env bash
set -euo pipefail

if [ $# -eq 0 ]; then
  echo "Usage: ./commit.sh \"commit message\"" >&2
  exit 1
fi

msg="$1"

if [[ "$msg" == *$'\n'* ]]; then
  echo "Error: commit message must be a single line" >&2
  exit 1
fi

if [ ${#msg} -gt 120 ]; then
  echo "Error: commit message too long (${#msg} chars, max 120)" >&2
  exit 1
fi

git add -A
git commit -m "$msg"
