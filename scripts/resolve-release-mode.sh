#!/usr/bin/env bash
set -euo pipefail

output_file="${1:-}"
certificate_chain="${CERTIFICATE_CHAIN:-}"
private_key="${PRIVATE_KEY:-}"
publish_token="${PUBLISH_TOKEN:-}"

if [[ -z "$output_file" ]]; then
  echo "::error::GitHub output file path is required" >&2
  exit 1
fi

if [[ -n "$certificate_chain" && -n "$private_key" ]]; then
  signed="true"
elif [[ -z "$certificate_chain" && -z "$private_key" ]]; then
  signed="false"
else
  echo "::error::Signing configuration is incomplete; CERTIFICATE_CHAIN and PRIVATE_KEY must both be set or both be empty" >&2
  exit 1
fi

if [[ "$signed" == "true" && -n "$publish_token" ]]; then
  publish="true"
  echo "::notice::Canonical release mode: signed; Marketplace publication is enabled"
elif [[ "$signed" == "true" ]]; then
  publish="false"
  echo "::notice::Canonical release mode: signed; Marketplace publication is skipped because PUBLISH_TOKEN is not configured"
else
  publish="false"
  echo "::notice::Canonical release mode: unsigned; signing credentials are not configured and Marketplace publication is skipped"
fi

printf 'signed=%s\npublish=%s\n' "$signed" "$publish" >> "$output_file"
