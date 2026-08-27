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
| Storage | Room: a table of day → steps, one of day → quarter hour → steps, and a one-row sync state holding the counter baseline |
| Background | a foreground service holds the app active so sensor delivery continues with the screen off; its notification is today's count. A WorkManager job every 15 minutes restarts what the system killed |
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
- `shouldFold()` decides how often a reading is written. An on-change counter reports every step or
  two and every reading written is a transaction, so both cases have a floor: `FOREGROUND_FOLD_INTERVAL_MS`
  (2 s) with a screen in front of the user, short enough that the count on it still grows as they
  walk, and `BACKGROUND_FOLD_INTERVAL_MS` (a minute) with none. The counter is cumulative, so a
  reading held back loses nothing — the next one carries its steps — and the quarter-hourly worker
  closes the day whatever happens. A pause is the exception and writes every reading whatever is on
  screen: it throws steps away rather than postponing them, and where the baseline stands when it
  ends decides which ones. The cost of the cadence is the reboot window, up to a minute wide instead
  of seconds.

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
- Counting needs a foreground service. The hardware counter advances only while some app holds a
  registration on it, and Android stops delivering to a registration whose UID has gone idle — so
  the slots are reconstructed from the interval between readings, not from a counter that ran
  unattended.
- Days walked before this table existed simply have no breakdown; a CSV import, which carries day
  totals only, drops the breakdown of any day whose total it changes.

Accepted losses, documented in the README: steps between the last reading and a reboot; up to
15 minutes of evening steps landing on the next day.

## Files

