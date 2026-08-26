# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A personal pedometer for Android: it reads the phone's hardware step counter, keeps a per-day
history, and shows progress towards a daily goal. Kotlin + Compose + Room, no network, no accounts,
no Play Services — pure AOSP/Jetpack. Target phones are a Samsung A36 and an M34.

**`SPEC.md` is the specification and is authoritative** — behaviour, screens, data model, counting
algorithm, and the order of work. Read the relevant part before changing behaviour, and update it in
the same commit when the design changes.

## Build / install (release-only workflow)

The user builds and installs release APKs only — never suggest a debug build or a debug install.
Scripts are shared in shape with the sibling `../BikeTracker`, `../WalkieTalkie` and `../myplayer`
projects:

- `10-MakeRelease.sh` — bumps `build_number.txt`, runs `assembleRelease`, renames the ABI splits to
  `steps-<version>+<code>-release-*.apk`.
- `11-EmulRELEASE.sh` (emulator, x86_64) / `12-SamsRELEASE.sh` (device, arm64) — install.
- `03-MakeDebug.sh` and `15-SamsDebug.sh` exist for completeness and are not part of the workflow.
- `05-Lint.sh` — Android Lint on the debug variant, findings printed as plain text.
- `06-Test.sh` — JVM unit tests with a per-class summary. One class:
  `./06-Test.sh --tests 'xx.steps.StepSyncTest'`.
- `99-CopyToAPKX.sh` — symlink the newest APK.
- `00-MakeAll.sh` — the whole run: release, both installs, the `OUT/` link. Not the icons —
  regenerating tracked files mid-build is what `02-MakeIcons.sh` exists to keep out of a build. A step that
  had no device to work on exits 3 and is reported as skipped rather than failed.
- `bash 02-MakeIcons.sh` — runs `tools/make_icon.py`, but only when `ADD/images/znak.png` is
  newer than the generated PNGs. No execute bit, so a build can never pull it in.
- `19-LinkOut.sh` — hard-links the newest arm64 APK into `OUT/` under its own name and sweeps
  the rest of that folder, so there is one path to copy a build from.
- `20-MakeTag.sh` / `21-PushTag.sh` — release tag and its push. `22-RelUpload.sh` — creates the
  GitHub Release for the newest tag out of its `CHANGELOG.md` section and uploads the arm64 and
  universal APKs to it (the x86_64 split is emulator-only and stays local).
- `tools/make_icon.py` — regenerates the launcher icon from `ADD/images/znak.png` (that whole folder is git-ignored). Its output,
  `mipmap-*/ic_launcher_foreground.png`, is committed, so the build itself never runs Python.

Conventions:

- **Never build or install an APK** — the user runs the build and install scripts, and not even a
  compile check is worth doing on their behalf.
- **Tests and lint are the exception**: `./06-Test.sh`, `./05-Lint.sh` and the emulator-only
  `connectedDebugAndroidTest` are yours to run after changing anything they cover, and to fix what
  they catch. They compile the debug variant, which is not the same as handing the user a debug APK.
  The counting logic is deliberately pure so it can be tested without a phone.
- Never hand-edit or bump `build_number.txt`; the build scripts own it. Check its status before
  every commit and stage it when it shows as modified, so the repo version never drifts out of sync
  with the built artifact.
- Toolchain: AGP 9.1.1 (built-in Kotlin, no separate `kotlin-android` plugin — AGP 9 fails if it is
  applied), Kotlin 2.3.0, KSP 2.3.6, Room 2.8.4, compileSdk 36, minSdk 33. Versions live in
  `gradle/libs.versions.toml`. Room schema export goes to `app/schemas`.
- Release signing reads `/home/e/.my-safe/key.properties`, falling back to a repo `key.properties`;
  unsigned if neither exists.
- Device safety rules are in `AGENTS.md`: tests run on an emulator, nothing touches a physical
  phone through `adb` without the user asking for that exact action.

## Architecture

Single `:app` module, package `xx.steps`.

- **`Common.kt`** — every shared constant and helper: database and prefs names, goal bounds, ISO
  date conversion, step and day formatting, the boot-time estimate. Nothing here touches Compose,
  so it stays covered by plain JVM tests.
- **`steps/StepSync.kt`** — `foldReading()`, the whole counting rule as one pure function: turn a
  cumulative `TYPE_STEP_COUNTER` reading into "steps to add to today". Reboot detection lives here
  (uptime or the counter falling below the stored value), as does the physical cap of
  `MAX_STEPS_PER_SECOND` over the elapsed interval. Covered by `StepSyncTest`; change the rule and
  the test changes with it, never the other way round.
- **`steps/StepSensor.kt`** — the only place that talks to `SensorManager`: a flow of readings for
  the open screen, a single timed read for the background worker, and the availability check for
  phones without the sensor. Its flow completes empty when there is no sensor, so collectors need
  no separate check — but `first()` throws on such a flow, which is why the timed read uses
  `firstOrNull()`.
- **`steps/StepAccess.kt`** — the one answer to "can we count right now": ready, permission
  missing, or no sensor. Refreshed by the application on start and by the activity on every start
  and permission answer; the screens and the readings both watch it, so counting restarts by itself
  the moment the permission is granted.
