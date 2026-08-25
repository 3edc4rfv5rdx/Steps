# Steps

A personal pedometer for Android. It reads the phone's hardware `TYPE_STEP_COUNTER`, keeps a
per-day history, and shows progress towards a daily goal. No network, no accounts, no Play
Services.

Full behaviour is in [SPEC.md](SPEC.md).

## What it shows

**Today** — a ring over the daily goal with the count inside it, the percentage under it counting
on past 100%, and the current week as bars. The whole ring is the pause button, for a bus ride the
hardware counts through regardless.

**History** — year → month → day, with week / month / year / all-time totals on top. All periods
are calendar ones: the week starts on the locale's first day, not seven days ago.

**A day taken apart** — a tap on any bar of the week, or on any day in the history, opens that day
from midnight to midnight: bars an hour wide, half an hour, or a quarter, with a pointer that
follows the finger and names the stretch it stands on, and the day's figures under it.

Settings hold the goal, the step length the distance is figured from, the theme and accent, the
language (English, Russian, Ukrainian), and the backups. A demo mode seeds plausible history and
fakes a walking counter, offered on an emulator only.

## How it counts

The sensor reports a cumulative number of steps since the phone booted. The app reads it
periodically, subtracts the previous reading, and adds the difference to the current day in its
database. A WorkManager job does this every 15 minutes; while the screen is open the app listens to
the sensor directly, so the number grows as you walk. Nothing runs in the background while you
walk — the hardware counter accumulates on its own, so there is no foreground service.

Each reading is also spread over the quarter hours its own interval covered, which is what the day
chart is built from. The sensor gives no timing breakdown, so an even spread is the only claim
the data supports — and a reading Android delayed paints an even hour rather than a spike at the
moment it landed.

No reading is trusted beyond what time allows: at most four steps a second can be credited for the
interval between two readings. That is what keeps a misreporting sensor — or firmware that keeps
its counter across a reboot — from dumping a lifetime total onto a single day.

Known limits:

- steps taken between the last reading and a reboot are lost — the sensor restarts at zero;
- the sensor gives no timing breakdown, so everything read after midnight is credited to the new
  day: up to 15 minutes of evening steps can land on the next one;
- a phone that decides the app may not run in the background wakes it to read less often. The day
  totals survive that — the counter keeps counting — but the day loses its shape. The Background
  work row in Settings leads to the exemption.

## Data out

Documents/Steps holds both: a CSV of `date,steps,goal` and a ZIP of the whole database. Either
import asks first — the ZIP replaces everything, the CSV merges by date and keeps the fuller record
of each day — and the CSV import says afterwards what it wrote and what it cost.

## Requirements

- Android 13+ (minSdk 33) and a hardware step counter
- the `ACTIVITY_RECOGNITION` permission — without it the system withholds the counter's readings

## Build

Release-only workflow, driven by the scripts in the repository root:

| Script | What it does |
|---|---|
| `./00-MakeAll.sh` | icons, release, both installs and the OUT link in one run |
| `bash 02-MakeIcons.sh` | redraw the icons when `ADD/images/znak.png` is newer |
| `./10-MakeRelease.sh` | signed release with ABI splits, bumping the build number |
| `./12-SamsRELEASE.sh` | install on the connected phone |
| `./11-EmulRELEASE.sh` | install on the emulator |
| `./05-Lint.sh` | Android Lint, findings as plain text |
| `./06-Test.sh` | JVM unit tests with a per-class summary |
| `./19-LinkOut.sh` | hard-link the newest arm64 APK into `OUT/` |
| `./20-MakeTag.sh`, `./21-PushTag.sh` | release tag and its push |
| `./22-RelUpload.sh` | GitHub Release for the newest tag, with the arm64 and universal APKs |

Release signing reads `~/.my-safe/key.properties`; the build number lives in `build_number.txt`.

The launcher icon is generated, not hand-drawn: `python3 tools/make_icon.py` cuts the pedestrian
out of the crossing sign in `ADD/images/znak.png` — dropping the zebra stripes — and writes
`mipmap-*/ic_launcher_foreground.png` at every density. The blue behind it is
`values/ic_launcher_background.xml`. The source image is git-ignored; the generated PNGs are
committed, so a normal build needs no Python.
