#!/bin/sh

# Emulator is x86_64 — pick that split, fall back to universal, then anything
apk=$(ls -t app/build/outputs/apk/release/*-x86_64.apk 2>/dev/null | head -1)
[ -z "$apk" ] && apk=$(ls -t app/build/outputs/apk/release/*-universal.apk 2>/dev/null | head -1)
[ -z "$apk" ] && apk=$(ls -t app/build/outputs/apk/release/*.apk 2>/dev/null | head -1)

if [ -z "$apk" ]; then
    echo "No release APK found"
    exit 1
fi

# Nothing to install on is not a failure. 3, not 1: 00-MakeAll.sh reads that as
# "no emulator was running" and finishes green, while an install that was tried
# and went wrong keeps its own non-zero code.
if ! adb devices | grep -q '^emulator-5554[[:space:]]*device$'; then
    echo "No emulator at emulator-5554. Start one, or install by hand."
    exit 3
fi

echo ">>> Installing: $(basename "$apk")"
adb -s emulator-5554 install -r "$apk"

sleep 2