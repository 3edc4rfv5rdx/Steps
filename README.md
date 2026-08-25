# Steps

A personal pedometer for Android. It reads the phone's hardware `TYPE_STEP_COUNTER`, keeps a
per-day history, and shows progress towards a daily goal.

Full behaviour and the order of work are in [SPEC.md](SPEC.md).

## How it counts

The sensor reports a cumulative number of steps since the phone booted. The app reads it
periodically, subtracts the previous reading, and adds the difference to the current day in its
database. A WorkManager job does this every 15 minutes; while the screen is open the app listens to
the sensor directly, so the number grows as you walk.

No reading is trusted beyond what time allows: at most four steps a second can be credited for the
interval between two readings. That is what keeps a misreporting sensor — or firmware that keeps its
counter across a reboot — from dumping a lifetime total onto a single day.

Two known limits follow from that:

- steps taken between the last reading and a reboot are lost — the sensor restarts at zero;
- the sensor gives no timing breakdown, so everything read after midnight is credited to the new
  day: up to 15 minutes of evening steps can land on the next one.

## Requirements

- Android 13+ (minSdk 33) and a hardware step counter
- the `ACTIVITY_RECOGNITION` permission — without it the system withholds the counter's readings

## Build

Release-only workflow. The scripts in the repository root mirror the BikeTracker toolchain:

| Script | What it does |
|---|---|
| `./10-MakeRelease.sh` | signed release with ABI splits, bumping the build number |
| `./12-SamsRELEASE.sh` | install on the connected phone |
| `./11-EmulRELEASE.sh` | install on the emulator |
| `./05-Lint.sh` | Android Lint, findings as plain text |
| `./06-Test.sh` | JVM unit tests with a per-class summary |
| `./20-MakeTag.sh`, `./21-PushTag.sh` | release tag and its push |

Release signing reads `~/.my-safe/key.properties`; the build number lives in `build_number.txt`.

The launcher icon is generated, not hand-drawn: `python3 tools/make_icon.py` cuts the pedestrian
out of the crossing sign in `ADD/images/znak.png` — dropping the zebra stripes — and writes
`mipmap-*/ic_launcher_foreground.png` at every density. The blue behind it is
`values/ic_launcher_background.xml`. The source image is git-ignored; the generated PNGs are
committed, so a normal build needs no Python.