- **`data/`** — Room: a table of day → steps and a one-row `sync_state` holding the counter
  baseline, plus the repository that folds a reading and writes both in a single transaction. The
  baseline belongs in the database, not in preferences, precisely so that pairing is atomic. CSV and
  ZIP import/export live here too.
- **`steps/StepCounting.kt`** — the sensor registration, held for the life of the process. The
  hardware counter is not free-running: it advances while some app holds a registration on it and
  stands still otherwise, so listening only while a screen is open records only the steps taken in
  front of it. Started by `StepsApp`, never by a screen.
- **`work/StepsService.kt`** — the foreground service. Android stops delivering sensor events to
  an app whose UID has gone idle, without unregistering anything, so counting from a pocket is
  impossible without it. It does not read the sensor; it keeps the process in a state where
  `StepCounting`'s registration works, and shows today's steps and distance in its notification.
- **`work/StepsSyncWorker.kt`** — the periodic 15-minute sync, and what starts the service again
  after the system has killed the process.
- **`work/StepsBootReceiver.kt`** — the same job for the two events that stop counting silently: a
  reboot and an app update. Both leave the app installed and scheduled but not running.
- **`settings/AppSettings`** — the only other persisted state, in `SharedPreferences`: goal, theme,
  accent, and whether the counting journal is written. Language uses the framework `LocaleManager` (API 33+).
- **`ui/`** — Compose only. Three tabs: Today (progress ring plus the past week), History
  (year → month → day tree), Settings. `HistoryModel.kt` holds the grouping and the period totals
  as plain functions, free of Compose, so both are covered by JVM tests; the screen only renders
  what it returns. Distance goes through `distanceLabel()` — one place builds that string.

## Counting invariants

- The counter is cumulative since boot and resets to zero on reboot. Only a delta against the
  previous reading of the *same boot* is meaningful — never treat the raw value as "steps today".
  "Same boot" is decided by `SystemClock.elapsedRealtime()`, never by wall-clock time.
- No reading is trusted beyond what elapsed time allows: the cap of four steps a second is what
  stands between the history and a sensor (or a vendor firmware) that misreports. Any new path that
  credits steps from a raw counter goes through `foldReading()` rather than around it.
- Steps are credited to the day the reading happens on; the sensor gives no timing breakdown. The
  15-minute sync keeps the midnight error window short, and steps lost to a reboot are accepted.
- The first reading after install or a data wipe only sets the baseline and credits nothing.
- The goal is stored in each day's row: changing the goal must never rewrite whether past days were
  met.
- Pausing does not stop reading. The hardware counts through a bus ride whatever the app does, so a
  pause consumes readings and moves the baseline with `credit = false`; stopping the readings would
  only hand those steps over in one lump when the pause ended.
- Demo mode is the only source of steps that is not the sensor, and it goes through `foldReading()`
  like everything else. It wipes the database on the way in and on the way out — demo days and real
  history must never share one database.

## Working style

- **Take the wording literally.** "A button" means a button, not a list row; "along the band" means
  touching it, not near it. Where a phrase allows two readings, ask one short question before
  writing code — never build one reading and rework it afterwards.
- Respond in Russian. Do exactly what is asked — minimal diff, no adjacent refactors, no library
  swaps. If a different approach looks better, propose it in text first and wait for approval.
- One-sentence proactive observations are welcome (a data-integrity risk, a copy gap, the next
  step). Surface them; do not act on them unasked.
- Never commit unless the user says so ("запиши", "коммит", "commit"). After committing, stop —
  the user pushes. But say when a feature is finished and offer to record it: one commit per
  feature only works if the end of each one is announced rather than quietly piled onto the next.

## UI rules

- Portrait only. `screenOrientation="portrait"` plus the `PROPERTY_COMPAT_ALLOW_RESTRICTED_ORIENTATION`
  manifest property, which is what keeps the lock working on Android 16.
- Buttons get a filled background — no outlined or low-contrast "grey on grey" controls, and no
  bare `TextButton` anywhere, dialogs included. Confirm actions are `Button` in the accent colour,
  dismiss actions are `FilledTonalButton`; the dark theme overrides `secondaryContainer` so the
  tonal fill stays visible against the near-black window.
- No grey text on a grey background anywhere: body text is `onSurface`, never `onSurfaceVariant`.
  The notice circle is plain black on amber, and its text is large — it stands in for the step
  count, so it carries the count's weight.
- UI strings are as short as they can be and still be clear; punctuation is added in code, not
  carried in the string.
- Colors, sizes and weights come from `MaterialTheme` and `ui/Type.kt`; nothing is hardcoded and
  nothing renders below `MinTextSize`.

## Conventions

- **Everything is written in English** — code, identifiers, comments, every `.md` in the repository
  (`SPEC.md`, `README.md`, `CHANGELOG.md`), and commit messages. The localization files are the only
  place another language appears.
- UI strings live in `values/strings.xml` with full `values-ru/` and `values-uk/` translations
  written at the same time — never leave a locale as an English placeholder. Strings carry no
  punctuation; the code adds it.
- Every commit must update `CHANGELOG.md` in the same commit, newest entries prepended to the
  `Unreleased` block. Commit only when the user asks for it.
- Shared values and helpers belong in `Common.kt`; reuse them before writing anything inline, and
  extract early when a pattern repeats. No duplication across files.
- Persisted state has exactly two homes: `AppSettings` (preferences) and the Room database. Nothing
  reads or writes it anywhere else.
