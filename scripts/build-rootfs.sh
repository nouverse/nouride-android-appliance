#!/usr/bin/env bash
# Downloads the Debian arm64 rootfs pinned in scripts/assets.lock from Docker Hub and puts it
# where Gradle packages it:
#
#   app/src/main/assets/rootfs.tar.gz   the image's single layer, byte for byte
#
# The manifest is fetched by digest and must list the pinned layer; the layer must hash to its
# digest. The app does what proot-distro does after extracting (resolv.conf, hosts, Android ids).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=assets.lock
. "$ROOT/scripts/assets.lock"

REGISTRY=https://registry-1.docker.io/v2/library/debian
WORK="$ROOT/build/rootfs"
OUT="$ROOT/app/src/main/assets/rootfs.tar.gz"
mkdir -p "$WORK" "$(dirname "$OUT")"

token=$(curl -fsS --retry 3 -m 60 \
  "https://auth.docker.io/token?service=registry.docker.io&scope=repository:library/debian:pull" \
  | python3 -I -c 'import json, sys; print(json.load(sys.stdin)["token"])')

curl -fsS --retry 3 -m 120 -H "Authorization: Bearer $token" \
  -H "Accept: application/vnd.oci.image.manifest.v1+json" \
  -o "$WORK/manifest.json" "$REGISTRY/manifests/$DEBIAN_ARM64_MANIFEST_DIGEST"
echo "${DEBIAN_ARM64_MANIFEST_DIGEST#sha256:}  $WORK/manifest.json" | sha256sum -c --quiet -
grep -q "\"$DEBIAN_LAYER_DIGEST\"" "$WORK/manifest.json" \
  || { echo "manifest does not list $DEBIAN_LAYER_DIGEST" >&2; exit 1; }

layer="$WORK/${DEBIAN_LAYER_DIGEST#sha256:}.tar.gz"
if [ ! -f "$layer" ]; then
  curl -fsSL --retry 5 --retry-all-errors --connect-timeout 30 -H "Authorization: Bearer $token" -o "$layer.part" "$REGISTRY/blobs/$DEBIAN_LAYER_DIGEST"
  mv "$layer.part" "$layer"
fi
echo "${DEBIAN_LAYER_DIGEST#sha256:}  $layer" | sha256sum -c --quiet -
cp "$layer" "$OUT"
ls -l "$OUT"
