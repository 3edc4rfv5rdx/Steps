# CHANGELOG
> Newest entries on top.
> N=new feature, E=error fix, F=fine-tune, R=refactor, I=infrastructure

## Unreleased
- I: The README names the three shared modules compiled in from beside the project, and the debug and .apkx steps its build table was missing
## v0.5.78 (2026-09-10)
- I: The README says what the app now does: it reaches the network for its own release, the notification pauses counting, and Settings and About carry the update check
- N: A pause button on the counting notification, so a bus or a bicycle can be paused without unlocking the phone; the card names the pause while it holds
- N: A copy of the database once a calendar day, made at launch and by the counting service, so days the app is never opened on get one too; the shared ../backups module keeps the newest three and a manual backup counts as the day's
- N: Two switches in Settings, both on by default: the daily backup, and the start-up update check — the latter is ../updater's own flag now, so it stops the check at launch and leaves the About dialog's button alone
- F: The About dialog's update button no longer promises an update that is not there: it opens as a check, asks the server as the card comes up, and turns into an inverted Update only when a newer build is published
- R: The About screen is the shared module's now — one dialog for every project here, with the version, build date, GitHub page, mailbox and the update check on it
- N: The About dialog has an Update button, so a new build can be looked for at once instead of waiting out the updater's six-hour interval; it answers even when there is nothing newer
## v0.4.74 (2026-09-08)
- E: 10-MakeRelease.sh printed the build date twice
- E: The release build failed to configure: the BUILD_DATE field needs buildFeatures.buildConfig, which was off
- N: The About screen shows the build date on a line of its own, where the build number used to be — the version already ends in that number
- I: The version is major.minor.build — the date left it — so the tag is v0.4.73 and an artifact steps-0.4.73-arm64-v8a.apk, each number written once; 20-MakeTag.sh puts the build date after the tag in the CHANGELOG heading, for the reader
- N: The app checks its own GitHub release for a newer build on start and offers it, the same updater the other projects here use — one manifest names every ABI split, so the phone takes arm64 and an armeabi-v7a box takes v7a, wherever either of them is
- I: 23-ToUpdate.sh writes that manifest into the release, run by hand after 22-RelUpload.sh
- I: OUT/ carries the universal APK beside the arm64 one, as it does in the other projects
- E: The version line looks only at release tags, so a tag like "duplex" can no longer answer which line the last release went out on
- I: The project docs name the artifact the way the scripts really write it
- I: Lint reruns instead of reprinting an up-to-date report, and says when the report was written
- I: A failed test prints its class, its name and the first lines of its message, and the summary links the HTML report
- I: The tag step refuses an untracked file too, so nothing can go into the APK without going into the tag
- I: The release uploads the exact APKs built for its tag, never the newest file of that ABI lying around
- I: A debug install with no phone connected exits 3 like every other step that had nothing to work on
- E: A test run that produced no results is reported as a failure instead of a clean pass
- E: An emulator install that failed makes the run fail, instead of being hidden by the pause after it
- E: The release push names the branch, and looks up the pushed tag by its full ref, so a tag whose name is a prefix of another is no longer taken for pushed
- E: Every step that touches the project's files or its git runs from the project directory, so one started from elsewhere can no longer work on the wrong tree
- E: The release notes carry the letter legend again, not the "newest on top" line that now sits above it in the changelog
- E: 99-CopyToAPKX.sh runs from the project directory, so its sweep of stale .apkx links can no longer delete them in whatever directory it was called from
- I: One CHANGELOG legend across every project here — N/E/F/R/I, newest on top, the type letter always followed by a colon
- I: Every changelog entry carries the colon after its type letter
- I: The waiting-feature check reads both "- N:" and "- N ", so a changelog entry moves the version line whichever way it was written
- I: Every artifact carries one name — steps-<version>-<build>-<abi>.apk, with -debug on the end of a debug build — and the tag it goes out under is v<version>-<build>.
- I: SPEC says why the background sync stands down in demo mode in the terms the code actually works in.
- R: An import, an export or a demo switch no longer holds on to the screen it was started from.
- E: A distance just under a kilometre reads as 1.0 km instead of 1000 m.
- E: A counting-journal batch that could not be written keeps its oldest line instead of dropping it.
- E: An operation refused or failed from the Today tab says so on that tab instead of nowhere.
- E: After an import raises today's count, the day chart shows no breakdown instead of bars that add up to less than the day.
- E: A step read by the background sync and the open screen at once can no longer take steps off the day.
- E: A step counter that restarts on its own no longer credits thousands of steps at once.
## v0.3.20260828+70
- N: The day you open from History stays marked with an outline of the accent until you open another.
- F: The day chart's pointer stands on the middle of the bar it reads and steps from one bar to the next.
- F: Today's row in History is filled with the accent colour instead of plain black or white.
- F: The dark theme's tonal buttons are a lighter grey.
- F: The chart's bar-width menu is only as wide as its labels, and an accent tint and outline set it apart from the dialog behind it.
- F: The day chart's controls run ‹ › − +.
- F: The day chart opens at quarter-hour bars, the resolution the day is actually stored at.
- F: The day chart's bar width moved into a drop-down beside the dialog's Close button, and the chart grew into the row of buttons it replaced.
- F: The day chart's bars are drawn wider, with a thinner gap between them.
- I: 06-Test.sh clears the previous run's results first, so a build that fails to compile can no longer be summarised as a pass.
- I: SPEC and the journal's own description say what the code does: the .txt extension, the whole test suite, and the release scripts.
- E: A dark start no longer flashes a white window, and the status bar icons follow the theme you picked rather than the phone's.
- R: The folder exports, backups and the journal are written to has one definition instead of three.
- E: The History tab follows the date over midnight instead of keeping yesterday's totals and today band.
- R: The History model drops a per-day average that no screen ever showed.
- E: An operation refused because another one is still running says so instead of ending in silence.
- E: Restarting the app during a demo run no longer drops the demo's whole starting count onto today.
- E: An import or a restore is refused while the demo runs, and says so, instead of mixing real days into the made-up ones.
- E: An import or a restore whose file cannot be read says so instead of closing the app.
## v0.2.20260827+57
- I: The README opens with four screenshots, kept in docs/screens.
- F: Today's label under the week bars is underlined as well as bold.
- F: Every bar of the week and of the day chart is the full accent colour, and the goal line and baseline are drawn brighter.
- F: The counting journal is off by default and its switch is gone from Settings — a diagnostic, not a setting.
- I: The README describes the write cadence, the journal setting and the new default goal as they now are.
- F: The daily goal starts at 10 000 steps instead of 8 000.
- F: The count is written at most twice a second while a screen is open, instead of on every event the sensor sends.
## v0.2.20260826+46
- I: SPEC's file list names every source file, including the eight it had never mentioned.
- E: Switching the demo happens whole or not at all, instead of being able to stop halfway with the history already gone.
- I: SPEC describes the pause control, the week bars, the tables and the backup file as they actually are.
- E: A notification put back after being swiped away always shows the current count.
- F: Today's day label under the week bars is set in bold, so the highlighted bar is not the only thing saying which day it is.
- I: The instrumentation test for the counting state checks the rule the app follows, not the one it replaced.
- E: The counting journal names the real reason a background run did not count, instead of blaming a missing sensor.
- E: An import or backup finishes even if you leave the Settings tab, and tells you what it did when you come back.
- I: The README describes the foreground service the app actually runs, instead of claiming it has none.
- F: The count is written once a minute while nothing is on screen, instead of on every step the sensor reports.
- E: Restoring a backup from elsewhere no longer breaks the History tab, and says how many days it could not read.
- E: Counting starts the moment the permission is allowed, and the notification is asked for right after the battery screen.
- E: Demo mode seeds its history at any daily goal instead of wiping the history and stopping.
- I: `10-MakeRelease.sh` raises the `major.minor` line by itself when a feature is waiting: an `N`
  entry under `Unreleased` is the whole decision, already made when the entry was written. It fires
  once per feature, since after the line moves the last tag still names the old one.
