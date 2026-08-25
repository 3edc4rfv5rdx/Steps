# Steps — an Android pedometer

## Context

A personal project in `/home/e/PRJ/Steps`. The app counts steps with the hardware sensor, keeps a
per-day history, and shows progress towards a daily goal. The target phones are a Samsung A36
(Snapdragon 6 Gen 3) and an M34; both have `TYPE_STEP_COUNTER`. The build scaffolding is copied from
the sibling project `/home/e/PRJ/BikeTracker`, and its patterns are reused: `AppSettings`, the
history tree, MediaStore export, the theme.

## Decisions

| Question | Decision |
|---|---|
| Step source | hardware `TYPE_STEP_COUNTER` |
| Storage | Room: one table of day → steps, one of day → quarter hour → steps |
| Background | no foreground service: a WorkManager job every 15 minutes, plus live reading while the screen is open |
| Screens | Today / History / Settings (three tabs) |
| Metrics | steps, the goal, and distance from a step length set in Settings — no calories |
| Widget | none |
| Data out | CSV export/import plus ZIP export/import of the database file |
| Color | a palette of 6–8 accents in Settings |
| Goal | stored in each day's row, so changing it never rewrites the past |

## Counting algorithm

`foldReading(previous: SyncState?, rawCount: Long, uptimeMillis: Long): SyncOutcome`

- `SyncState(lastRaw, lastUptimeMillis)` — the position in the cumulative counter's stream, and the
  uptime at which it was read. Uptime is `SystemClock.elapsedRealtime()`: monotonic, sleep included,
  and reset to zero by a reboot.
- The delta is `rawCount - lastRaw`; after a reboot it is the whole `rawCount`, because the sensor
  restarts at zero.
- A reboot is detected two ways: uptime below the stored one, or `rawCount` below the stored one.
  Wall-clock time is not used — it jumps on clock corrections and would need an empirical tolerance.
- The credited number is capped at `MAX_STEPS_PER_SECOND` (4) over the interval since the previous
  reading, plus `STEP_WINDOW_SLACK_MS` (60 s) of grace for sensor batching. Without the cap, vendor
  firmware that keeps the counter across a reboot would dump its lifetime total onto one day; with
  it, any sensor anomaly is bounded by elapsed time. The slack matters: batched events can arrive
  milliseconds apart carrying real steps, and a cap rounding to zero would lose them for good.
- The first reading (install, cleared data) only sets the baseline and credits nothing.
- The whole delta is credited to the day the reading happens on. The sensor gives no timing
  breakdown; syncing every 15 minutes keeps the midnight error window short.

### The intra-day breakdown

Every reading also writes where in the day its steps fell, in slots of `SLOT_MINUTES` (15 minutes,
`SLOTS_PER_DAY` = 96 of them), which is the finest resolution a quarter-hourly sync can honestly
carry.

- `spreadOverSlots(steps, fromMinute, toMinute)` lays the credited steps across the slots the
  interval between the two readings covered, in proportion to the time in each. The sensor gives no
  timing breakdown, so an even spread is the only claim the data supports — and it bounds the damage
  when Android defers the periodic work: an hour-late reading paints an even hour instead of a spike
  at the moment it happened.
- `fromMinute` is clipped to the start of the day, so a reading whose interval reaches back over
  midnight keeps everything on the day it is credited to. The breakdown of a day therefore always
  adds up to that day's total, and both are written in the same transaction.
- Rounding is cumulative rather than per slot, so the shares add up to exactly the steps credited.
- No foreground service is involved: the hardware counter accumulates while nothing runs, and the
  slots are reconstructed from the interval, not from the app being awake.
- Days walked before this table existed simply have no breakdown; a CSV import, which carries day
  totals only, drops the breakdown of any day whose total it changes.

Accepted losses, documented in the README: steps between the last reading and a reboot; up to
15 minutes of evening steps landing on the next day.

## Files

