#!/usr/bin/env bash
set -euo pipefail

plugin_zip="${1:-}"
checksum_file="${2:-SHA256SUMS}"

if [[ -z "$plugin_zip" || ! -f "$plugin_zip" ]]; then
  echo "::error::Plugin ZIP not found: ${plugin_zip:-<missing>}" >&2
  exit 1
fi

if [[ "$plugin_zip" != *.zip ]]; then
  echo "::error::Release artifact must be a ZIP file: $plugin_zip" >&2
  exit 1
fi

checksum_directory="$(dirname "$checksum_file")"
if [[ ! -d "$checksum_directory" ]]; then
  echo "::error::Checksum output directory not found: $checksum_directory" >&2
  exit 1
fi

plugin_directory="$(cd "$(dirname "$plugin_zip")" && pwd -P)"
plugin_name="$(basename "$plugin_zip")"
checksum_directory="$(cd "$checksum_directory" && pwd -P)"
checksum_file="$checksum_directory/$(basename "$checksum_file")"
temporary_checksum="$(mktemp "$checksum_directory/.SHA256SUMS.XXXXXX")"
trap 'rm -f "$temporary_checksum"' EXIT

(
  cd "$plugin_directory"
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum -- "$plugin_name" > "$temporary_checksum"
    sha256sum --check --strict "$temporary_checksum"
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 -- "$plugin_name" > "$temporary_checksum"
    shasum -a 256 --check "$temporary_checksum"
  else
    echo "::error::Neither sha256sum nor shasum is available" >&2
    exit 1
  fi
)

mv "$temporary_checksum" "$checksum_file"
trap - EXIT
echo "Checksum written and verified: $checksum_file"