- I: `00-MakeAll.sh` no longer redraws the icons. The step rewrites tracked files, which left the
  tree dirty mid-build and stopped the version bump being folded into the previous commit — the
  very thing `02-MakeIcons.sh` says it stays out of a build to avoid.
## v0.2.20260826+45
- F: The launcher icon drops the road bar under the pedestrian. At icon size it was one more thing
  to read beside a figure that is already small.
- N: The day chart can be driven without pinching: a ⋮ button on the dialog's date line unfolds
  zoom and step-along controls beside it. Holding a finger still on the chart hands it over to
  dragging the window, with a tap of haptic feedback to say so. Touching the chart folds the
  controls away again.
- E: A batch of journal lines whose write fails is kept for the next attempt rather than dropped.
  Deleting the day's file used to take the lines in flight with it.
- E: Counting starts again by itself after a restart or an app update. Both leave the app installed
  and scheduled but not running, and nothing counted until the quarter-hourly worker next came
  round — a reboot cost up to fifteen minutes of walking.
- N: The day chart stretches: two fingers zoom its axis, up to three hours across, and the moment
  under them stays under them. The hour rules follow the zoom, so a stretched chart is still
  labelled, and the bar width can be changed without losing the zoom.
- F: The paused circle gives its play glyph the room it deserves and puts the word under it instead
  of beside it, so the one thing there is to tap reads at arm's length.
