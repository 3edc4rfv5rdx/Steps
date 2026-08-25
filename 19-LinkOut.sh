#!/usr/bin/env bash
#
# Put the newest arm64 APK into OUT/ as a link under its own name, and sweep
# whatever else is in that folder:
#
#   OUT/steps-<version>+<build>-release-arm64-v8a.apk
#
# One place to pick a build up from, instead of a path deep inside app/build.
# The link is a hard one: the entry here is the file itself, so copying it
# elsewhere copies a build and not a dangling path, and a clean of app/build
# leaves it whole. The name carries the version and the build number, so the
# listing says which build it is. Nothing is built here: 00-MakeAll.sh runs this
# after a build, and on its own it picks up a build that already exists.
#
# arm64 only, because that is what the phones take. The x86_64 split belongs to
# the emulator and the universal APK to the GitHub release; neither is a file to
# carry off by hand.
#
cd "$(dirname "$0")"

if [ "$1" = "-h" ] || [ "$1" = "--help" ]; then
    sed -n '2,18p' "$0"
    exit 0
fi

APK_DIR="app/build/outputs/apk/release"

MISSING=""
# An array, not a string of names: a name with a space in it would turn the
# membership test in the sweep into a match on halves of two different names.
LINKED=()

link_latest() { # link_latest <candidate files...>
    local newest
    newest=$(ls -t "$@" 2>/dev/null | head -1)
    if [ -z "$newest" ] || [ ! -f "$newest" ]; then
        echo ">>> nothing to link into OUT"
        MISSING="yes"
        return 0
    fi
    local name
    name=$(basename "$newest")
    mkdir -p OUT
    ln -f "$newest" "OUT/$name"
    LINKED+=("$name")
    echo "OUT/$name"
}

link_latest "$APK_DIR"/*arm64-v8a*.apk

# Everything else goes: the previous build's name, a copy left behind. Only
# files and links — a directory somebody made here is not ours to remove. And
# only once there is something to replace them with, so a run that found no APK
# leaves the last good one alone instead of emptying the folder.
if [ -z "$MISSING" ] && [ -d OUT ]; then
    for entry in OUT/* ; do
        [ -d "$entry" ] && continue
        [ -e "$entry" ] || [ -L "$entry" ] || continue
        name=$(basename "$entry")
        keep=""
        for linked in ${LINKED+"${LINKED[@]}"}; do
            [ "$name" = "$linked" ] && { keep=yes; break; }
        done
        [ -n "$keep" ] && continue
        rm -f "$entry"
    done
fi

if [ -n "$MISSING" ]; then
    echo ">>> no arm64 APK: OUT left as it was"
    exit 1
fi
exit 0
