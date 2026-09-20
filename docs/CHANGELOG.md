# Flip — changelog for this build session

Everything below is against the `src/` tree as received. Sections map to the
numbered features in the brief, followed by the work that wasn't asked for.

---

## Before anything else: two things I checked, and what came back

The brief asked me to verify rather than assume. The two that changed
decisions:

- **`androidx.security:security-crypto` is dead.** Confirmed — *every* API in
  the library is deprecated "in favour of existing platform APIs and direct
  use of Android Keystore". `EncryptedSharedPreferences`, `EncryptedFile` and
  `MasterKey` are all gone. Feature 8 is built directly on the Keystore.
- **`net.zetetic:android-database-sqlcipher` is also deprecated**, replaced by
  `net.zetetic:sqlcipher-android` for Play's 16 KB page-size requirement. I
  evaluated it and didn't use it — reasoning under feature 8.

Also checked and acted on: exact-alarm policy on Android 14+ (feature 10),
the Photo Picker contract (feature 5), Haze's beta status (feature 13), and
current Compose recomposition guidance (throughout).

---

## 1. Global analytics screen

New `Stats` tab, peer to Tasks / Notes / Settings.

- **Headline stats**: completions today, best *running* streak with the
  category it belongs to, rolling completion rate.
- **Calendar heatmap**, GitHub-contribution style, filterable by category.
- **Time-of-day × day-of-week heatmap**, same filter.
- **Per-category streaks**, current and longest, recurrence-aware.
- **Category comparison** over a selectable window (7d / 30d / 90d / 1y).
- **Notes activity trend** (the optional one) — built on a new
  `note_activity` table.

**Judgment calls:**

- *Completion rate means "share of days with any activity", not "completions
  ÷ tasks".* Tasks are created and deleted continuously, so a task-count
  denominator moves under you and the rate jumps when you tidy up.
  Days-with-activity is stable and comparable week to week.
- *"Best streak" means the longest one still running.* A dead 90-day streak is
  history, not a headline. Streaks that are alive but haven't been fed today
  are shown dimmed rather than broken — `StreakInfo.activeNow` carries that.
- *Horizontal bars for the category comparison, not a pie.* Angle comparison
  is a known weak point of visual perception; bars on a shared baseline are
  read accurately and leave room for real category names instead of a legend.

## 2. Per-category inline analytics

Stacked beneath the task list, same scroll, no navigation. Rolling and
calendar-month heatmap modes, plus a 30-day trend and the category's streak.

**This genuinely shares its logic.** `AnalyticsEngine` has one set of
functions; the inline block calls them with a `categoryFilter` argument and
lays the results out differently. There is no second aggregation
implementation anywhere in the codebase.

It lives in the tasks `LazyColumn` as an item, so it is disposed when it
scrolls away — and its ViewModel flow is subscribed with no grace period, so
the aggregation stops with it.

## 3. Completed tasks — hidden until requested

Completed work collapses into a reveal bar at the end of the active list.
Both affordances from the brief: tap the chevron (or the bar), or swipe up on
it. The bar is one of the three places translucency is used.

Previously the screen only ever queried *incomplete* tasks, so completed work
was simply unreachable. It now queries once and splits in the ViewModel —
which is also one fewer database observer.

## 4. Search

Across tasks and notes, results in one list with section headers.

**Scope is a toggle, not a setting** — which one you want depends on the
search ("where did I put that" is global; "is this already on today's list"
is local), so it's one tap and always visible.

Debounced at 180 ms: below the threshold where a delay reads as lag, above
the point where typing "groceries" fires nine queries.

Locked content is searchable once the vault is open and invisible before
that. Matching locked notes while locked would leak their contents through
the result count; skipping them while unlocked would look broken.

## 5. Background images

Notes and categories both. Curated set plus gallery picks.

- **System Photo Picker** (`PickVisualMedia`). The app declares **no storage
  permission at all**.
- **Picked images are copied into internal storage.** Picker URIs die on
  reboot, break if the user deletes the photo, and can't be persisted with
  `takePersistableUriPermission`.
- **The curated set is gradients, not bundled JPEGs.** Six photographs would
  add megabytes to the APK and still need decoding at runtime; gradients draw
  in one GPU op, scale to any size, and cost nothing in download size.