- N: Counting survives a locked screen: a foreground service keeps the app active, and its
  notification shows the app's name, today's steps and the distance on one line, under the walking
  figure from the Today tab, and does not expand. Swiping it away puts it straight back: Android 14 allows the
  swipe whatever the notification asks for, and losing the card would hide both the count and the
  one visible sign that the app is holding the sensor open.
  Without it Android stops handing sensor events to an app it considers idle — the registration
  stays in place, marked disabled, and a walk in a pocket is simply never delivered.
- N: A switch in Settings turns the counting journal off. It stays on by default, since a walk that
  went uncounted leaves nothing to look at unless it was recorded while it happened, and the file
  notes its own switching on and off so a gap in it is never unexplained.
- E: Steps taken with the phone in a pocket are counted again. The hardware counter is not
  free-running: it advances only while some app holds a registration on it, so listening only while
  the screen was on recorded just the steps taken in front of the app — about six per quarter hour
  against a real several hundred. The registration now lives as long as the process does. This was
  hidden for as long as another pedometer was installed and kept the sensor awake for everybody.
- I: A counting journal as a plain text file, `Documents/Steps/steps-<date>.txt`, one per day:
  every sensor event with its own timestamp, every reading with its raw delta, window and what
  survived the cap, and every background run that read nothing. Whether the sensor answers at all
  is the phone's decision, and this is the only place it is written down.
- E: A fresh install asks for the permission instead of claiming the phone has no step counter.
  Android hides the counter from an app that has not been allowed activity data, so the app was
  reading its own missing permission as missing hardware and offering nothing to fix it.
- F: Granting the activity permission leads straight into the battery exemption dialog, so both
  questions are asked in one go instead of the second one waiting on a system screen nobody
  opens by themselves. A phone already exempt, or with no such screen, is not asked.
- I: `02-DebugWiFiConn.sh` is gone: it held one hardcoded address, and its number now belongs to
  the icons.
- I: `00-MakeAll.sh` does a whole release in one run — icons, build, both installs, the `OUT/`
  link — and `19-LinkOut.sh` hard-links the newest arm64 APK into `OUT/` under its own name,
  sweeping what was there before. `bash 02-MakeIcons.sh` redraws the icons only when the
  drawing is newer than them. A missing emulator or phone now exits 3, so a full run counts it
  as skipped rather than failed.
- I: `22-RelUpload.sh`, taken from BikeTracker: it creates the GitHub Release for the newest tag out
  of that tag's `CHANGELOG.md` section and uploads the arm64 and universal APKs to it.
## v0.1.20260825+29
- The README says what the app now does: the three screens, the day taken apart, why the periods
  are calendar ones, and what a phone holding the app back costs.
- A CSV import asks before it merges, the way a restore does: it cannot be undone either, and it
  costs the breakdown of every day it changes.
