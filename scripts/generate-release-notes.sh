#!/usr/bin/env bash
set -euo pipefail

release_version="${1:-}"
release_tag="${2:-}"
output_file="${3:-release-notes.md}"
gradle_wrapper="${GRADLE_WRAPPER:-./gradlew}"

if [[ -z "$release_version" || -z "$release_tag" ]]; then
  echo "::error::Release version and tag are required." >&2
  exit 1
fi

output_directory="$(dirname "$output_file")"
if [[ ! -d "$output_directory" ]]; then
  echo "::error::Release notes output directory not found: $output_directory" >&2
  exit 1
fi

temporary_notes="$(mktemp "$output_directory/.release-notes.XXXXXX")"
trap 'rm -f "$temporary_notes"' EXIT

# Warm the wrapper before redirecting task output. On a cold cache, the wrapper
# writes distribution download progress to stdout before Gradle starts.
"$gradle_wrapper" --version --console=plain

"$gradle_wrapper" getChangelog \
  --project-version "$release_version" \
  --console=plain \
  -q \
  --no-header \
  --no-links \
  --no-summary > "$temporary_notes"

if [[ ! -s "$temporary_notes" ]]; then
  printf 'Release %s\n' "$release_tag" > "$temporary_notes"
fi

mv "$temporary_notes" "$output_file"
trap - EXIT
