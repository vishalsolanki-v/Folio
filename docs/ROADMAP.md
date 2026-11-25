# Folio roadmap

_Ordered list of improvements from the [audit](AUDIT.md) (25 November 2026, commit `c657420`). Each item names the audit finding it fixes, so the evidence, reproduction and test are one click away._

**Principles that every item must keep:** offline, no INTERNET permission, no analytics, ads, accounts or tracking SDKs, no new permissions, no cloud. No item below breaks any of these. Items marked _(decision)_ need a product call before work starts.

Sizes: **S** = under an hour, **M** = a few hours, **L** = a day or more.

---

## Release 1.0.1: crashes and data loss (hotfix)

Ship these together, soon. Each fix is small and local, and every one is something a user can hit in normal use.

| # | Item | Findings | Size | Test that proves it |
|---|---|---|---|---|
| 1 | Cap PDF page bitmaps at every zoom level (remove `coerceAtLeast(baseWidth)` from the cap path) | FMT-1 | S | Unit test of `renderWidth` for aspect 0.04 at zoom 1 and 4; tall-page PDF in landscape |
| 2 | Limit file names by UTF-8 bytes, not characters; guard `sharedTextFile` with a fallback name | CORE-3 | S | `LocalCopiesTest` with 200 CJK characters; `ExternalOpenTest` SEND with a long CJK subject |
| 3 | Make `Sharing.outgoingUri` return null instead of throwing; refuse external `file://` URIs into Folio's own data dir | CORE-1 | S | `ExternalOpenTest`: VIEW `file:///system/etc/hosts` doesn't crash |
| 4 | Editor: Discard clears `dirty` first, and no draft is written after discard or close | FEAT-3 | S | Compose test: edit, Back, Discard, then no draft and no banner on reopen |
| 5 | Editor: one source of truth for the text (`editor ?: liveText ?: doc.text`, never `""`); Save copy takes its text from the ViewModel or draft, not the live view | FEAT-1, FEAT-5 | M | `ActivityScenario.recreate()` with a stubbed CreateDocument result; copy bytes equal the edited text |
| 6 | Editor: revision counter so edits typed during a save stay dirty | FEAT-4 | S | ViewModel test with a slow fake saver |
| 7 | Editor: reset `closeOnSave` on validation, failure or cancel | FEAT-10 | S | Compose test of the Fix-it, then Save flow |
| 8 | Release signing: fail `assembleRelease` without `keystore.properties`; check the certificate fingerprint in the checklist | DIST-2 | S | `assembleRelease` without the keystore fails |
| 9 | Strip the dependency-metadata block (`dependenciesInfo { includeInApk = false }`) | DIST-1 | S | Signing-block ID check in RELEASE_CHECKLIST |

Before shipping 1.0.1, run the **upgrade from v1.0.0** test in TEST_MATRIX.md (section L). The schema is unchanged, so no migration is needed. Confirm that this still holds.

---

## Release 1.1: keeping access to files, plus correctness

| # | Item | Findings | Size |
|---|---|---|---|
| 10 | **Migration test setup before any schema change:** `room-testing`, schemas as androidTest assets, a v1 → current test | CORE-9 | S |
| 11 | Persist the grant on the renamed URI (once, in `Sharing.renameFile`); update the Viewer's saved URI | FEAT-2 | M |
| 12 | De-duplication never gives up a persisted grant for a temporary one (and update `LibraryTest`) | CORE-2 | S |
| 13 | Removing a folder also removes (or warns about) Recents rows inside it | CORE-8 | S |
| 14 | Folder list reloads on resume | FEAT-7 | S |
| 15 | Treat AUDIO and VIDEO as agreeing families, so `.m4a` with `isom` stays audio | FMT-3 | S |
| 16 | Don't pause media on rotation (`isChangingConfigurations`); keep the screen on while video plays | FMT-2, FMT-11 | S |
| 17 | PPTX: gradient, picture and pattern backgrounds fall back to the first stop or `bg1`, never white | TXT-1 | S |
| 18 | One `decodePrefix` helper that cuts at a newline or character boundary before decoding (CSV over 50 MB, text over 5 MB) | TXT-2 | S |
| 19 | Pretty view, validation and HTML templating off the main thread | TXT-3, FEAT-14 | S |
| 20 | Sheets: report "too large" instead of "damaged", add a cell budget, and separate the column-truncated message | TXT-4, TXT-13 | M |
| 21 | DOCX size guard (about 40 MB), with templating inside the try/catch | FMT-6 | S |
| 22 | Close PDFium and ZIP handles when the open is cancelled | FMT-7 | S |
| 23 | Reject `DOCDECL` in OOXML parts (billion-laughs guard) in `Xml.parse` and `XlsxReader` | CORE-4 | S |
| 24 | `RandomAccess.file`: size and free-space check, temp file then rename, clean up on failure | CORE-5 | S |
| 25 | DataStore `ReplaceFileCorruptionHandler` | CORE-6 | S |
| 26 | `dataExtractionRules` excluding Recents, copies, drafts and crash logs from device-to-device transfer | CORE-7 | S |
| 27 | Markdown links: allow only http, https and mailto, and ask first ("Open in browser?") | TXT-6 | S |
| 28 | De-duplicate `SEND_MULTIPLE` URIs; skip duplicate ZIP entry names | FEAT-13, FMT-4 | S |
| 29 | `record()` in Recents survives Back (NonCancellable) | FEAT-12 | S |
| 30 | CI workflow: unit tests, lint, licensee, assembleDebug on every push and pull request | DIST-3 | S |
| 31 | `CHANGELOG.md` as the single source for release notes | DIST-4 | S |

