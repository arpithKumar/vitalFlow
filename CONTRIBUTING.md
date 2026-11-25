# Contributing to VitalFlow

How to build, test, organise code and ship changes. Short on purpose: if a rule is not here, follow the
surrounding code and the Android/Java conventions.

## 1. Prerequisites

- JDK 21 (Temurin recommended)
- Android SDK with platform 37 (`compileSdk 37`, `targetSdk 36`, `minSdk 26`)
- Android Studio (current stable), or just the command line

## 2. Everyday commands

```bash
./gradlew testDebugUnitTest      # unit tests (JVM, fast)
./gradlew lintDebug              # Android Lint -> app/build/reports/lint-results-debug.html
./gradlew assembleDebug          # debug APK
./gradlew assembleRelease        # release build; runs R8 (shrink + obfuscate)
```

The build uses `-Werror`: any compiler warning (deprecations, unchecked) fails the build. Fix the cause; only
suppress a warning with a comment explaining why it cannot be fixed.

**Always build the release variant before merging anything that touches data models, JSON, Firestore, or
reflection.** R8 renames and removes code that is only reached by name at runtime, and debug builds hide it.

## 3. What CI checks

`.github/workflows/android-ci.yml` runs on every push to `main` and every pull request: unit tests, lint, debug
build and release build. A change is mergeable only when it is green. The release R8 mapping is uploaded as an
artifact for diagnosing obfuscation problems.

## 4. Branches, commits, pull requests

- Work on a branch (`feat/...`, `fix/...`, `chore/...`, `refactor/...`); do not commit straight to `main`.
- **One branch per piece of work, always cut from the latest `main`.** Once a pull request is merged, that
  branch is finished: update `main` (`git switch main && git pull`), then create a new branch for the next
  piece of work. Never keep committing to a branch that has already been merged.
- **One concern per commit.** Do not mix a bug fix, a refactor and a style change in one commit.
- Commit messages use [Conventional Commits](https://www.conventionalcommits.org/):
  `type(scope): summary` with `feat`, `fix`, `refactor`, `perf`, `test`, `docs`, `build`, `ci`, `style`,
  `chore`. Explain *why* in the body.
- A pull request must build, pass tests and lint, and say what changed and how it was verified.
- Never commit secrets, keystores, `local.properties`, or IDE state (`.idea/`).

## 5. Where code belongs

| Package | Responsibility | Must not |
|---|---|---|
| `data/entity` | Room entities | contain UI or network code |
| `data/dao` | Room DAOs | be called from UI classes |
| `data/repository` | Combine DAO / network / Firestore | touch Views |
| `data/network` | Retrofit interfaces and API models | contain business rules |
| `di` | Hilt modules | contain logic |
| `diagnostics` | Crash reporting and other opt-in observability, behind small interfaces | send data before the user has opted in |
| `ui/<feature>` | Fragments, adapters, ViewModels, sheets of one feature | access a DAO directly; hold business rules |
| `workers` | WorkManager workers | live anywhere else |
| `receivers` | BroadcastReceivers | do long work without `goAsync()` |
| `widget` | App widgets | block the main thread |
| `utils` | Small, stateless, feature-neutral helpers | become a dumping ground |

Rules of thumb:

- A Fragment renders state and forwards user actions. Decisions and calculations belong in a ViewModel,
  repository or a plain helper class that can be unit-tested.
- One top-level class per file; the file name equals the class name.
- Do not ship dead code, commented-out code, or `printStackTrace()`. Handle or log errors deliberately.
- Never block the main thread (`blockingGet()`/`Tasks.await()` only on background threads).
- Dates and times that are stored or compared use `java.time` with an explicit zone and `Locale.ROOT` /
  ISO formats, never the device locale.
- User-visible text goes in `res/values/strings.xml`, not in Java or layout literals.

## 6. Resource naming

| Resource | Pattern | Example |
|---|---|---|
| Activity layout | `activity_<name>` | `activity_main` |
| Fragment layout | `fragment_<screen>` | `fragment_vitals` |
| List item | `item_<thing>` | `item_medication` |
| Dialog | `dialog_<purpose>` | `dialog_set_goal` |
| Bottom sheet | `bottom_sheet_<purpose>` | `bottom_sheet_add_med` |
| App widget | `widget_<name>` | `widget_water` |
| Icon (vector) | `ic_<name>` | `ic_heart` |
| Background / shape | `bg_<name>` | `bg_bottom_sheet` |
| Colour state list | `<role>_selector` | `nav_icon_selector` |
| Animation | `<verb>_<direction>` | `slide_in_right` |
| View IDs | `camelCase` with a type prefix | `btnSaveVital`, `tvStepsValue` |
| Colours | semantic, not literal | `text_primary`, not `dark_grey` |

Known deviations (to be renamed when the owning screen is reworked, not before):
`layout_add_vital_sheet`, `layout_bottom_sheet_health_plan`, `layout_home_menu_sheet`,
`layout_calender_tooltip` (typo), `layout_floating_chat`, `layout_skeleton_*`, and the drawables
`circle_shape`, `circular_progress`, `fade_teal`.
Also in the wrong package: `utils/MedicationWorker` and `utils/ReminderWorker` belong in `workers/`.

## 7. Removing unused code and files

Delete what is no longer used; do not comment it out or leave it "just in case". Git history is the backup.

1. Confirm it is unused: `./gradlew lintDebug` (rule `UnusedResources`) and a repository-wide search for the
   name (including `getIdentifier`, XML references and the manifest).
2. Remove it in its **own commit**: `chore: remove unused <what>`, listing the files and the evidence.
3. Rebuild debug **and** release and run the tests before pushing.

## 8. Tests

- Mirror the production package: `ui/home/Foo.java` is tested by `app/src/test/.../ui/home/FooTest.java`.
- JUnit 4. Name tests `subject_condition_expectedResult`.
- Unit tests must not need Android: keep logic in plain classes. Pin anything environmental
  (`Locale`, `TimeZone`, clock) inside the test.
- Every Room schema change ships with a migration and a migration test.
- Bug fixes start with a test that fails for the bug.

## 9. Release smoke test (run on a **release** build, on a device)

The release build is unsigned by default. Sign it with a local keystore that is **not** committed, install it,
and check:

- [ ] App starts, splash and navigation work; no crash in the first minute
- [ ] Sign in (email and Google); sign out
- [ ] Log water; the ring and amount animate
- [ ] Add a medication, mark a dose taken, skip a dose; delete it and pull to refresh (it must stay deleted)
- [ ] Log a vital (HR and BP) and see it in the list and chart
- [ ] AI chat returns a reply (the Gemini response model survives R8)
- [ ] Sync Now succeeds; reinstall, sign in, and confirm the data comes back with correct field values
- [ ] Notifications and the water/medication widgets update
- [ ] Dark mode, rotation and the offline banner behave
- [ ] Settings > "Share crash reports" is **off** on a fresh install; switching it on and off works
- [ ] Open `app/build/outputs/mapping/release/mapping.txt` and confirm the data models you serialise
      (Firestore documents, Gson request/response classes) are kept, not renamed