- The demo has one control now, a flask in the top bar of the Today tab: lit while it runs, dimmed
  while it does not, and present on an emulator only. The button under the ring and the row in
  Settings are both gone, and the confirmation before the wipe came with it.
- The Background work row carries a line saying what it is about: battery optimisation delaying the
  counter read, set smaller than the row above it so it reads as a note rather than a setting.
- No "goal reached" line in the ring any more: the closed green arc and the green percentage say it
  between them, and the circle keeps the same five lines all day.
- The Background work row says so when the phone has no such system screen, instead of a tap that
  visibly does nothing.
- A CSV import says how many days lost their hourly breakdown to it, and the banner goes amber for
  it. A file carries day totals and nothing else, so a day whose total it raises cannot keep the
  breakdown of the old one; days left alone keep theirs.
- A day chart built at a bar width that does not divide the day folds the remainder into the last
  bar instead of indexing past it.
- Restoring a full backup brings the breakdown back with the days. The archive always held it —
  it is a copy of the database file — but the restore was reading only the day totals out of it.
- The backup dialog and the restore confirmation were each being composed twice on the Settings
  screen, one copy on top of the other; one of each is gone.
- A Background work row in Settings says whether Android is holding the app back and leads straight
  to the exemption: a restricted app is woken to read the counter less often, and a walk then
  arrives in one lump at the hour the app was next opened.
- The pedestrian in the launcher icon is a little smaller, with air above it inside the circle.
- The percentage inside the ring is set large, next in weight to the count itself, and it keeps
  counting past the goal — 146% rather than nothing. "Goal reached" moved above the step count,
  where it no longer takes the number's place.
- A tap on any bar under the ring, or on any day in the history, takes that day apart: its steps
  hour by hour, half hour by half hour or quarter by quarter, as a chart ruled in amber at every
  sixth hour, with a pointer that follows the finger and names the stretch it stands on, and the
  day's figures under it. The breakdown is recorded in
  quarter hours as the readings land, each reading spread over the stretch of time it covers, so it
  always adds up to the day's total; days walked before this version have none.
- The Today tab wears the same pedestrian as the launcher icon, cut from the sign by
  tools/make_icon.py — without the road, which at 24dp is only a smudge.
- The bottom bar sits above the on-screen back and home buttons on phones that have them: the app
  now declares edge-to-edge itself rather than being put into it by Android 15, and the bar takes
  the navigation inset.
- Today's row in the history is inverted: its two colours swap places, dark on light becoming light
  on dark, with slightly rounded corners.
- The Backup row carries a chevron, saying it leads somewhere, and the accent palette moved out of
  the settings list into a dialog of its own, leaving a single swatch on the row.
- Dialog buttons no longer wrap onto two lines, the long backup action is centred on one line, and
  Resume on the paused circle is set large enough to read as the thing to tap.
- The launcher icon is the pedestrian from the crossing sign, cut out of the sign itself by
  tools/make_icon.py rather than redrawn: the zebra stripes are dropped and replaced by one solid
  bar for the road, and the blue behind it matches the sibling EasySend icon.
- Demo mode is offered on an emulator only — on a real phone it could only wipe the history by
  accident — and an install carried from one to the other switches it off by itself.
- A localization pass across all three languages: the confirm button no longer says "wipe" in the
  dialog that restores a backup, the four backup actions read as one set, and the wording is
  tightened throughout.
- A full backup as a ZIP holding the database, written to Documents/Steps, and a restore that
  pours it back into the live database — every screen updates on its own, with no restart. A
  restore replaces rather than merges, so it asks first, and a file that is not a backup is refused
  by name before anything is touched. The counter baseline is never restored: it describes where
  this phone's sensor stood, and a figure from another phone would swallow or invent steps.
- Export and import moved behind one Backup row in Settings, holding all four actions.
- Outcomes are announced by a banner instead of a dialog: green when it worked, amber when it
  worked only partly (an import that skipped lines), red when it did not.