```
app/src/main/java/xx/steps/
  StepsApp.kt              Application: AppSettings.load(), schedules the periodic work (KEEP)
  MainActivity.kt          Scaffold + NavigationBar (three tabs), the ACTIVITY_RECOGNITION request,
                           live sensor reading via repeatOnLifecycle(STARTED)
  Common.kt                done
  steps/StepSync.kt        done
  steps/StepSensor.kt      readings(): Flow<Long> over callbackFlow; readOnce(timeout) for the
                           worker; isAvailable for phones without the sensor; hasStepPermission()
  steps/StepAccess.kt      READY / PERMISSION_MISSING / SENSOR_MISSING as a StateFlow the screens
                           and the live reading both watch; refreshed by the activity on start and
                           after a permission answer
  data/DaySteps.kt         @Entity day_steps: date TEXT PK (ISO), steps, goal INTEGER;
                           @Entity day_slots: (date, slot) PK, steps — the intra-day breakdown, a
                           row per quarter hour that has any;
                           @Entity sync_state: the one-row counter baseline (id, lastRaw,
                           bootTimeMillis) — in the database, not in preferences, so it is written
                           in the same transaction as the steps it accounts for
  data/StepsDao.kt         observeAll(), observeDay(date), dayRow(date), upsertDay(),
                           updateGoal(date, goal), syncState(), upsertSyncState()
  data/AppDatabase.kt      Room singleton; schemaLocation is already set in build.gradle.kts
  data/StepsRepository.kt  folds a reading, writes the day and the baseline in one transaction;
                           pins the current goal when a day's row is created, and updates the goal
                           of today's row only when the goal changes
  data/CsvFormat.kt        formatCsv / parseCsv / mergeDays — the format and the merge rule, free
                           of Android and covered by JVM tests
  data/CsvIo.kt            writes to Documents/Steps through MediaStore, reads a Uri from the
                           system picker, and folds the result into the database
  data/Backup.kt           ZIP holding the database file, plus restore; a port of BikeTracker's
                           Backup.kt and DatabaseRestoreCoordinator, cut down to one table
  settings/AppSettings.kt  object with StateFlows: goal, step length, paused, demo, themeMode,
                           accentIndex
  settings/AppTheme.kt     ThemeMode and AppLanguage, plus the LocaleManager read/write (API 33+)
  work/StepsSyncWorker.kt  CoroutineWorker: readOnce → repository.fold; periodic, 15 minutes
  ui/Theme.kt Color.kt Type.kt   ported from BikeTracker, plus the accent palette
  ui/TodayScreen.kt        progress ring and the seven bars of the past week
  ui/HistoryScreen.kt      year → month → day tree with period totals
  ui/SettingsScreen.kt     goal, step length, theme, accent, language, demo, CSV and ZIP later
  ui/DayModel.kt           buildDayBuckets() and dayStats(): the chart's bars and its figures,
                           free of Compose and covered by JVM tests
  ui/DayChart.kt           one day as bars from midnight to midnight, with the pointer that reads it
  ui/DayDetailDialog.kt    the day taken apart: hour or half-hour bars, the pointer readout, and the
                           day's figures
  ui/Dialogs.kt            NumberDialog, ChoiceDialog, ConfirmDialog — every dialog in the app
  ui/NoticeCircle.kt       amber circle with black text, standing in for the ring
  ui/Distance.kt           distanceLabel(): the one place a distance string is built
app/src/test/java/xx/steps/StepSyncTest.kt
app/src/main/res/values{,-ru,-uk}/strings.xml, values/themes.xml,
                 xml/locales_config.xml, xml/data_extraction_rules.xml
```

Manifest: `ACTIVITY_RECOGNITION`, `<uses-feature android:name="android.hardware.sensor.stepcounter"
android:required="false">`, `allowBackup="false"` — data moves through the app's own export.

## Screens

**Today.** A header with the date. A progress ring: grey track, with an arc from twelve o'clock
clockwise in proportion to the goal; once the goal is passed the ring closes and changes color.
Inside it the step count in large type, below it the goal and the percentage of it, set large and
counting on past the goal; once the goal is met "goal reached" appears above the count, in the
ring's own green. Under the ring, seven
bars for the past seven days, each scaled against the best day of that week, today highlighted, with
a dashed goal line across them and day labels underneath. The number grows live while the screen is
open. A tap on any bar opens that day's breakdown.

