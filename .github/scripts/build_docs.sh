#!/usr/bin/env bash
set -euo pipefail

out="$(cd "$(dirname "${1:?usage: build_docs.sh OUT_DIR}")" && pwd)/$(basename "$1")"
repo="$(git rev-parse --show-toplevel)"
scratch="$(mktemp -d)"
trap 'rm -rf "$scratch"' EXIT

tag="$(git -C "$repo" tag -l '[0-9]*.[0-9]*.[0-9]*' --sort=-v:refname | head -1)"

guides() {
  local from="$1" target="$2" version="$3" dir date go_version
  dir="$(mktemp -d "$scratch/site.XXXX")"
  cp -R "$from/docs" "$from/zensical.toml" "$from/CHANGELOG.md" "$dir/"
  mkdir -p "$dir/docs/brand"
  cp "$from"/brand/*.svg "$dir/docs/brand/"
  date="$(head -1 "$from/data/catalog.tsv" | cut -f3)"
  go_version="$(printf 'v1.%04d%02d%02d.0' ${version//./ })"
  find "$dir/docs" -name '*.md' -exec sed -i.bak -e "s/{{ version }}/$version/g" -e "s/{{ date }}/$date/g" \
    -e "s/{{ go_version }}/$go_version/g" {} +
  find "$dir/docs" -name '*.bak' -delete
  (cd "$dir" && zensical build --clean --strict)
  mkdir -p "$target"
  cp -R "$dir/site/." "$target/"
}

api() {
  local from="$1" target="$2"
  (cd "$from" && ./gradlew :polyglossa:dokkaGenerate --stacktrace)
  mkdir -p "$target/api"
  cp -R "$from/kotlin/build/dokka/html/." "$target/api/"
}

rm -rf "$out"
guides "$repo" "$out/dev" "${tag:-0.0.0}"
api "$repo" "$out/dev"

if [[ -n "$tag" ]] && git -C "$repo" cat-file -e "$tag:zensical.toml" 2>/dev/null; then
  release="$scratch/release"
  git -C "$repo" worktree add --detach "$release" "$tag"
  guides "$release" "$out" "$tag"
  api "$release" "$out"
  git -C "$repo" worktree remove --force "$release"
  echo "The site's root documents $tag, and /dev/ documents $(git -C "$repo" rev-parse --short HEAD)"
else
  cp -R "$out/dev/." "$out/"
  echo "No release has documentation yet, so the site's root documents $(git -C "$repo" rev-parse --short HEAD) too"
fi