- Every decode is downsampled to its draw size and LRU-cached. A 12 MP phone
  photo at full size is ~48 MB of bitmap for a card 160 dp wide.

## 6. Pinning and sorting

Pinning on tasks, notes and categories. Sort by manual order, A–Z, date
created, date modified.

**Pinning is not a sort mode** — it composes with whichever mode is active.
Every comparator is `pinned → chosen key → id`. The trailing id tiebreak
matters: without it two items saved in the same millisecond swap places
between emissions, and a keyed LazyColumn animates the swap.

Task sort is stored per category (different lists want different orders);
note sort is session state.

## 7. Sharing, copying, exporting

Share sheet, clipboard, and file export for tasks, notes and whole categories
(as a Markdown checklist).

Shapes taken from comparable apps: Keep/Todoist's `title` + blank line +
`content` for notes, Obsidian's H1-titled Markdown for file export. No "sent
from Flip" footer — that's noise in someone else's inbox. Copy and share
produce identical text so the two never subtly differ.

Export goes through a `FileProvider` (scoped grant, not a `file://` URI) into
cache, which the system can reclaim.

## 8. Locking with encryption

Categories and notes, with a category lock covering everything inside it.

**Built on the Android Keystore directly**, AES-256-GCM, hardware-backed
where available, StrongBox where the device supports it (with a retry path,
because several shipping devices advertise StrongBox and then reject 256-bit
AES in it). Unlock gated through `androidx.biometric`'s `BiometricPrompt`.

**Judgment calls:**

- *Per-field encryption, not SQLCipher.* The brief asked me to evaluate
  rather than assume, and whole-DB encryption is the wrong shape here:
  SQLCipher needs its passphrase at `Room.databaseBuilder` time, and this app
  has a **home-screen widget** and a **quick-add trampoline** that read the
  database from a cold process with no UI and no way to prompt. Encrypting
  the whole database would blank the widget until the user unlocked the app —
  breaking the most-used surface in the product — and *still* wouldn't let one
  category be locked while another stays open. It also adds ~4 MB of native
  libraries across four ABIs.
- *A time-based Keystore key, not auth-per-use.* Auth-per-use keys must be
  unlocked via a `CryptoObject`, and the platform forbids pairing a
  `CryptoObject` with `DEVICE_CREDENTIAL` fallback. The brief asked for
  "biometric/device-credential", and a user with a PIN but no fingerprint
  still needs a way in. Five-minute validity window.
- *A successful prompt does not itself unlock anything.* `Vault.confirmUsable()`
  does a real cipher init afterwards, so the session flag can only be set by
  the Keystore actually agreeing.
- *Category names stay plaintext when locked.* You need to see the tab to
  know what to unlock. Locking encrypts what's *inside* — same model as Keep
  and Notion.
- *Voice recordings on a locked note are encrypted too*, and decrypted to a
  cache file that's deleted on stop. "Lock this note" shouldn't mean the
  words are hidden and the audio is sitting next to them in plain AAC.
- *`setInvalidatedByBiometricEnrollment(true)`* — enrolling a new fingerprint
  destroys the key. That's the correct posture (it stops someone with your
  unlocked phone from adding their own finger), but it makes locked content
  unreadable, so `Vault.isKeyInvalidated()` detects it and Settings says so
  plainly instead of showing a decrypt error.

**Also fixed as a consequence:** the widget would have rendered ciphertext on
the home screen. It now masks locked content while still counting it.

## 9. Local backups

One self-describing JSON document via the Storage Access Framework.

**Encryption is a toggle at backup time**, as specified — with the
consequence of each choice written next to it, because "encrypt?" isn't a
question most people can answer well without being told what happens if they
forget the passphrase.

**Judgment calls:**

- *JSON, not a copy of the `.db`.* A file copy is only restorable into the
  exact schema version it came from, needs WAL checkpointing to be
  consistent, and is unreadable if the app is gone.
- *Backup encryption uses PBKDF2 from a passphrase, not the Keystore vault
  key.* The vault key is device-bound and non-exportable — a Keystore-sealed
  backup couldn't be restored onto a new phone, which is most of the reason
  to take one. 210,000 iterations, random per-file salt.
