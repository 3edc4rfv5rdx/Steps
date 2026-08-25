# Device testing safety

- Run Android and instrumentation tests only on an emulator.
- Never install, launch, test, uninstall, or otherwise modify anything on a physical Android device through `adb` unless the user explicitly authorizes that exact action.
- Do not run Gradle `connected*AndroidTest` tasks while a physical device is among the connected targets.
- If an emulator is unavailable or any device action is needed, stop and ask the user first.

# Working rules

When fixing findings from `ADD/tofix*.md`:

- Finish and verify one finding at a time.
- Add a concise one- or two-line entry under `CHANGELOG.md` → `Unreleased` for every completed fix.
- Run the relevant focused tests, `./05-Lint.sh`, and the full `./06-Test.sh` before calling a fix complete.
- Commit each completed fix immediately as its own commit and report the commit hash.
- Do not include pre-existing or unrelated working-tree changes in that commit.
