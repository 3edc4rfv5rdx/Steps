#!/usr/bin/env bash
#
# Everything in one run: build the signed release, install it on the emulator and
# on the phone, publish it to the update server, and link the build into OUT/.
#
# The icons are not part of it. 02-MakeIcons.sh rewrites tracked files, which
# would leave the tree dirty in the middle of a build and stop 10-MakeRelease.sh
# from folding the version bump into the previous commit. Redrawing them is its
# own deliberate step, and its own commit:  bash 02-MakeIcons.sh
#
# A build that fails stops the run. A device that is not plugged in does not: an
# absent emulator should not cost you the APK. The difference is in the exit code
# of the step — 3 means there was nothing to run it on, anything else non-zero
# means it tried and failed — and this run ends non-zero when something actually
# failed, so it is not only the log that says so.
#
cd "$(dirname "$0")"

if [ "$1" = "-h" ] || [ "$1" = "--help" ]; then
    sed -n '2,10p' "$0"
    exit 0
fi

# Two different endings, kept apart: a step that was not there or had nothing to
# work on, and a step that ran and did not finish.
SKIPPED=""
FAILED=""

run() { # run <script> <fatal|optional>
    echo
    echo "=== $1 ==="
    if [ ! -x "$1" ]; then
        echo ">>> $1 is missing or not executable"
        [ "$2" = "fatal" ] && exit 1
        SKIPPED="$SKIPPED $1"
        return 0
    fi
    ./"$1"
    local rc=$?
    [ "$rc" = 0 ] && return 0
    if [ "$rc" = 3 ]; then
        echo ">>> $1 had nothing to work on"
        SKIPPED="$SKIPPED $1"
        return 0
    fi
    echo ">>> $1 failed"
    [ "$2" = "fatal" ] && exit 1
    FAILED="$FAILED $1"
}

run 10-MakeRelease.sh fatal
run 11-EmulRELEASE.sh optional
run 12-SamsRELEASE.sh optional
# The arm64 build published to the update server, so the app's own updater can
# offer it. Optional: a machine without the server is not a failed build.
run 18-ToUpdate.sh optional
# The APKs of this build, linked into OUT/ under their own names. Its own script,
# so the same step also works on a build that already exists.
run 19-LinkOut.sh optional

echo
if [ -n "$FAILED" ]; then
    echo "Finished, but these failed:$FAILED"
elif [ -n "$SKIPPED" ]; then
    echo "Done, without:$SKIPPED"
else
    echo "Done: APK installed on both, published, build linked into OUT/"
fi
# Only a step that tried and failed makes the run itself a failure.
[ -z "$FAILED" ] || exit 1

sleep 3