- *Validation is a separate step before any write.* The file is inspected and
  summarised while live data is still intact; a corrupt file, a wrong
  passphrase or a file from another app is rejected there. The restore itself
  runs in one Room transaction, and orphaned rows (a task pointing at a
  category not in the file) are dropped and reported rather than aborting the
  whole thing on a foreign-key violation.
- Read is capped at 64 MB so picking the wrong file can't OOM the process.

## 10. Reminders

Per-task and per-category. A category reminder is the recurring default for
its list: when it fires it summarises what's outstanding, **skipping any task
that has its own reminder** so nothing is announced twice.

**Judgment calls:**

- *`AlarmManager`, not WorkManager.* WorkManager's floor is 15 minutes and it
  promises nothing about when inside a flex window a job runs. That's right
  for the nightly widget refresh nobody notices and wrong for "remind me at
  9:00".
- *`SCHEDULE_EXACT_ALARM`, not `USE_EXACT_ALARM`.* The latter is granted at
  install but Play restricts it to alarm-clock, timer and calendar apps;
  declaring it in a task app risks the listing. Since Android 14 the former
  is denied by default, so the scheduler checks `canScheduleExactAlarms()`
  and degrades to `setAndAllowWhileIdle` — reminders still arrive, just not
  to the second. Settings says which one you're getting and offers the route
  to the system screen.
- *Repeats are recomputed from the calendar, not by adding 24h.* Adding a
  fixed interval across a DST boundary drifts a 9:00 reminder to 8:00 and it
  never comes back.
- *`MY_PACKAGE_REPLACED` as well as `BOOT_COMPLETED`.* App updates clear
  pending alarms too. Without it every user silently loses their reminders on
  update — invisible until somebody misses something.
- *A locked category's task titles never reach the lock screen.* The reminder
  still fires (suppressing it would make locking silently break reminders) but
  says only that something is due.
- Tapping a notification deep-links to the right category via
  `SINGLE_TOP | CLEAR_TOP`, and selects it before the screen appears so the
  list doesn't flash the previous category.

## 11. Voice notes

**Several per note, not one.** The storage model is identical either way (a
row plus a file), but "several" avoids the awkward question of what happens
when you record over an existing memo, and matches how people use voice notes
— a few short thoughts rather than one long take.

AAC/MPEG-4 `.m4a`, mono, 96 kbps (~0.7 MB/min), `setPrivacySensitive(true)` on
API 30+. `MediaRecorder`/`MediaPlayer` rather than Media3 — one short local
file played inline doesn't need a session lifecycle and 1.5 MB.

Live level meter samples at 60 ms into a fixed-size ring buffer, so nothing
allocates per sample for as long as the user is recording.

## 12. Centralized theming

New `ui/theme/Tokens.kt` is the single source for every colour, spacing step,
radius, size and motion duration. `AppTheme.colors` / `.spacing` / `.radius`
/ `.sizes` / `.motion`, mirroring the shape of `MaterialTheme`.

The Material 3 scheme is now *derived from* the tokens rather than living
alongside them, so there's exactly one place to change a colour. All five
locals are `staticCompositionLocalOf`, so a token lookup costs nothing at
recomposition time.

Every screen and component was converted. The one deliberate exception is the
Glance widget, which runs outside composition and can't read a
CompositionLocal — it reads the same raw palette constants the token sets are
built from.

**This fixed a real bug:** `TaskItem`'s completion flash used two hardcoded
dark-mode hex values that looked wrong in light mode. It's now derived from
the theme's success colour.

## 13. Visual language: glass at the edges

Translucency at exactly three boundaries — app bar backdrops, the bottom nav,
and the completed-tasks reveal. Card interiors and list rows are flat and
opaque. No pill badges, no neon.

**Judgment call, and the one I'd most expect pushback on:** this is a
translucent tint plus a hairline plus a gradient fade, **not a real backdrop
blur**. Real backdrop blur on Android today means Haze (2.x still beta, and
it re-renders captured content through a two-pass shader every frame) or
`RenderNode.setBackdropRenderEffect` (Android 17 QPR2, nearly nobody has it).
`Modifier.blur` blurs a composable's own content, which is the wrong tool
entirely.

Given "performance and smoothness are the top priority, above any individual
feature", I took the version that costs one extra draw op and allocates
nothing per frame. It reads as glass because content genuinely shows through.
If you want a real blur later it drops into two functions in
`ui/theme/Glass.kt` and nowhere else.

---

