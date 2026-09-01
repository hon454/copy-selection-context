#!/usr/bin/env bash
set -euo pipefail

release_tag="${1:-${GITHUB_REF_NAME:-}}"
build_file="${2:-build.gradle.kts}"

if [[ -z "$release_tag" ]]; then
  echo "::error::Release tag is required (argument 1 or GITHUB_REF_NAME)." >&2
  exit 1
fi

if [[ ! "$release_tag" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "::error::Release tag must use v<major>.<minor>.<patch>: $release_tag" >&2
  exit 1
fi

if [[ ! -f "$build_file" ]]; then
  echo "::error::Canonical Gradle build file not found: $build_file" >&2
  exit 1
fi

version_lines="$(grep -E '^[[:space:]]*version[[:space:]]*=[[:space:]]*"[^"]+"' "$build_file" || true)"
version_count="$(printf '%s\n' "$version_lines" | awk 'NF { count++ } END { print count + 0 }')"

if [[ "$version_count" -ne 1 ]]; then
  echo "::error::Expected exactly one canonical version declaration in $build_file; found $version_count." >&2
  exit 1
fi

canonical_version="$(printf '%s\n' "$version_lines" | sed -E 's/^[[:space:]]*version[[:space:]]*=[[:space:]]*"([^"]+)".*$/\1/')"
tag_version="${release_tag#v}"

if [[ "$canonical_version" != "$tag_version" ]]; then
  echo "::error::Version mismatch: tag=$tag_version, $build_file=$canonical_version" >&2
  exit 1
fi

echo "Version verified: $release_tag"
