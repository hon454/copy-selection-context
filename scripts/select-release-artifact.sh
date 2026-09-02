#!/usr/bin/env bash
set -euo pipefail

distribution_directory="${1:-}"
signed_mode="${2:-}"
output_file="${3:-}"

if [[ ! -d "$distribution_directory" ]]; then
  echo "::error::Plugin distribution directory not found: ${distribution_directory:-<missing>}" >&2
  exit 1
fi

if [[ "$signed_mode" != "true" && "$signed_mode" != "false" ]]; then
  echo "::error::Signed mode must be 'true' or 'false': ${signed_mode:-<missing>}" >&2
  exit 1
fi

if [[ -z "$output_file" ]]; then
  echo "::error::GitHub output file path is required" >&2
  exit 1
fi

unsigned_zips=()
while IFS= read -r plugin_zip; do
  unsigned_zips+=("$plugin_zip")
done < <(find "$distribution_directory" -maxdepth 1 -type f -name '*.zip' ! -name '*-signed.zip' -print | sort)

signed_zips=()
while IFS= read -r plugin_zip; do
  signed_zips+=("$plugin_zip")
done < <(find "$distribution_directory" -maxdepth 1 -type f -name '*-signed.zip' -print | sort)

if [[ "${#unsigned_zips[@]}" -ne 1 ]]; then
  echo "::error::Expected exactly one unsigned plugin ZIP, found ${#unsigned_zips[@]}" >&2
  exit 1
fi

if [[ "$signed_mode" == "true" ]]; then
  if [[ "${#signed_zips[@]}" -ne 1 ]]; then
    echo "::error::Expected exactly one signed plugin ZIP, found ${#signed_zips[@]}" >&2
    exit 1
  fi
  canonical_zip="${signed_zips[0]}"
  echo "::notice::Selected signed canonical release ZIP: $canonical_zip"
else
  if [[ "${#signed_zips[@]}" -ne 0 ]]; then
    echo "::error::Unsigned release mode must not contain signed plugin ZIPs" >&2
    exit 1
  fi
  canonical_zip="${unsigned_zips[0]}"
  echo "::notice::Selected unsigned canonical release ZIP: $canonical_zip"
fi

printf 'path=%s\nsigned=%s\n' "$canonical_zip" "$signed_mode" >> "$output_file"
