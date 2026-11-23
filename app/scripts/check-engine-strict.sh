#!/usr/bin/env bash
set -euo pipefail

app_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
source_dir="$app_dir/src/main/java/com/github/sbadolankaur/rpg/engine"
classes_dir="$(mktemp -d)"
trap 'rm -rf "$classes_dir"' EXIT

mapfile -d '' sources < <(find "$source_dir" -type f -name '*.java' -print0)
if ((${#sources[@]} == 0)); then
  echo "No engine Java sources found." >&2
  exit 1
fi

javac -Xlint:all -Werror -d "$classes_dir" "${sources[@]}"