---

## Release 1.2: accessibility and polish

| # | Item | Findings | Size |
|---|---|---|---|
| 32 | Toggle and selected semantics on switches, chips and sheet toggles | FEAT-6 | S |
| 33 | PDF page text exposed to TalkBack | FMT-8 | M |
| 34 | Spreadsheet grid: full row descriptions with column letters, open-cell action, scroll semantics | TXT-5 | M |
| 35 | Headings, slider label, page-pill click label | FEAT-17 | S |
| 36 | Scrollable empty and error states, wrapping button rows (landscape, large fonts) | FEAT-8 | S |
| 37 | Extension-badge ink at ≥ 4.5:1 contrast | FEAT-16 | S |
| 38 | State survives rotation and process death: multi-select, rename dialogs, Home search, filter and sort | FEAT-9, FEAT-11, FEAT-18 | S |
| 39 | Keep the reading position when the paper changes, on rotation (DOCX and HTML), and on "Load more" | FMT-10, TXT-16 | M |
| 40 | Show the truncation banner in Markdown and HTML previews | TXT-17 | S |
| 41 | Large-file performance: lazy Markdown preview or a cap; plain text for very long lines | TXT-8, TXT-9 | M |
| 42 | Cancellable archive extraction | FMT-14 | S |
| 43 | Open-with task affinity, so Back returns to the sending app | CORE-10 | S |

---

## Later: format fidelity (by user demand)

Pick items from these as users report problem files through "Send feedback".

- **Spreadsheets:** `[h]` elapsed times (TXT-10), CJK built-in dates (TXT-11), hidden sheets and active tab (TXT-12), and number formats (decimals, thousands, currency).
- **Slides:** picture crops (TXT-14), drop the parsed XML after reading (TXT-15), and speaker notes.
- **Media and images:** register AVI, MPG, FLV, MKA and M4B (FMT-13); EXIF-correct printing (FMT-9).
- **PDF:** hide Print for password-protected files, or render them through PDFium (FMT-15).
- **Text:** legacy encodings such as windows-1251, GBK and Shift-JIS, read-only (CORE-11); XML Pretty view entity spacing (TXT-19); clean up `shared_text` (CORE-12).
- **Code:** fix the README's "40+" claim now, then add grammars or name-based detection (`Dockerfile`, `Makefile`) (TXT-20). Add a grammar-load fallback plus an instrumented test over all scopes (TXT-7).
- **Printing:** text print truncation, cancellation and stale-layout fixes (TXT-18).
- **Archives:** CP437 names on Android 8 to 11 (FMT-5, needs an API 26 to 30 check first); API 34+ traversal entries (FMT-12, not reproduced on API 37, so recheck only).
- **Localization** _(decision)_: strings are hard-coded English by design (FEAT-15). Move them into `strings.xml` only if translations are planned.

---

## Distribution track (can run in parallel)

1. **1.0.1:** DIST-1 and DIST-2 (above). Publish the signing-certificate SHA-256 next to the APK hash in the README and every release (DIST-5).
2. **Reproducible builds:** build twice from clean clones and compare with `diffoscope`; fix any differences (DIST-5).
3. **IzzyOnDroid** as the first F-Droid-compatible repo, since it ships your own signed APK. It needs DIST-1 fixed and fastlane metadata (DIST-4).
4. **F-Droid main repo** _(decision)_: first check the prebuilt PDFium native libraries with `fdroid scanner` and the maintainers, and check that the build server supports the AGP 9.4, Gradle 9.8 and SDK 37 toolchain. Use reproducible builds so F-Droid can ship your signature, otherwise GitHub and F-Droid installs can't update each other. **Don't claim F-Droid compatibility until a build passes there.**
5. Consider enabling APK Signature Scheme v3 before the install base grows, so key rotation is possible later.

## Deliberately not on this roadmap

- Any network feature, including in-app update downloads. "Check for updates" stays a browser link.
- Crash-reporting or analytics SDKs. The email-draft crash report stays.
- Storage permissions (`MANAGE_EXTERNAL_STORAGE`, `READ_MEDIA_*`). The SAF picker and added folders cover the use case.
- Editing DOCX, XLSX or PPTX. View-only protects the files' layout, as the README promises.
- Architecture rewrites. The current structure (core / feature / formats, per-entry ViewModels) is sound; every fix above is local.