Special states take the ring's exact footprint as an amber circle with black text, so the screen
never reads as a genuine zero and nothing below it shifts: no sensor; permission not granted (with a
button that asks for it, turning into "open settings" once Android stops showing the dialog);
counting paused.

**Pause.** A button under the ring stops counting for a bus ride and resumes it after. While paused
the app still consumes readings and moves the baseline without crediting anything — the hardware
counts through the ride regardless, so discarding is the only way to not receive those steps in one
lump at the end. The state persists across restarts.

**Demo mode.** For looking at the interface on an emulator or a phone with no counter: it seeds
`DEMO_HISTORY_DAYS` of plausible days and feeds the app a simulated counter that climbs by a few
steps every second and a half, through the same folding path as the real one. Offered on the Today
screen when there is no sensor, and switchable from Settings. Starting or stopping it wipes the
database — a demo run and real history must never mix. The background worker stands down while it
runs, since the fake counter exists only while a screen is open.

**History.** An expandable year → month → day tree (a day is a leaf). Expansion state survives
rotation and Back collapses one level — the mechanics of `history/HistoryScreen.kt:398` in
BikeTracker (`groupByDate`, `orderedItemKeys`, `expanded`), one level shorter. Year and month rows
carry the total and the average per day, counted over days that have rows. A totals card sits on
top: week / month / year / all time. Days that met their goal are marked by the color of the
number — against the goal stored in that day's row. A tap on a day opens its breakdown.

**A day's breakdown.** A dialog over either screen: the day from midnight to midnight as bars, an
hour, half an hour or a quarter of one by three buttons — half an hour to begin with — built out of
the stored quarter hours, and ruled in amber every six hours. A pointer
reads it — the line follows the finger while its dot snaps to the top of the bar underneath, a tap
puts it where it landed, and it stays there to be read; above the chart it names the stretch of the
day and what was walked in it. It starts on the current hour for today and on the busiest stretch
for a past day. Under the chart: the day's total with its distance, the goal and the percentage of
it, the busiest stretch, and the hours between the first steps and the last. A day recorded before
the breakdown existed shows its total and says it has none.

**Settings.** Daily goal (a row plus an input dialog, 500–100 000); step length in centimetres,
70 by default, 30–120, which is the only input the distance readout has; demo mode; theme system/light/dark; accent
color from a palette of 6–8 swatches (check each for contrast in both themes); language
system/English/Russian/Ukrainian through the framework `LocaleManager` (API 33+); CSV export and
import; ZIP export and import of the database. Either import asks for confirmation first: CSV merges
by date, ZIP replaces the database wholesale.

## Order of work (one feature per commit)

1. Skeleton: `git init`, README, CHANGELOG, manifest, theme, `StepsApp`, empty three-tab navigation
2. Data: Room, DAO, repository, and `StepSyncTest` (first run, ordinary growth, reboot seen by the
   counter, reboot seen by boot time, zero delta, Int overflow). `CsvIoTest` joins it later: line
   parsing, malformed and duplicate dates, the merge rule
3. Sensor, WorkManager, the permission, live reading on the open screen
4. The Today screen (ring and week)
5. The History screen (tree and totals)
6. Settings: goal, theme, accent palette, language
7. CSV export/import, then ZIP export/import of the database (separate commits)
8. Full ru/uk localization, app icon (`make_icon.py` from BikeTracker)

## Verification

- `./06-Test.sh` — the `StepSyncTest` unit tests (pure logic, JVM, no device)
- `./05-Lint.sh` — Android Lint on the debug variant
- The user runs the build and the install (`./03-MakeDebug.sh`, `./15-SamsDebug.sh`); nothing is
  built or installed on their behalf
- Manual check on the phone: walk with the screen open — the number grows; leave it for 20 minutes
  and come back — the number has caught up through the worker; reboot the phone — today's count
  continues from the same figure instead of starting over