## Performance work (not on the feature list)

The brief said to treat dropped frames and main-thread blocking as bugs
across everything I touched, so:

**Fixed: the note editor wrote to the database on every keystroke.** The old
`LaunchedEffect(title, content) { updateNote(...) }` meant typing a paragraph
was a few hundred Room transactions, each one waking the widget updater
across a process boundary. This was the most expensive thing the app did.
Now debounced through `snapshotFlow` at 600 ms, with a final flush on dispose
so nothing is lost. Same guarantee, roughly two orders of magnitude fewer
writes.

**Fixed: three database observers stayed alive for the whole process
lifetime.** `SharingStarted.Eagerly` kept the tasks flows querying while the
user was on Notes or Settings. Now `WhileSubscribed(5_000)` — the grace
period keeps state across a configuration change without re-querying.

**Fixed: recurrence reset only ran for the category you happened to open.**
A widget-driven tick on an unvisited list could act on last week's
checkboxes. There's now one sweep at app start.

**Fixed: locale-dependent week boundaries.** The old weekly period key used
the `"YYYY-ww"` pattern, which silently follows the device locale's
first-day-of-week — so the same history produced a different reset week in
the US than in India. Now explicit ISO weeks.

**Everything derived is computed off the main thread.** Sorting, splitting,
decryption and all analytics aggregation run on `Dispatchers.Default` and
collapse into a single `StateFlow`. Ticking one task produces **one** state
emission and one recomposition pass, not one per observed flow.

**Heatmaps are one `Canvas`, not a grid of composables.** A year-long
contribution graph is 365 cells and the time-of-day matrix is 168. As nested
`Box`es that's 500+ layout nodes to measure and place inside a scrolling
screen. As a draw loop it's one node and zero per-frame allocation. Same for
both charts — which is also why I didn't pull in a charting library.

**Analytics models use `IntArray`, not `List<Int>`**, and override
`equals`/`hashCode` to compare contents. Boxed integers would be re-walked by
the GC on every aggregation, and reference equality would defeat Compose's
skipping entirely.

**Display models decrypt once, off the main thread.** `TaskUi` / `NoteUi` /
`CategoryUi` arrive pre-decrypted, pre-sorted and `@Immutable`. Passing
entities down would mean an AES operation inside a composable on every
recomposition.

**Note snippets are truncated at the model boundary**, not by `maxLines`. A
40 KB note would otherwise have its entire body measured by the text layout
pass to render two visible lines — real jank in a staggered grid.

**Composite index on `(category_id, completed)`** replacing the
`category_id`-only one, because that's the shape of every list query.
`category_id` is denormalised onto `task_completions` so analytics never
joins, and it survives the task row being deleted.

**`LazyColumn` with `contentType`** on the analytics screen and the task
list, so Compose reuses a task row for a task row and never tries to reuse
one for a chart.

**Cross-fade rather than slide between tabs** — a slide has to rasterise both
screens into layers and move them, which on the analytics tab means
compositing a full screen of charts twice per frame.

**Spring, not tween, for list placement.** A reorder interrupted mid-flight
retargets from current velocity instead of snapping. Interruption is where
list animations usually look broken.

---

## Things I'd flag

- **Not compiled.** No Android SDK, Gradle or network in this environment.
  Structure, cross-file references and schema/DAO/migration agreement were
  checked by hand; expect a few import fixes on first build. See
  `BUILD_NOTES.md`.
- **You have to add the dependency yourself** — the zip had no build files.
  One new library: `androidx.biometric`. `MainActivity` had to become a
  `FragmentActivity` for it.
- **Note search is `LIKE '%q%'`, a table scan.** Deliberate: for a personal
  corpus that's well under a frame off the main thread, and it avoids an FTS4
  shadow table plus three sync triggers to keep correct through every future
  migration. FTS slots in behind the same function if the corpus ever grows
  past a few thousand.
- **Locked content in a backup stays device-bound.** It's written as the
  ciphertext already in the database, openable only by the original device's
  Keystore. The backup dialog says so rather than producing a restore with
  silently unreadable notes.
- **Follow-ups I didn't do:** migrating `SharedPreferences` to DataStore
  (real improvement, unrelated churn in a change this size), drag-to-reorder
  for manual task order (the data model supports it; only the gesture is
  missing), and a baseline profile.
