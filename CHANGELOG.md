# Changelog

> N=new feature, E=error fix, F=fine-tune, R=refactor, I=infrastructure, T=tag

## Unreleased
- I `00-MakeAll.sh` does a whole release in one run — icons, build, both installs, the `OUT/`
  link — and `19-LinkOut.sh` hard-links the newest arm64 APK into `OUT/` under its own name,
  sweeping what was there before. `bash 02-MakeIcons.sh` redraws the icons only when the
  drawing is newer than them. A missing emulator or phone now exits 3, so a full run counts it
  as skipped rather than failed.
- I `22-RelUpload.sh`, taken from BikeTracker: it creates the GitHub Release for the newest tag out
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