```
app/src/main/java/xx/steps/
  StepsApp.kt              Application: AppSettings.load(), schedules the periodic work (KEEP)
  MainActivity.kt          Scaffold + NavigationBar (three tabs), the ACTIVITY_RECOGNITION request;
                           refreshes StepAccessState and starts StepsService on every start, and
                           owns the chain of questions that follows a granted permission. It does
                           not read the sensor
  Common.kt                done
  StepLog.kt               the counting journal, off by default and with no switch on the Settings
                           screen — SHOW_JOURNAL_SETTING hides the row rather than deleting it, so
                           a phone that miscounts is one flag away from recording why:
                           logSteps() queues a line, a background writer
                           appends it to Documents/Steps/steps-<date>.txt through MediaStore, one
                           file per day. Whether the sensor answers a registration at all is the
                           phone's decision and nothing else in the app records it
  steps/StepSync.kt        done
  steps/StepSensor.kt      readings(): Flow<Long> over callbackFlow; readOnce(timeout) for the
                           worker; isAvailable for phones without the sensor; hasStepPermission()
  work/StepsBootReceiver.kt starts the service again after a reboot or an app update, neither of
                           which leaves the app running
  work/StepsService.kt     foreground service: keeps the UID active so sensor delivery continues,
                           and shows today's steps and distance in its notification. Does not read
                           the sensor itself. Its collector runs on the main thread, which is also
                           where the framework calls the dismissal receiver, so the readout the two
                           share needs no publishing between threads
  steps/StepCounting.kt    holds the sensor registration for the life of the process and folds the
                           readings in at the cadence shouldFold() sets; started by StepsApp, not by
                           a screen, because the hardware counter stands still while nobody is
                           registered on it. Watches the activity lifecycle for whether a screen is
                           in front of the user at all
  steps/DemoSteps.kt       the simulated counter and the made-up history behind demo mode, offered
                           on an emulator only. demoHistory() builds a whole run before anything is
                           written; toggle() empties the database and refills it in one transaction
  steps/StepAccess.kt      READY / PERMISSION_MISSING / SENSOR_MISSING as a StateFlow the screens
                           and the readings both watch; refreshed by the application on start, and
                           by the activity on every start and after a permission answer. The permission is decided before the sensor
                           is looked for: Android hides the step counter from an app without
                           ACTIVITY_RECOGNITION, so "no counter" cannot be told from "not allowed
                           yet" until the permission is granted. stepAccessOf() is that rule, pure
                           and pinned by a JVM test, and everything else asks it rather than working
                           the answer out again. nextPermissionAsk() is the order the remaining
                           questions go in, kept pure for the same reason
  data/DaySteps.kt         usableRows(): what a restore may take out of an untrusted database,
                           free of Android and covered by JVM tests;
                           @Entity day_steps: date TEXT PK (ISO), steps, goal INTEGER;
                           @Entity day_slots: (date, slot) PK, steps — the intra-day breakdown, a
                           row per quarter hour that has any;
                           @Entity sync_state: the one-row counter baseline (id, lastRaw,
                           lastUptimeMillis, from SystemClock.elapsedRealtime() and never from the
                           wall clock) — in the database, not in preferences, so it is written
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
  data/ZipBackup.kt        ZIP holding the database file, plus restore; a port of BikeTracker's
                           Backup.kt and DatabaseRestoreCoordinator. Restores the days and their
                           breakdown, keeping only the rows usableRows() can prove are readable
  settings/AppSettings.kt  object with StateFlows: goal, step length, paused, demo, themeMode,
                           accentIndex, journalEnabled
  settings/PowerSettings.kt whether the app is exempt from battery optimisation, and the intent
                           that asks for it or gives it back. Nothing here counts steps; what the
                           restrictions bear on is how often the app is woken to read the counter
  settings/AppTheme.kt     ThemeMode and AppLanguage, plus the LocaleManager read/write (API 33+)
  work/StepsSyncWorker.kt  CoroutineWorker: refreshes StepAccessState and stands down on whatever
                           it says, then readOnce → repository.fold; periodic, 15 minutes
  ui/Theme.kt Color.kt Type.kt   ported from BikeTracker, plus the accent palette
  ui/TodayScreen.kt        progress ring and the seven bars of the past week
  ui/StepRing.kt           the progress ring, the figures inside it, and the tap that pauses —
                           the whole ring is the control
  ui/WeekBars.kt           buildWeekBars(): the seven bars of the calendar week, gaps filled, free
                           of Compose and covered by JVM tests; and the canvas that draws them
  ui/HistoryScreen.kt      year → month → day tree with period totals
  ui/HistoryModel.kt       buildHistoryTree(), historyTotals() and visibleKeys(): the grouping, the
                           calendar-period totals and the rows on show, free of Compose and covered
                           by JVM tests
  ui/HistoryCommands.kt    the top bar's two History buttons reach the tree through this, since the
                           buttons live in the activity and the expansion state in the screen
  ui/SettingsScreen.kt     goal, step length, background work, theme, accent, language, CSV and ZIP
  ui/DayModel.kt           ChartView, zoomedView() and gridHours(): the stretch of the day on
                           show and the hours it is ruled at, as plain functions.
                           buildDayBuckets() and dayStats(): the chart's bars and its figures,
                           free of Compose and covered by JVM tests
  ui/DayChart.kt           one day as bars from midnight to midnight, with the pointer that reads
                           it. Two fingers stretch the X axis, holding one finger still hands it
                           over to dragging the window, and one gesture handler decides between
                           tap, scrub, pan and pinch. ChartMenuButton is the fold-out control
  ui/DayDetailDialog.kt    the day taken apart: hour or half-hour bars, the pointer readout, and the
                           day's figures
  ui/ScreenWork.kt         the jobs a screen starts — export, import, restore, the demo switch —
                           and the one message they leave behind: both held by the process, since a
                           screen is a branch of a `when` on the tab and its scope goes when the tab
                           does. One job at a time; no composition owns such work
  ui/StatusBanner.kt       one banner for every outcome in the app: green when it worked, amber
                           when it worked partly, red when it did not
  ui/DialogButtons.kt      the two buttons every dialog ends with — the accent one commits, the
                           tonal one backs out, and neither is ever a bare text button
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
counting on past the goal. Nothing announces the goal in words: the arc closes and both it and the
percentage turn green, which says it without a line of text. Under the ring, seven
bars for the current calendar week — it starts on the locale's first day, not seven days ago —
each scaled against the taller of that week's best day and the goal, so the goal line cannot sit off
the top edge and leave the week looking complete. Every bar is the full accent colour, a day that met its goal green; today
is told apart by its label, set in bold below the bar, since nothing in this app is dimmed to say
what it is. A dashed goal line runs across them, day labels underneath. The number grows live
while the screen is open. A tap on any bar opens that day's breakdown.

Special states take the ring's exact footprint as an amber circle with black text, so the screen
never reads as a genuine zero and nothing below it shifts: no sensor; permission not granted (with a
button that asks for it, turning into "open settings" once Android stops showing the dialog);
counting paused. Granting the permission starts the foreground service at once, and leads straight
into the questions that are still open, one at a time: the battery exemption first, unless the app
is exempt already, then permission to post the notification the count lives in. All of it is asked
while the user is already answering for this app, rather than left to a system screen nobody opens
unprompted or to the next launch. The chain is held by the activity, not by the button that starts
it — that button is gone the moment the permission is granted.

**Pause.** The ring itself is the pause control — a target that size needs no aiming, and the glyph
and word inside it say what a tap does. It stops counting for a bus ride, and the amber circle that
replaces it resumes. While paused
the app still consumes readings and moves the baseline without crediting anything — the hardware
counts through the ride regardless, so discarding is the only way to not receive those steps in one
lump at the end. The state persists across restarts.

**Demo mode.** For looking at the interface on an emulator or a phone with no counter: it seeds
`DEMO_HISTORY_DAYS` of plausible days and feeds the app a simulated counter that climbs by a few
steps every second and a half, through the same folding path as the real one. Its one control is a
flask in the top bar of the Today tab, lit while the demo runs and dimmed while it does not, and
present on an emulator only. Starting or stopping it empties the database of what was in it — a
demo run and real history must never mix — in one transaction, so the switch either happens or does
not: built whole first, written once. The background worker stands down while it runs, since the fake counter exists only while a screen is open.

**History.** An expandable year → month → day tree (a day is a leaf). Expansion state survives
rotation and Back collapses one level — the mechanics of `history/HistoryScreen.kt:398` in
BikeTracker (`groupByDate`, `orderedItemKeys`, `expanded`), one level shorter. Year and month rows
carry the total and the average per day, counted over days that have rows. A totals card sits on
top: week / month / year / all time. Days that met their goal are marked by the color of the
number — against the goal stored in that day's row. A tap on a day opens its breakdown.

**A day's breakdown.** A dialog over either screen: the day from midnight to midnight as bars, an
hour, half an hour or a quarter of one by three buttons — half an hour to begin with — built out of
the stored quarter hours, every bar the full accent, and ruled in amber every six hours. A pointer
reads it — the line follows the finger while its dot snaps to the top of the bar underneath, a tap
puts it where it landed, and it stays there to be read; above the chart it names the stretch of the
day and what was walked in it. It starts on the current hour for today and on the busiest stretch
for a past day. Under the chart: the day's total with its distance, the goal and the percentage of
it, the busiest stretch, and the hours between the first steps and the last. A day recorded before
the breakdown existed shows its total and says it has none.

**Settings.** Daily goal (a row plus an input dialog, 500–100 000); background work, a row showing
whether the app is exempt from battery optimisation and leading to the system dialog that grants it
or the list that takes it back — a restricted app is woken to read the counter less often, which
costs the day its shape rather than its steps; step length in centimetres,
70 by default, 30–120, which is the only input the distance readout has; theme system/light/dark; accent
color from a palette of 6–8 swatches (check each for contrast in both themes); language
system/English/Russian/Ukrainian through the framework `LocaleManager` (API 33+); CSV export and
import; ZIP export and import of the database. Either import asks for confirmation first: CSV merges
by date, ZIP replaces the database wholesale. Either one runs to the end whether or not the screen
that started it is still on show, and says what it did when the user comes back to it. A restore keeps only the rows it can read back — every
reader of a stored date parses it, so a date that will not parse is dropped rather than stored and
crashed on afterwards — and says how many days it dropped. An archive in which no day survives is
refused, and the stored history is left as it was.

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