- The history can be written out as CSV to Documents/Steps and read back from any file the system
  picker reaches. One line per day, `date,steps,goal`, so it opens in a spreadsheet and can be
  edited by hand. An import merges by date and keeps the fuller record of each day, which makes
  importing the same file twice a no-op; malformed lines are skipped and counted rather than
  costing the rest of the file, and a day already stored keeps the goal it was judged by.
- The whole ring is the pause button now: tapping anywhere on it stops counting, and the amber
  circle it turns into resumes on the same tap. Each says which with an icon and a word inside the
  circle, so the separate button under it is gone.
- The bars show the current calendar week, Monday through Sunday, rather than the last seven days.
  Days still to come sit at zero until they are walked.
- The goal inside the ring reads "Goal: 8 000" instead of "of 8 000".
- Every dialog button is a filled pill now — confirm in the accent colour, dismiss tonal — and both
  come from one pair of shared components rather than being rebuilt in each dialog. Switching the
  demo on or off also lives in one place instead of being copied into the two screens that offer it.
- Settings gained an About row showing the version at a glance and opening the name, version and
  build number, as in BikeTracker.
- The language list is built from the locales the build actually ships, reading
  `locales_config.xml` through the platform, and each language is named in itself. Adding a locale
  now takes no code change, and the picker scrolls.
- Every screen now carries a top bar in the accent colour, and the History one holds two buttons:
  jump to today, and collapse the whole tree.
- The history totals are a table — week, month and year side by side, with everything ever walked
  under a rule beneath them. Per-day averages are gone, distances are whole kilometres, and the tree
  itself sits tighter so more of it fits on a screen. Every row reads "steps / kilometres", steps
  first as everywhere else, and today's row carries a tinted band so it is found at a glance.
- Settings rows read as "Step length, cm [70]": the unit belongs to the label, and the value is
  framed as the thing you tap to change.
- The Settings tab: daily goal, step length, theme (system, light or dark), an accent colour picked
  from six swatches that the ring, the bars and the buttons all follow, interface language through
  the system's per-app locales, and the demo switch. Changing the goal retargets today only, and
  turning the demo on or off asks before wiping the database.
- The app stays in portrait: Android 16 ignores an orientation lock unless an app opts back in, and
  this one is laid out for a tall screen held in one hand.
- The History tab: an expandable year, month and day tree, newest first, with each level carrying
  its total and its average day, and a card on top holding this week, this month, this year and
  everything ever walked. A day that met its goal shows its number in green, judged by the goal that
  day was walked against.
- Distance walked is shown under the step count and beside each total, from a step length set in
  Settings (70 cm by default).
- Counting can be paused for a bus ride and resumed after it. While paused the app keeps reading
  the counter and throws the readings away, so the ride's steps are gone rather than postponed, and
  the ring is replaced by an amber circle saying counting is paused.
- Whenever there is nothing to show — no step counter, no permission, counting paused — the ring's
  place is taken by an amber circle stating the reason in black, so the screen never reads as a
  genuine zero and nothing below it shifts.
- A demo mode, for looking at the interface on an emulator or any phone without a step counter: it
  seeds a couple of months of plausible days and feeds the app a simulated counter that walks by
  itself. Starting and stopping it both wipe the database, so demo days can never mix with real ones.
- The Today screen: a ring filling towards the daily goal with the count inside it, turning green
  once the goal is met, and the past seven days as bars underneath with the goal drawn across them.
  A phone that cannot count says why, and asks for the permission from the screen itself rather than
  throwing a dialog at a screen nobody has seen yet.
- Steps are actually counted now: the app asks for activity recognition, reads the counter directly
  while a screen is open, and a WorkManager job folds it in every quarter hour otherwise. A phone
  without a step counter is recognised as such rather than silently showing zero.
- The step store: a Room table of day, steps and the goal that day was judged by, and a one-row
  counter baseline written in the same transaction as the steps it accounts for.
- Counting reads the phone's uptime rather than an estimated boot time, so a restart is recognised
  by a clock that cannot drift, and no reading is credited beyond the four steps a second the
  elapsed time allows — firmware that carries its step counter across a reboot can no longer dump a
  lifetime total onto one day.
- Project skeleton: Gradle build, three-tab navigation, theme, localized app resources (en/ru/uk).
