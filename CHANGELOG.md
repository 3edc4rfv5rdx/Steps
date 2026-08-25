# Changelog

## Unreleased
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
