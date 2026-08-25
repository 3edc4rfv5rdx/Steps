#!/usr/bin/env bash
#
# Bring the launcher and tab icons up to date with the drawing they are cut from:
#
#   ADD/images/znak.png  ->  res/mipmap-*/ic_launcher_foreground.png
#                            res/drawable-*/ic_walker.png
#
# tools/make_icon.py does the cutting; this only decides whether it has to run.
# The two sides are compared by modification time, and the generator runs only
# when something generated is older than the drawing — or missing.
#
# ADD/ is git-ignored, so a fresh clone has no drawing at all. That is not a
# failure: the generated PNGs are committed, and without the master there is
# simply nothing to redo.
#
# Its own step, and deliberately not part of a build. A build that regenerated
# icons would rewrite tracked files behind the build's back: the APK would carry
# icons no commit recorded, and 10-MakeRelease.sh would stop folding the version
# bump into the previous commit because the tree was dirty for a reason nobody
# asked for. 00-MakeAll.sh runs this before it builds, so a full run cannot go
# out with yesterday's icon.
#
# What it rewrites is listed at the end; those files belong in a commit of their
# own. No execute bit, on purpose:  bash 02-MakeIcons.sh
#
set -e
cd "$(dirname "$0")"

MASTER="ADD/images/znak.png"
GENERATED_ROOT="app/src/main/res"

if [ "$1" = "-h" ] || [ "$1" = "--help" ]; then
    sed -n '2,24p' "$0"
    exit 0
fi

if [ ! -e "$MASTER" ]; then
    echo "No $MASTER here; the committed icons stay as they are."
    exit 0
fi

# Whether anything generated is older than the drawing, or simply not there.
#
# By modification time, which is the only thing to compare without doing the work
# anyway. Git does not preserve mtimes, so a fresh clone can answer yes when the
# content is already right; that costs one regeneration which changes nothing, and
# the listing at the end says so.
needs_rebuild() {
    local generated file
    generated=$(find "$GENERATED_ROOT" \
        \( -name 'ic_launcher_foreground.png' -o -name 'ic_walker.png' \) \
        -type f 2>/dev/null || true)
    if [ -z "$generated" ]; then
        echo "No generated icons under $GENERATED_ROOT"
        return 0
    fi

    while IFS= read -r file; do
        [ -n "$file" ] || continue
        if [ "$MASTER" -nt "$file" ]; then
            echo "Older than $MASTER: $file"
            return 0
        fi
    done <<STALE
$generated
STALE
    return 1
}

if ! needs_rebuild; then
    echo "The icons are newer than $MASTER; nothing to do."
    exit 0
fi

echo "=== Cutting the icons out of $MASTER ==="
python3 tools/make_icon.py

# What this run rewrote, so it can be committed on purpose rather than swept into
# the next commit by accident. Only inside a git work tree: the script has to keep
# working in a copy that is not one.
if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    CHANGED=$(git status --porcelain -- "$GENERATED_ROOT")
    echo
    if [ -z "$CHANGED" ]; then
        echo "Nothing changed: the icons already matched the drawing."
    else
        echo "Rewritten — commit these on their own:"
        printf '%s\n' "$CHANGED"
    fi
fi
