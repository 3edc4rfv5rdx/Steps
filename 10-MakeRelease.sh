#!/usr/bin/env bash
#
# Builds the signed release APKs and bumps build_number.txt.
#
# The major.minor line moves by itself when CHANGELOG has an N entry waiting
# since the last tag: N is a feature, everything else is a fix, a tweak or
# plumbing, and that decision was already made when the entry was written. There
# is nothing to pass and nothing to remember.
#
set -e
cd "$(dirname "$0")"

BUILD_FILE="build_number.txt"
CHANGELOG_FILE="CHANGELOG.md"

# Whether anything waiting for release is a new feature. 20-MakeTag.sh empties
# Unreleased when it stamps a version, so this reads exactly what has landed
# since the last tag. Continuation lines are indented, so only the first line of
# an entry can match, and both dialects of the marker — "- N:" and "- N " —
# count.
unreleased_has_feature() {
    [ -f "$CHANGELOG_FILE" ] || return 1
    awk '
        /^## Unreleased$/ { inside = 1; next }
        /^## / { inside = 0 }
        inside && /^- N[: ]/ { found = 1 }
        END { exit !found }
    ' "$CHANGELOG_FILE"
}

# The line the last release went out on, so the rule fires once per feature and
# not on every build after it. Silence rather than failure when there is no tag
# yet: a project without releases is not an error, and an assignment from a
# function that returns non-zero would end the script under set -e.
released_line() {
    local tag
    # Release tags only: a tag like "duplex" sorts ahead of them and would answer
    # this question with a name that carries no version at all.
    tag=$(git tag --list 'v*' --sort=-v:refname 2>/dev/null | head -1) || true
    if [[ "$tag" =~ ^v([0-9]+\.[0-9]+)\. ]]; then
        echo "${BASH_REMATCH[1]}"
    fi
    return 0
}

next_line() {
    if [[ ! "$1" =~ ^([0-9]+)\.([0-9]+)$ ]]; then
        echo "Malformed base_version: $1" >&2
        return 1
    fi
    echo "${BASH_REMATCH[1]}.$((BASH_REMATCH[2] + 1))"
}

if [[ ! -f "$BUILD_FILE" ]]; then
    echo "base_version=0.1" > "$BUILD_FILE"
    echo "build=0" >> "$BUILD_FILE"
    echo "version=0.1.0" >> "$BUILD_FILE"
fi

source "$BUILD_FILE"
NEW_BUILD=$((build + 1))
# The day of the build. It is not part of the version any more — the version
# ends in the build number — and is kept for the About screen alone, which reads
# it through BuildConfig.
BUILD_DATE=$(date +%F)

# The line moves only when the last release went out on this same line: after it
# has moved, the tag still names the old one, so the next build leaves it alone.
RELEASED_LINE=$(released_line)
if [ -n "$RELEASED_LINE" ] && [ "$RELEASED_LINE" = "$base_version" ] && unreleased_has_feature; then
    base_version=$(next_line "$base_version")
    echo ">>> A feature is waiting in CHANGELOG, so the line moves to $base_version"
fi

NEW_VERSION="${base_version}.${NEW_BUILD}"

cat > "$BUILD_FILE" <<EOF
base_version=${base_version}
build=${NEW_BUILD}
version=${NEW_VERSION}
build_date=${BUILD_DATE}
EOF

echo "Version: $NEW_VERSION"
echo "Date:    $BUILD_DATE"
echo "Date:    $BUILD_DATE"
echo ">>> Build: $NEW_BUILD <<<"

./gradlew assembleRelease

echo
echo "Release APKs: app/build/outputs/apk/release/"
ls -1 app/build/outputs/apk/release/*.apk 2>/dev/null

# Fold the build_number bump into the previous commit, if safe.
# Safe = HEAD is not yet on any remote branch AND the only modified file is build_number.txt.
echo
if git rev-parse --verify HEAD >/dev/null 2>&1; then
    dirty=$(git status --porcelain | awk '{print $2}')
    if [[ "$dirty" == "$BUILD_FILE" ]]; then
        if [[ -z "$(git branch -r --contains HEAD 2>/dev/null)" ]]; then
            git add "$BUILD_FILE"
            git commit --amend --no-edit >/dev/null
            echo ">>> Folded $BUILD_FILE into $(git log -1 --pretty=format:'%h %s')"
        else
            echo ">>> HEAD already pushed; leaving $BUILD_FILE uncommitted."
        fi
    else
        echo ">>> Other changes present; leaving $BUILD_FILE uncommitted."
    fi
fi

sleep 2
