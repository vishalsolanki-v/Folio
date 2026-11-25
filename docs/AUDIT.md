# Folio audit

_Audit of `main` at commit `c657420` (v1.0.0 plus the README), dated 25 November 2026. No application source code was changed._

Paths in the findings are relative to `app/src/main/java/com/folio/app/` unless they start with `app/`, `docs/` or a repo-root file name. Line numbers refer to commit `c657420`.

## How this audit was done

1. **Code read in full**, area by area. Every Kotlin file in `app/src/main` was read: the app shell, `core/data`, `core/files`, the design system, every `feature/` screen, and every `formats/` renderer. So were the manifests, ProGuard rules, Gradle files, tests, README and `docs/`.
2. **Tests run.** The unit suite gave **85 of 85 passing** (`testDebugUnitTest`). The instrumented suite on an Android 16 (API 37) emulator gave **34 of 34 passing** (`connectedDebugAndroidTest`).
3. **Runtime checks.** The riskiest claims were re-tested on the emulator with generated test files: hostile ZIPs, a tall PDF, a dark-template PPTX, an `isom` M4A, Markdown links, corrupted files, one sample per syntax grammar, and Unicode text. Each finding below says whether the emulator confirmed it, failed to reproduce it, or wasn't run.
4. **Release artifact inspected.** The local `app-release.apk` has the same SHA-256 as the one published in the README (`9d4ce51d…0d9f17`), so the shipped v1.0.0 was inspected directly: signing scheme, certificate, signing-block contents and merged manifest.

**Status labels:** *Confirmed bug* means the code path was traced and it misbehaves (the emulator check, where run, is noted). *Potential risk* depends on the input, device or provider. *Unverified assumption* means it needs a runtime check that wasn't possible here (no API 26 to 30 image, no TalkBack pass, no real phone).

**Not covered:** a TalkBack walkthrough, real low-RAM hardware, Android 8 to 11 behaviour (the API 26 AVD no longer exists on this machine), video playback (the emulator's H.264 decoder failed with a Codec2 BufferPool error, which is not Folio's fault), and mammoth's internals (the library source wasn't available).

## Executive summary

Folio's foundations are strong. Privacy rests on the manifest rather than on promises: INTERNET, ACCESS_NETWORK_STATE and WAKE_LOCK are removed, and the only permission in the merged manifest is AndroidX's app-private receiver permission. The WebView is locked down, Markdown has no image loader, the ZIP handling resists zip slip and zip bombs, saves are verified and rolled back on failure, drafts survive a crash, and corrupted PDF, DOCX and XLSX files and random bytes all show an error screen instead of crashing. **No Critical issues were found.**

**70 findings:** 0 Critical, 4 High, 24 Medium, 42 Low (after the emulator re-ratings).

**Fix these first:**

1. **FMT-1:** a tall PDF page crashes the app in landscape (emulator: `Canvas: trying to draw too large(538255944bytes) bitmap`). The fix is a one-line pixel cap.
2. **CORE-3:** sharing text whose subject is a long Hindi, Chinese or other non-Latin title crashes Folio (`ENAMETOOLONG`, confirmed). The same bug silently drops private copies of files with long non-Latin names.
3. **CORE-1:** a `file://` link from another app to a readable file outside Folio's FileProvider roots crashes the viewer (confirmed with `/system/etc/hosts`).
4. **The editor data-loss cluster:** FEAT-1 (Save copy after the activity is recreated), FEAT-3 (Discard doesn't discard, confirmed), FEAT-4 (typing during a save) and FEAT-5. These are where users lose words.
5. **Losing durable access to files:** FEAT-2 (rename drops the persisted grant) and CORE-2 (de-duplication releases the persisted grant). Pinned files later become "no longer available".
6. **Distribution:** DIST-2 (a release can be signed with the debug key without any warning) and DIST-1 (the dependency-metadata block blocks F-Droid).

## Runtime verification on the emulator (Android 16, API 37, debug build of `c657420`)

| Check | Result | Effect on findings |
|---|---|---|
| One-page PDF, 595 × 14,400 pt, portrait | Renders correctly | Not triggered at 1× on a 1080 px screen |
| Same PDF, landscape | **Crash:** `Canvas: trying to draw too large(538255944bytes) bitmap` | **FMT-1 confirmed** (High) |
| `VIEW file:///system/etc/hosts` | **Crash:** `IllegalArgumentException: Failed to find configured root` | **CORE-1 confirmed** |
| `SEND` text with a 100-character CJK subject | **Crash:** `FileNotFoundException … ENAMETOOLONG` in `ExternalOpenActivity.onCreate` | **CORE-3 confirmed**, raised to High |
| `voice.m4a` (AAC, major brand `isom`) | Opens in the black video player, with no audio card | **FMT-3 confirmed** |
| PPTX with a dark gradient master and `tx1 = lt1` | White slide; the title "Can you read me?" is invisible | **TXT-1 confirmed** |
| Editor: type, Back, then **Discard** in the dialog | The draft file is still in `files/drafts/` | **FEAT-3 confirmed** |
| Editor: type, then kill the app | "You have unsaved changes from earlier" with Restore on reopen | Draft recovery works |
| `dup.zip` (two `a.txt` entries) | "This file looks damaged" (the platform rejects duplicates), no crash | FMT-4 lowered to Low; the crash may still happen on older APIs |
| `slip.zip` (`../evil.txt` plus `ok.txt`) | Both entries listed, as designed | FMT-12 not reproduced on API 37 |
| `cp437.zip` (`café.txt`, no UTF-8 flag) | Correct name | FMT-5 still needs API 26 to 30 |
| One file each for 30 syntax grammars | All open, none crash | TXT-7 not reproduced; the missing test still stands |
| Markdown links | No crash. `file://` opens the system "Open with" sheet straight away; `https://` opens Chrome with no confirmation; `javascript:` and relative links are ignored; the remote image is not loaded | TXT-6 lowered to Low (no crash) |
| Corrupted PDF, DOCX and XLSX; 4 KB of random bytes; empty `.txt` | Error or empty states, no crash | Error handling works |
| Devanagari, CJK, Arabic and emoji text | Renders correctly | OK |
| 60 s H.264 MP4, rotation | Inconclusive: the emulator's decoder failed (Codec2 `BufferPool` error 4), and Folio correctly showed "Folio can't play this file." | FMT-2 still traced in code only |
| Published APK | SHA-256 matches the README; v2 signature only; signer `CN=Vishal Solanki`, cert SHA-256 `19bf7453…8fe218`; includes the dependency-metadata block | DIST-1 confirmed |

## Findings index

| ID | Severity | Status | Finding | Where |
|---|---|---|---|---|
| [CORE-3](#core-3) | High | Confirmed bug | `safeName` limits characters, not bytes: crash on shared text with a long non-ASCII subject, and silently lost copies | `core/files/LocalCopies.kt:21-26` |
| [FEAT-1](#feat-1) | High | Unverified assumption | "Save copy" after activity/process recreation writes empty (or unedited) text and deletes the draft | `feature/editor/EditorScreen.kt:105-109` |
| [FEAT-2](#feat-2) | High | Potential risk | Rename doesn't keep access to the new URI: renamed files stop opening after a reboot, and a second rename fails | `feature/home/HomeViewModel.kt:120-133` |
| [FMT-1](#fmt-1) | High | Confirmed bug | PDF pages with a tall or large aspect render an uncapped bitmap at 1x zoom, which crashes the app or leaves the page blank | `formats/pdf/PdfViewer.kt:327-333` |
| [CORE-1](#core-1) | Medium | Confirmed bug | A `file://` URI outside the FileProvider roots crashes the viewer (and Share from Recents) | `core/files/Sharing.kt:51-53` |
| [CORE-2](#core-2) | Medium | Confirmed bug | Recents de-duplication releases the persisted grant and keeps the weaker, temporary URI | `core/data/Library.kt:36-46` |
| [CORE-4](#core-4) | Medium | Unverified assumption | `Xml.parse` relies on "no DOCTYPE processing", but Android's KXmlParser still parses internal entity declarations | `core/files/Xml.kt:39-46` |
| [CORE-5](#core-5) | Medium | Potential risk | `RandomAccess.file` copies any size into the cache, without atomic write or cleanup on failure | `core/files/RandomAccess.kt:10-16` |
| [DIST-1](#dist-1) | Medium | Confirmed bug | The release APK carries Google's encrypted dependency-metadata block | `app/build.gradle.kts` |
| [DIST-2](#dist-2) | Medium | Confirmed | Release builds silently fall back to the debug key | `app/build.gradle.kts:97` |
| [FEAT-3](#feat-3) | Medium | Confirmed bug | "Discard" in the editor doesn't discard: the draft is written again as the screen closes | `feature/editor/EditorViewModel.kt:106-109` |
| [FEAT-4](#feat-4) | Medium | Confirmed bug | Edits typed while a save is in flight are marked as saved and lost on Back | `feature/editor/EditorViewModel.kt:88-93` |
| [FEAT-5](#feat-5) | Medium | Potential risk | An editor that hasn't been created yet is read as empty text (drafts, Restore) | `feature/editor/EditorScreen.kt:105` |
| [FEAT-6](#feat-6) | Medium | Confirmed bug | TalkBack isn't told whether switches, chips and toggles are on or selected (theme, paper, filters, settings) | `feature/settings/SettingsScreen.kt:162-181` |
| [FEAT-7](#feat-7) | Medium | Confirmed bug | Folder list never refreshes: after renaming a file from a folder, the old entry opens "no longer available" | `feature/folder/FolderViewModel.kt:41-46` |
| [FEAT-8](#feat-8) | Medium | Unverified assumption | Empty and error states can't scroll and their button rows don't wrap: on first launch in landscape or with large fonts, the only "Open file" button can be cut off | `core/designsystem/components/States.kt:49-53, 84-104` |
| [FMT-2](#fmt-2) | Medium | Confirmed bug | Rotating while a video or song is playing leaves it paused (API 28+) | `formats/media/MediaViewer.kt:91,119,135` |
| [FMT-3](#fmt-3) | Medium | Confirmed bug | `.m4a` files whose ftyp brand is not `M4A ` open in the video player | `core/files/MimeSniffer.kt:85-91` |
| [FMT-5](#fmt-5) | Medium | Potential risk | On older Android, ZIPs with non-UTF-8 (DOS code page) names show "damaged" instead of using the CP437 fallback | `formats/archive/ZipArchive.kt:30-49` |
| [FMT-6](#fmt-6) | Medium | Potential risk | Large or image-heavy DOCX can run the app out of memory on the main thread | `formats/docx/DocxViewer.kt:44-48` |
| [FMT-8](#fmt-8) | Medium | Confirmed bug | PDF page content is invisible to TalkBack | `formats/pdf/PdfViewer.kt:308` |
| [TXT-1](#txt-1) | Medium | Confirmed bug | Slides with a gradient or picture background render on white, so light theme text disappears | `formats/slides/PptxReader.kt:136-140` |
| [TXT-2](#txt-2) | Medium | Confirmed bug | Truncated reads decode a window cut mid-character, so the whole file falls back to Windows-1252 | `formats/sheet/SheetViewer.kt:81-86` |
| [TXT-3](#txt-3) | Medium | Confirmed bug | "Pretty view" re-formats JSON/XML on the main thread inside composition | `formats/text/TextViewer.kt:91` |
| [TXT-4](#txt-4) | Medium | Confirmed bug | Large or limit-hitting XLSX sheets are reported as "damaged"; big sheets can OOM | `formats/sheet/SheetViewer.kt:124` |
| [TXT-5](#txt-5) | Medium | Confirmed bug | The spreadsheet grid is not usable with TalkBack | `formats/sheet/SheetGrid.kt:174-186` |
| [TXT-8](#txt-8) | Medium | Potential risk | Markdown preview composes the whole document at once (non-lazy) | `formats/text/MarkdownViewer.kt:73-75` |
| [TXT-9](#txt-9) | Medium | Unverified assumption | Very long single lines (minified JSON/JS) with word wrap and TextMate | `formats/text/SoraText.kt:89-90,142` |
| [CORE-6](#core-6) | Low | Confirmed bug | Corrupt settings file: the read falls back, but the flow ends and every write throws | `AppContainer.kt:31` |
| [CORE-7](#core-7) | Low | Confirmed | `allowBackup="false"` without `dataExtractionRules` still allows device-to-device transfer on Android 12+ | `AndroidManifest.xml:20-29` |
| [CORE-8](#core-8) | Low | Confirmed bug | Removing a folder silently breaks every Recents row opened from inside it | `core/data/Library.kt:81-84` |
| [CORE-9](#core-9) | Low | Potential risk | Room upgrade path from v1.0.0 cannot be tested yet | `core/data/FolioDatabase.kt:8,15` |
| [CORE-10](#core-10) | Low | Unverified assumption | `ExternalOpenActivity` shares the app's default task affinity | `AndroidManifest.xml:45-48` |
| [CORE-11](#core-11) | Low | Potential risk | Legacy 8-bit and multibyte encodings are always shown as windows-1252 | `core/files/TextCodec.kt:45-47` |
| [CORE-12](#core-12) | Low | Potential risk | `cache/shared_text/` is never cleaned | `ExternalOpenActivity.kt:80` |
| [DIST-3](#dist-3) | Low | Confirmed | No continuous integration | `` |
| [DIST-4](#dist-4) | Low | Confirmed | No changelog or store metadata in the repository | `` |
| [DIST-5](#dist-5) | Low | Unverified assumption | Reproducible builds are unverified, and the APK checksum alone doesn't prove who signed it | `` |
| [FEAT-9](#feat-9) | Low | Confirmed bug | Home multi-select loses its selection on rotation (mode stays on with "0 selected") | `feature/home/HomeScreen.kt:113-115, 124` |
| [FEAT-10](#feat-10) | Low | Confirmed bug | `closeOnSave` is never reset, so a later normal Save closes the editor unexpectedly | `feature/editor/EditorScreen.kt:98, 119-124, 210` |
| [FEAT-11](#feat-11) | Low | Confirmed bug | Rename dialogs: Viewer shows a stale error when reopened; typed name lost on rotation | `feature/viewer/ViewerViewModel.kt:59-60, 107, 113` |
| [FEAT-12](#feat-12) | Low | Potential risk | Backing out of the Viewer quickly can drop the file from Recents (and leave an orphan copy) | `feature/viewer/ViewerViewModel.kt:73-75, 95, 135-160` |
| [FEAT-13](#feat-13) | Low | Potential risk | Shared-files list crashes if the same URI is shared twice | `feature/viewer/SharedFilesScreen.kt:40` |
| [FEAT-14](#feat-14) | Low | Potential risk | JSON/XML validation runs on the main thread inside Save | `feature/editor/EditorViewModel.kt:120` |
| [FEAT-15](#feat-15) | Low | Confirmed | UI strings are hardcoded; strings.xml holds only `app_name` | `res/values/strings.xml:1-4` |
| [FEAT-16](#feat-16) | Low | Confirmed | Extension badge text is below WCAG AA contrast in the light theme | `core/designsystem/Color.kt:116-128` |
| [FEAT-17](#feat-17) | Low | Confirmed bug | Missing semantics: section headings, an unlabeled font-size slider, page pill without an action | `feature/settings/SettingsScreen.kt:146-154` |
| [FEAT-18](#feat-18) | Low | Confirmed bug | Home search/filter/sort and folder sort reset after process death | `feature/home/HomeViewModel.kt:70-72` |
| [FMT-4](#fmt-4) | Low | Potential risk | A ZIP with duplicate entry names crashes the archive browser (duplicate LazyColumn key) | `formats/archive/ZipArchive.kt:63-86` |
| [FMT-7](#fmt-7) | Low | Confirmed bug | PDF documents in the middle of opening, and ZIP archives, leak native handles and file descriptors when the viewer leaves during load | `formats/pdf/PdfViewer.kt:115-118` |
| [FMT-9](#fmt-9) | Low | Confirmed bug | Printing a phone photo ignores EXIF orientation, so portrait photos print sideways | `formats/image/BitmapPrintAdapter.kt:71-79` |
| [FMT-10](#fmt-10) | Low | Confirmed bug | DOCX (and HTML) jump back to the top when paper changes or the phone rotates | `formats/docx/DocxViewer.kt:79` |
| [FMT-11](#fmt-11) | Low | Unverified assumption | Video can go dark when the screen times out during playback | `formats/media/MediaViewer.kt:158-170` |
| [FMT-12](#fmt-12) | Low | Unverified assumption | ZIPs containing a `../` or absolute entry probably show "damaged" on Android 14+, not the planned "listed but blocked" row | `formats/archive/ZipArchive.kt:33-45,78-83` |
| [FMT-13](#fmt-13) | Low | Confirmed bug | AVI, MPEG-PS, FLV, MKA and M4B fall back to the Unknown viewer even though bundled Media3 extractors can play them | `core/model/FormatInfo.kt:91-102` |
| [FMT-14](#fmt-14) | Low | Potential risk | The archive "Opening…" dialog cannot be cancelled during large extractions | `formats/archive/ArchiveViewer.kt:113-116,166-168` |
| [FMT-15](#fmt-15) | Low | Unverified assumption | Printing a password-protected PDF sends the still-encrypted bytes to the print spooler | `formats/pdf/PdfViewer.kt:103` |
| [TXT-6](#txt-6) | Low | Unverified assumption | Markdown links are opened straight by the system UriHandler; `file://` links likely crash the app | `formats/text/MarkdownViewer.kt:103-118` |
| [TXT-7](#txt-7) | Low | Unverified assumption | Grammar loading and compile failures are uncaught crashes, and no test loads the app's own 27 grammars | `formats/text/SoraText.kt:63` |
| [TXT-10](#txt-10) | Low | Confirmed bug | Elapsed-time formats (`[h]:mm:ss`, built-in 46) wrap at 24 hours | `formats/sheet/XlsxReader.kt:126-141` |
| [TXT-11](#txt-11) | Low | Confirmed bug | East Asian built-in date formats (ids 27–36, 50–58) show raw serial numbers | `formats/sheet/XlsxReader.kt:212-220` |
| [TXT-12](#txt-12) | Low | Confirmed bug | Hidden and veryHidden sheets (and chartsheets) appear as normal tabs; the first tab may be a hidden one | `formats/sheet/XlsxReader.kt:161-166` |
| [TXT-13](#txt-13) | Low | Confirmed bug | The "Showing the first N rows" banner appears when nothing is missing, and when columns were cut | `formats/sheet/XlsxReader.kt:67` |
| [TXT-14](#txt-14) | Low | Confirmed bug | Cropped pictures in slides are drawn stretched | `formats/slides/PptxReader.kt:155-160` |
| [TXT-15](#txt-15) | Low | Potential risk | PptxReader keeps every parsed XML tree until the viewer closes | `formats/slides/PptxReader.kt:73-77` |
| [TXT-16](#txt-16) | Low | Confirmed bug | "Load more" reloads and recreates the editor, jumping back to the top | `formats/text/TextViewer.kt:68-71,104` |
| [TXT-17](#txt-17) | Low | Confirmed bug | Markdown and HTML previews are silently cut at 5 MB | `formats/text/MarkdownViewer.kt:73-87` |
| [TXT-18](#txt-18) | Low | Confirmed bug | Text printing: silent 2 MB cut, cancellation never answered, stale layout can win; Markdown prints raw source | `formats/text/TextPrintAdapter.kt:32,52` |
| [TXT-19](#txt-19) | Low | Confirmed bug | XML "Pretty view" drops the spaces around entity references | `formats/text/Pretty.kt:39,46` |
| [TXT-20](#txt-20) | Low | Confirmed bug | Syntax highlighting covers 27 grammars, not the "40+ languages" the README claims; name-only files are never highlighted | `formats/text/TextMate.kt:42-74` |

## Detailed findings

### Distribution and release

<a id="dist-1"></a>
#### DIST-1 — The release APK carries Google's encrypted dependency-metadata block
- **Severity:** Medium
- **Status:** Confirmed bug (found in the published v1.0.0 APK)
- **Where:** `app/build.gradle.kts` — `android { }` has no `dependenciesInfo { }` block
- **Problem:** By default AGP adds a "dependency metadata" block to the APK Signing Block. It is encrypted to a Google key and only Google can read it. The published `Folio-1.0.0.apk` (SHA-256 `9d4ce51d…0d9f17`, which is the same file as the local `app/build/outputs/apk/release/app-release.apk`) has the IDs `0x7109871a` (v2 signature), `0x504b4453` (dependency metadata) and `0x42726577` (padding). F-Droid's scanner rejects APKs that contain this block. It's also an opaque blob in an app that markets itself as having nothing hidden, and it does nothing outside Google Play.
- **Reproduce:** Parse the APK Signing Block of the release APK and list the block IDs (the Python snippet used in this audit is in TEST_MATRIX.md, section R).
- **Fix:** `android { dependenciesInfo { includeInApk = false; includeInBundle = false } }`. Keep `includeInBundle = true` if a Play AAB is ever uploaded.
- **Verify with:** Rebuild the release APK and list the signing-block IDs again. Only `0x7109871a` and `0x42726577` should remain. This is added to RELEASE_CHECKLIST.md.

<a id="dist-2"></a>
#### DIST-2 — Release builds silently fall back to the debug key
- **Severity:** Medium
- **Status:** Confirmed (build configuration)
- **Where:** `app/build.gradle.kts:97` — `signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.getByName("debug")`
- **Problem:** If `keystore.properties` is missing, misnamed or unreadable (new laptop, fresh clone, a CI runner), `assembleRelease` still succeeds and signs with the debug key, with no warning. An APK signed that way cannot update the existing install: Android reports `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, Obtainium refuses it, and users would have to uninstall and lose Recents and settings. This is the most dangerous mistake for a sideloaded app with no Play App Signing safety net (docs/release.md: "a lost key means users must uninstall to update").
- **Reproduce:** Rename `keystore.properties`, run `./gradlew assembleRelease`, then `apksigner verify --print-certs` on the output: the DN is `CN=Android Debug`.
- **Fix:** Fail the release signing config when `keystore.properties` is absent, unless a property such as `-Pfolio.debugSignedRelease=true` is passed for local testing. Also add a checklist step that compares the signer's certificate SHA-256 with the expected release fingerprint, `19bf74536960acb7421491ba83096bf9f68ac68ea7ad5547810c557c328fe218`.
- **Verify with:** Run `assembleRelease` without the keystore: the build fails with a clear message. With the keystore: `apksigner verify --print-certs` shows the release fingerprint.

<a id="dist-3"></a>
#### DIST-3 — No continuous integration
- **Severity:** Low
- **Status:** Confirmed
- **Where:** The repo has no `.github/workflows/`. `docs/release.md` step 2 runs tests and lint by hand.
- **Problem:** Unit tests (85), lint, licensee and the build only run when the maintainer remembers to run them. Outside contributors get no automatic feedback, and a broken `main` can be tagged.
- **Fix:** One GitHub Actions workflow on push and pull request: `./gradlew testDebugUnitTest lintDebug :app:licensee assembleDebug` on JDK 17. It needs no secrets and does no release signing.
- **Verify with:** The workflow is green on a pull request, and a deliberately failing test turns it red.

<a id="dist-4"></a>
#### DIST-4 — No changelog or store metadata in the repository
- **Severity:** Low
- **Status:** Confirmed
- **Where:** Repo root and `docs/`. There is no `CHANGELOG.md` and no `fastlane/metadata/android/`. The changelog currently lives in GitHub release notes and in the separate website repo (`src/data/apps.ts`, per docs/release.md step 5).
- **Problem:** F-Droid, IzzyOnDroid and Obtainium users, and contributors, cannot see what changed between versions from the repo itself. The two changelog copies can drift apart.
- **Fix:** Add a `CHANGELOG.md` (Keep a Changelog format) as the single source. For F-Droid or IzzyOnDroid, also add `fastlane/metadata/android/en-US/` with `title.txt`, `short_description.txt`, `full_description.txt`, `changelogs/<versionCode>.txt` and screenshots.
- **Verify with:** A release-checklist item that the changelog entry exists for the new `versionCode`.

<a id="dist-5"></a>
#### DIST-5 — Reproducible builds are unverified, and the APK checksum alone doesn't prove who signed it
- **Severity:** Low
- **Status:** Unverified assumption
- **Where:** Release process (`docs/release.md`); `app/src/release/generated/baselineProfiles/` (committed baseline and startup profiles); APK signed with v2 only.
- **Problem:** Nobody has built the same commit twice and compared the results. Reproducibility is what lets F-Droid publish the developer-signed APK, so that F-Droid and GitHub installs can update each other. Separately, the README publishes the APK's SHA-256, which proves one download is intact but says nothing about future updates. Publishing the signing certificate fingerprint lets users check any version, for example with `apksigner` or the AppVerifier app.
- **Fix:** Build the release twice from clean clones on different machines or paths and compare with `diffoscope` (or `apksigcopier compare`). Fix any differences. Publish the certificate SHA-256 (`19bf7453…8fe218`) in the README and in each release's notes.
- **Verify with:** Two clean builds give identical unsigned APKs.

### App shell, data and file access

<a id="core-1"></a>
#### CORE-1 — A `file://` URI outside the FileProvider roots crashes the viewer (and Share from Recents)
- **Severity:** Medium
- **Status:** Confirmed bug
- **Runtime check:** Confirmed on the emulator with `file:///system/etc/hosts`: `IllegalArgumentException: Failed to find configured root`.
- **Where:** `core/files/Sharing.kt:51-53` — `Sharing.outgoingUri`; reached from `Sharing.browserFor` (`Sharing.kt:112`), which `feature/viewer/ViewerScreen.kt:108-110` calls automatically in a `produceState` for every opened file. Also reached from `Sharing.openWith` (`Sharing.kt:67`), `Sharing.share` (`Sharing.kt:75`) and Home's share (`feature/home/HomeScreen.kt:179`). Entry: `AndroidManifest.xml:71,98` (`file` scheme on the exported `ExternalOpenActivity`), `ExternalOpenActivity.kt:69`, `FileResolver.kt:41-44`.
- **Problem:** `outgoingUri` calls `FileProvider.getUriForFile` for any `file://` URI. That throws `IllegalArgumentException` ("Failed to find configured root") unless the path is under `cache/` or `files/recent_copies/` (`res/xml/file_paths.xml:3-4`). `ExternalOpenActivity` accepts `file://` from any app, and `FileResolver` opens any path Folio's own UID can read. On Android 8+ without storage permission, that is mostly Folio's own private data (`files/last-crash.txt`, `files/drafts/*`, `databases/*`) and world-readable system files. For a text, HTML, PDF or image MIME type, the `browserFor` check runs as soon as the viewer shows, throws inside `produceState`, and crashes the app. The `file://` path is also saved in Recents (`ViewerViewModel.record`, `uri.scheme != content` → no copy, row still written), so each later open or Share of that row crashes again. A second, smaller effect: any app can make Folio display its own private files (a confused deputy), and one tap on Share or Open with would grant them to another app wherever FileProvider covers the path.
- **Reproduce:** `adb shell am start -a android.intent.action.VIEW -d file:///system/etc/hosts -t text/plain -n online.vishalsolanki.folio/com.folio.app.ExternalOpenActivity` → the text viewer appears, then the app crashes with `IllegalArgumentException: Failed to find configured root`. Another trigger is `file:///data/data/online.vishalsolanki.folio/files/last-crash.txt` after any crash.
- **Fix:** (1) In `outgoingUri`, return `Uri?` (`runCatching { getUriForFile(...) }.getOrNull()`). `browserFor` then returns null, and `share`/`openWith` show a toast instead of crashing. (2) In `ExternalOpenActivity.startDestination`, refuse external `file://` URIs whose canonical path is under `applicationInfo.dataDir`, and content URIs whose authority is `"$packageName.files"`. The existing "shared this file in an old way" message (`ViewerViewModel.kt:83-84`) can cover them.
- **Verify with:** Instrumented test in `ExternalOpenTest`: launch VIEW with `Uri.fromFile(File(context.filesDir, "x.txt"))`, assert no crash and the "can't read" message. Unit or instrumented test: `outgoingUri` on `/system/etc/hosts` returns null.

<a id="core-2"></a>
#### CORE-2 — Recents de-duplication releases the persisted grant and keeps the weaker, temporary URI
- **Severity:** Medium
- **Status:** Confirmed bug (the current test locks this behaviour in)
- **Where:** `core/data/Library.kt:36-46` — `Library.recordOpen` → `forget` (`Library.kt:102-106`); test `androidTest/.../core/data/LibraryTest.kt:57-66`
- **Problem:** When a file arrives under a new URI with the same name, size and MIME type as an existing row, every older match is forgotten. `forget` releases its persisted permission and deletes its private copy, and the new row has `persisted = file.persisted` (false for a temporary grant). Example: the user picks `Report.pdf` with Open file (persisted grant, possibly pinned), then opens the same file from WhatsApp or from the Files app's Open with (temporary grant). Folio gives up its durable access and keeps only the temporary URI. That URI stops working once the sender's task ends. Files over 25 MB, or any file with "Keep copies" off, get no copy (`ViewerViewModel.kt:142-143`), so the pinned row becomes "no longer available" for good. Because matching uses only name, size and MIME type (the comment at `Library.kt:35` admits this shortcut), two different files that look identical also wipe each other's grant and copy.
- **Reproduce:** Open `X.pdf` via Home → Open file (persisted). Pin it. Share the same `X.pdf` (over 25 MB, or with Keep copies off) to Folio from Files or WhatsApp. Close the sender, reopen Folio, tap the row → "This file is no longer available". `LibraryTest.sameFileUnderAnotherUriReplacesOldRowAndKeepsPin` asserts this exact release (`released == ["content://downloads/msf:24"]`).
- **Fix:** In `recordOpen`, when a duplicate is `persisted` and the new file is not, keep the duplicate row and update its `lastOpenedAt`, instead of forgetting it (or skip de-duplication for persisted duplicates). Release a grant only when the surviving row also holds one.
- **Verify with:** Update `LibraryTest`: persisted row A plus a non-persisted duplicate B → `released` is empty and the surviving row is `persisted`.

<a id="core-3"></a>
#### CORE-3 — `safeName` limits characters, not bytes: crash on shared text with a long non-ASCII subject, and silently lost copies
- **Severity:** High (was Medium before the emulator check)
- **Status:** Confirmed bug
- **Runtime check:** Confirmed on the emulator: a 100-character CJK subject crashes `ExternalOpenActivity` with `ENAMETOOLONG`. Raised to High because long non-Latin page titles are an everyday share.
- **Where:** `core/files/LocalCopies.kt:21-26` — `LocalCopies.safeName` (`.take(120)`); used at `ExternalOpenActivity.kt:79-81` (`sharedTextFile`) and `LocalCopies.kt:49-50` (`store`)
- **Problem:** Linux file names are limited to 255 bytes. `safeName` keeps 120 UTF-16 chars, which is up to 360 bytes for Devanagari, CJK or Cyrillic text and up to 480 bytes for emoji. In `sharedTextFile`, `File(dir, "$name.txt").writeText(text)` then throws `FileNotFoundException (ENAMETOOLONG)` in `onCreate`, with nothing catching it, so `ExternalOpenActivity` crashes. In `LocalCopies.store`, `renameTo` fails, so the method returns null and deletes the copy (`LocalCopies.kt:50-55`). A WhatsApp document with a long Hindi or Chinese name then gets no private copy and disappears from Recents once WhatsApp's grant ends. `take(120)` can also split a surrogate pair.
- **Reproduce:** `adb shell am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT hi --es android.intent.extra.SUBJECT "<100 Devanagari or Chinese characters>" -n online.vishalsolanki.folio/com.folio.app.ExternalOpenActivity` → crash.
- **Fix:** Truncate by UTF-8 bytes (for example, drop trailing code points until `toByteArray().size <= 200`, leaving room for an extension or suffix), and keep the extension when cutting. Also wrap the write in `sharedTextFile` with `runCatching` and fall back to `"Shared text"`.
- **Verify with:** Unit test in `LocalCopiesTest`: `safeName("字".repeat(200)).toByteArray().size <= 255`, and `store` with that name returns non-null. Instrumented test in `ExternalOpenTest` with a long CJK subject.

<a id="core-4"></a>
#### CORE-4 — `Xml.parse` relies on "no DOCTYPE processing", but Android's KXmlParser still parses internal entity declarations
- **Severity:** Medium
- **Status:** Unverified assumption
- **Where:** `core/files/Xml.kt:39-46` — `Xml.parse` (used by `formats/slides/PptxReader.kt:76`)
- **Problem:** The KDoc says there is no DOCTYPE processing and no entity expansion. The only guard is `setFeature(FEATURE_PROCESS_DOCDECL, false)`, wrapped in `runCatching`. As far as I know, libcore's KXmlParser still reads an internal subset (`<!DOCTYPE x [<!ENTITY a "...">]>`) and expands those entities in text, whatever that feature is set to. `LimitedStream` caps input bytes, not expanded output, so a "billion laughs" slide XML of a few KB could expand to hundreds of MB. External `SYSTEM` entities are not fetched, so classic XXE file or network reads are not a concern. A related point: a legitimate 50 MB part becomes an `XNode` tree several times that size. `SlidesViewer` catches OOM via `runCatching`, but OOM can surface on another thread first.
- **Reproduce:** Build a PPTX whose `ppt/slides/slide1.xml` begins with a nested 10x10 `lol` entity DOCTYPE and uses `&lol9;` in an `<a:t>`. Open it on a device and watch memory or time.
- **Fix:** Before the main loop, read with `parser.nextToken()` until the first `START_TAG`, and throw if a `DOCDECL` event appears. OOXML parts never contain a DOCTYPE. Then continue with `next()`; mixing the two is allowed. Apply the same guard in `XlsxReader` (formats scope).
- **Verify with:** Instrumented test, which needs the real Android parser: `Xml.parse` of a billion-laughs document throws within 1 s. The JVM unit tests use kxml2, which behaves differently.

<a id="core-5"></a>
#### CORE-5 — `RandomAccess.file` copies any size into the cache, without atomic write or cleanup on failure
- **Severity:** Medium
- **Status:** Potential risk
- **Where:** `core/files/RandomAccess.kt:10-16` — `RandomAccess.file` (callers: `ArchiveViewer.kt:73`, `SheetViewer.kt:96`, `SlidesViewer.kt:80`). The same pattern is in `PdfDocumentHandle.kt:75-77`.
- **Problem:** Every ZIP, XLSX or PPTX opened from a content URI is fully copied to `cache/open/<sha1>.<ext>`, with no size limit and no free-space check. A multi-GB ZIP from a file manager fills the disk. If the copy fails partway (disk full, provider error), the partial file stays. `CacheCleaner` only deletes it more than a day later, at the next process start (`CacheCleaner.kt:7-13`). The copy is written in place (`copy.outputStream()` truncates the same inode), so a second open of the same URI rewrites the file while an earlier `ZipFile` may still read it. This happens if a previous `produceState` coroutine is still running after a rotation, or when the same file is open in two tasks.
- **Reproduce:** Open a 3-4 GB ZIP from Files with Folio on a device with less free space than that → storage fills up, then the open fails, and the partial file stays in `cache/open/`.
- **Fix:** Check `OpenableColumns.SIZE` (or the PFD `statSize`) against a cap and against `StorageManager.getAllocatableBytes`. Copy to a temp file and rename it into place, and delete the temp file in `finally` on failure.
- **Verify with:** Unit test using a fake stream that throws halfway: no file is left under `open/`. Manual test with a large ZIP.

<a id="core-6"></a>
#### CORE-6 — Corrupt settings file: the read falls back, but the flow ends and every write throws
- **Severity:** Low
- **Status:** Confirmed bug (by DataStore semantics; needs a corrupt file to trigger)
- **Where:** `AppContainer.kt:31` (`preferencesDataStore(name = "settings")`, no `corruptionHandler`); `core/data/SettingsRepository.kt:41-42`
- **Problem:** `CorruptionException` is an `IOException`, so `.catch` emits defaults. After `catch`, the flow completes, so later theme or paper changes never reach `FolioRoot`. Without a `ReplaceFileCorruptionHandler`, each `store.edit` reads the corrupt file again and throws `CorruptionException` into the calling coroutine. That crashes the Settings screen on any toggle, unless the feature code catches it.
- **Reproduce:** Write garbage bytes to `files/datastore/settings.preferences_pb` (debug build, `run-as`), launch, change the theme → crash.
- **Fix:** `preferencesDataStore(name = "settings", corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() })`.
- **Verify with:** Unit test in `SettingsRepositoryTest`: create the store with that handler over a file of garbage bytes; `setTheme` succeeds and `settings.first()` reflects it.

<a id="core-7"></a>
#### CORE-7 — `allowBackup="false"` without `dataExtractionRules` still allows device-to-device transfer on Android 12+
- **Severity:** Low
- **Status:** Confirmed (configuration)
- **Where:** `AndroidManifest.xml:20-29` (no `android:dataExtractionRules`, no `fullBackupContent`; `res/xml` holds only `file_paths.xml`)
- **Problem:** With targetSdk 37, `allowBackup=false` turns off cloud backup but not device-to-device migration. On a phone transfer, Folio's private data moves to the new device: the Recents DB (file names, source apps, URIs), `files/recent_copies/` (up to 300 MB of copies of documents received in WhatsApp and Gmail), editor drafts and `last-crash.txt`. The persisted URI grants do not move, so most rows break on the new device anyway. This conflicts with the evident intent of `allowBackup=false` and the privacy promise.
- **Reproduce:** Run a D2D transfer (or `adb shell bmgr` D2D test mode) from an Android 12+ device with Folio Recents populated, then inspect the new device.
- **Fix:** Add `android:dataExtractionRules="@xml/data_extraction_rules"`, with `<cloud-backup>` and `<device-transfer>` each excluding the `root`, `file`, `database` and `sharedpref` domains. Alternatively, include only `datastore/settings.preferences_pb` if moving settings is wanted.
- **Verify with:** Manual check, or `adb shell bmgr backupnow` with the D2D transport, to confirm nothing (or only settings) is transferred.

<a id="core-8"></a>
#### CORE-8 — Removing a folder silently breaks every Recents row opened from inside it
- **Severity:** Low
- **Status:** Confirmed bug
- **Where:** `core/data/Library.kt:81-84` — `Library.removeFolder`; together with `ViewerViewModel.kt:141` (no private copy for `isInsideTree` URIs)
- **Problem:** Files opened through an added folder are recorded under tree document URIs with no copy and `persisted = false`, because they rely on the folder's grant. `removeFolder` releases that grant but leaves those rows. They stay in Recents (pinned ones too) and always show "This file is no longer available". Nothing removes them except the 500-row trim, which skips pinned rows.
- **Reproduce:** Add a folder, open and pin a file from it, remove the folder, tap the pinned row.
- **Fix:** In `removeFolder`, also `forget` rows whose URI starts with the tree URI's document prefix (same authority, `tree/<treeDocId>/`). Alternatively, warn in the remove dialog that its files will leave Recents.
- **Verify with:** `LibraryTest`: add a folder, record `content://a/tree/x/document/y`, remove the folder, assert the row is gone.

<a id="core-9"></a>
#### CORE-9 — Room upgrade path from v1.0.0 cannot be tested yet
- **Severity:** Low
- **Status:** Potential risk
- **Where:** `core/data/FolioDatabase.kt:8,15`; `app/build.gradle.kts:120-122` (schema location), `163-165` (no `room-testing`); `app/schemas/.../1.json`
- **Problem:** Things are in good shape today: version 1, `exportSchema = true`, `1.json` committed and matching the entities, and no destructive fallback, which is right because Recents holds grant bookkeeping. But `room-testing` is not a dependency, and `schemas/` is not added to androidTest assets, so `MigrationTestHelper` cannot run. The first schema change in 1.1 without a tested `Migration(1, 2)` would crash every upgrading user at the first DB access (`IllegalStateException: A migration from 1 to 2 was required`).
- **Reproduce:** N/A until the version is bumped.
- **Fix:** Add `androidTestImplementation(libs.room.testing)` and `sourceSets["androidTest"].assets.srcDir("$projectDir/schemas")`, and add a migration test template now. For simple column additions, prefer `@AutoMigration`.
- **Verify with:** Instrumented `MigrationTestHelper` test that opens v1 from `1.json` and runs all migrations.

<a id="core-10"></a>
#### CORE-10 — `ExternalOpenActivity` shares the app's default task affinity
- **Severity:** Low
- **Status:** Unverified assumption
- **Where:** `AndroidManifest.xml:45-48`; comment at `ExternalOpenActivity.kt:18-21`
- **Problem:** The design says Open with stacks on the sender's task, so Back returns to the sender. That holds only when the sender does not set `FLAG_ACTIVITY_NEW_TASK`. Download notifications, some file managers and browser download UIs do set it. With the default affinity, the activity then joins Folio's existing main task, on top of Home. Back lands on Folio's Home instead of the sender, and Folio's Recents card shows the viewer.
- **Reproduce:** Open Folio (Home), press Home, then open a downloaded PDF from the Downloads notification and choose Folio → press Back.
- **Fix:** `android:taskAffinity=""` on `ExternalOpenActivity` (optionally with `excludeFromRecents` and `autoRemoveFromRecents`), the usual setup for viewer activities.
- **Verify with:** Manual test with `adb shell am start -f 0x10000000 -a VIEW -d <content uri>` while MainActivity is in the background; check `dumpsys activity activities` for the task.

<a id="core-11"></a>
#### CORE-11 — Legacy 8-bit and multibyte encodings are always shown as windows-1252
- **Severity:** Low
- **Status:** Potential risk
- **Where:** `core/files/TextCodec.kt:45-47` — `TextCodec.decode`; `MimeSniffer.kt:131-148` (`looksLikeText` accepts only UTF-8 or a UTF-16 BOM)
- **Problem:** Text that is not valid UTF-8 (Windows-1251 Cyrillic, GBK, Shift-JIS, ISCII) is decoded as windows-1252 and shown read-only as mojibake. Without a known text extension, the same file is not even detected as text and goes to the Unknown viewer. It is safe, since there is no lossy write-back, but such files are unreadable.
- **Reproduce:** Open a `.txt` saved as Windows-1251 ("Привет") → shows `Ïðèâåò`.
- **Fix:** Optional. Try the platform default charset or a small candidate list (windows-1251, GBK, Shift_JIS) with a strict decoder before falling back to windows-1252, and keep `editable = false` for all of them.
- **Verify with:** `TextCodecTest` with Windows-1251 bytes, asserting readable Cyrillic and `editable == false`.

<a id="core-12"></a>
#### CORE-12 — `cache/shared_text/` is never cleaned
- **Severity:** Low
- **Status:** Potential risk
- **Where:** `ExternalOpenActivity.kt:80`; `core/files/CacheCleaner.kt:10` (cleans only `archive` and `open`)
- **Problem:** Every distinct piece of shared text leaves a folder and file in `cache/shared_text/` until Android trims the cache. Each is small (binder-limited, under 1 MB), but they pile up and hold message contents the user shared, which matters for a privacy-first app.
- **Reproduce:** Share text to Folio several times, then inspect `cache/shared_text/`.
- **Fix:** Add `"shared_text"` to the list in `CacheCleaner.clean`. The path is content-hashed, so re-creating the activity within the day still finds it.
- **Verify with:** Extend `CacheCleanerTest` with an old `shared_text/<hash>/x.txt` and assert it is removed.

### Screens, editor and design system

<a id="feat-1"></a>
#### FEAT-1 — "Save copy" after activity/process recreation writes empty (or unedited) text and deletes the draft
- **Severity:** High
- **Status:** Unverified assumption (I traced the code path; whether the result arrives this way needs a runtime check)
- **Where:** `feature/editor/EditorScreen.kt:105-109` (`text()`, `saveAs` callback); `feature/editor/EditorViewModel.kt:58` (`liveText` kept only in memory), `:149-163` (`saveCopy` deletes the draft)
- **Problem:** The system "Save as" picker is a separate activity. If Folio's activity is destroyed while the picker is open (low memory, or "Don't keep activities"), a new EditorViewModel is created and `liveText` is null. When ReadyEditor composes again, `rememberLauncherForActivityResult` registers and the pending result is delivered straight away. At that point `editor` is still null: `settings` starts at `null` (line 93), so line 166 shows a skeleton, and `SoraText` waits for TextMate to load (SoraText.kt:63-66). So `text()` returns `""` (line 105). `vm.saveCopy(uri, "")` writes an empty file and then runs `drafts.delete(key)` (line 163), which deletes the draft that ON_STOP had just saved. Even if the editor were already created, it would hold the original file text, because `liveText` is null. Either way the user's edits are gone. HomeScreen's create flow handles this case (`pendingCreate` is rememberSaveable, HomeScreen.kt:119-120). The editor does not.
- **Reproduce:** Turn on Developer options → Don't keep activities. Open a file that Folio can't write (one received through Share, so the editor shows "Save copy"). Tap Edit, type some text, tap Save copy, pick a folder, tap Save. Open the new copy: it is empty or unedited. Reopen the original in the editor: no "unsaved changes" banner appears.
- **Fix:** Never use the live editor text in an activity-result callback. Keep the text to save in the ViewModel when the picker launches (`vm.prepareSaveCopy(text())`, stored in SavedStateHandle or written as the draft). In the callback, read it from the draft or SavedStateHandle. Also make `text()` fall back to `vm.liveText ?: s.doc.text`, never `""` (see FEAT-5).
- **Verify with:** An instrumented test using `ActivityScenario.recreate()` with a stubbed `CreateDocument` result (ActivityResultRegistry test double). Assert that the copy's bytes equal the edited text and that the draft still exists until the copy succeeds.

<a id="feat-2"></a>
#### FEAT-2 — Rename doesn't keep access to the new URI: renamed files stop opening after a reboot, and a second rename fails
- **Severity:** High
- **Status:** Potential risk (depends on the provider: affects providers whose document ID changes on rename, such as ExternalStorageProvider "Internal storage"; not msf:-ID Downloads)
- **Where:** `feature/home/HomeViewModel.kt:120-133` — `rename`; `feature/viewer/ViewerViewModel.kt:105-124` — `rename`; related: `core/files/UriAccess.kt:27-28, 55-56`, `core/files/Sharing.kt:126-130`
- **Problem:** `DocumentsContract.renameDocument` returns a new URI when the document ID changes. The provider revokes grants on the old ID and gives only a temporary grant on the new one. Neither rename path calls `UriAccess.persist(context, newUri)`. `library.rename` moves the row to `newUri` but leaves `persisted = true`, and persisted rows never get a local copy (ViewerViewModel.kt:141). Effects:
  1. Right away: `UriAccess.canRename(newUri)` is false (`isPersisted` checks the exact URI). The Viewer's More sheet drops Rename, but Home still offers it (HomeScreen.kt:509 gates on `row.persisted`), and a second rename fails with "This file's storage doesn't allow renaming it."
  2. After a reboot, the temporary grant is gone and the Recents row opens to "This file is no longer available."
  3. ViewerViewModel keeps `route.uri` (line 47) as the old URI. After process death the Viewer reopens the old URI, and `record()` (line 137) would record under it.
- **Reproduce:** Home → Open file → pick a .txt under Internal storage (not the Downloads root). Back. ⋮ → Rename → "a2.txt". ⋮ → Rename again → error. Optional: reboot the emulator and tap the row.
- **Fix:** After a successful rename, when the old URI was persisted, call `UriAccess.persist(context, newUri)` in both ViewModels (or once inside `Sharing.renameFile`, which both use). Then pass the real `persisted` value to `library.rename`. In ViewerViewModel, also write the new URI into the SavedStateHandle so restore uses it.
- **Verify with:** An instrumented test on a temp document from ExternalStorageProvider (or the emulator steps above): after rename, `contentResolver.persistedUriPermissions` contains `newUri`, and a second rename succeeds.

<a id="feat-3"></a>
#### FEAT-3 — "Discard" in the editor doesn't discard: the draft is written again as the screen closes
- **Severity:** Medium
- **Status:** Confirmed bug (code path traced; confirm on emulator)
- **Runtime check:** Confirmed on the emulator: the draft file is still in `files/drafts/` after Discard.
- **Where:** `feature/editor/EditorViewModel.kt:106-109` — `discard()`; `feature/editor/EditorScreen.kt:133` (`LifecycleEventEffect(ON_STOP)`), `:214`
- **Problem:** `discard()` deletes the draft on IO but leaves `dirty = true`. `onClose()` → `popBackStack()` moves the popped NavBackStackEntry to CREATED synchronously, while its composition stays alive during the exit animation. The ON_STOP observer at line 133 therefore fires, sees `state.dirty == true`, and calls `vm.writeDraft(text())`. That saves the discarded text again, racing the delete on Dispatchers.IO. The next time the file is edited, the user sees "You have unsaved changes from earlier", and Restore brings back text they discarded.
- **Reproduce:** Open a .txt → Edit → type "zzz" → Back → Discard. Open the same file → Edit. The draft banner appears.
- **Fix:** In `discard()`, set `ready { it.copy(dirty = false) }` before deleting. Better still, add a `closing` flag that stops writeDraft once discard or close starts.
- **Verify with:** A Compose UI test: edit, Back, Discard, then call `Drafts(filesDir/drafts).load(uri)` and assert null. Also re-enter the editor and assert there is no "Restore" node.

<a id="feat-4"></a>
#### FEAT-4 — Edits typed while a save is in flight are marked as saved and lost on Back
- **Severity:** Medium
- **Status:** Confirmed bug (the window is as long as the save; larger on big files or slow providers)
- **Where:** `feature/editor/EditorViewModel.kt:88-93` (`updateText`/`markDirty` do nothing while already dirty), `:130-138` (clears `dirty` unconditionally on success); `feature/editor/EditorScreen.kt:141`
- **Problem:** `save()` takes a snapshot of `text`, then writes on IO while `dirty` is still true. Keystrokes during the write call `markDirty()`, which does nothing because `dirty` is already true. On success the state becomes `dirty = false`, even though the editor now holds unsaved characters. The draft is deleted (line 137), and the 5 s draft loop and the debounced `updateText` both skip because `dirty` is false. `close()` (line 141) then leaves without asking, and those characters are lost.
- **Reproduce:** Edit a ~4 MB text file. Tap Save and immediately type a character. Wait for "Saved", then press Back. The editor closes without the Discard dialog, and the character is not in the file.
- **Fix:** Count edits with a revision counter (bump it in `onChange`). `save()` records the revision it saved, and on success sets `dirty = currentRevision != savedRevision`. The same applies to `saveCopy`.
- **Verify with:** A ViewModel unit test with a fake slow `FileSaver`: call save, then call `markDirty()` while saving, complete the save, and assert `dirty == true`.

<a id="feat-5"></a>
#### FEAT-5 — An editor that hasn't been created yet is read as empty text (drafts, Restore)
- **Severity:** Medium
- **Status:** Potential risk (needs timing; the result is data loss)
- **Where:** `feature/editor/EditorScreen.kt:105` (`text()` returns `""` when `editor == null`), `:127-133`, `:161`; `EditorViewModel.kt:101-104`
- **Problem:** After any recreation, `editor` is null until settings emit and TextMate loads. During that time:
  - (a) If ON_STOP fires (rotate and press Home quickly, or a second rotation) while `state.dirty`, then `writeDraft("")` sets `liveText = ""` and saves an empty draft. The next composition seeds the editor from `liveText` (line 104), so the document appears blank and dirty, and a Save empties the file.
  - (b) Tapping Restore on the draft banner before the editor exists: `editor?.setText(draft)` does nothing, but `dismissEarlierDraft(restored = true)` hides the banner. The editor then shows the original text (`editorInitialText` was computed earlier), and the 5 s loop overwrites the draft with the original text.
- **Reproduce:** (a) Edit, rotate, then press Home within about 100 ms (hard to do by hand; you can test it by delaying the settings flow in a debug build). (b) Open a file that has a draft on a slow emulator and tap Restore as soon as the banner appears.
- **Fix:** `fun text() = editor?.text?.toString() ?: vm.liveText ?: s.doc.text`. Skip `writeDraft` when `editor == null`. Disable the banner buttons until `editor != null`, or make Restore set `liveText` and re-key `editorInitialText`.
- **Verify with:** A Compose UI test with a delayed settings flow: mark dirty, send ON_STOP, and assert the draft is not empty.

<a id="feat-6"></a>
#### FEAT-6 — TalkBack isn't told whether switches, chips and toggles are on or selected (theme, paper, filters, settings)
- **Severity:** Medium
- **Status:** Confirmed bug
- **Where:** `feature/settings/SettingsScreen.kt:162-181` — `SwitchRow`; `core/designsystem/components/Bars.kt:126-137` — `FilterChipsRow`; `feature/viewer/ViewerSheets.kt:78-83` (More-sheet toggles)
- **Problem:**
  - `SwitchRow` uses `clickable(role = Role.Switch)` and a `Switch(onCheckedChange = null)`. A Switch with a null callback has no `toggleable` semantics, so TalkBack reads "Word wrap, switch" with no on/off state.
  - Each `FilterChipsRow` chip is a `Surface(onClick)` with no `selected`/`Role.Tab`/`RadioButton` semantics. Nothing announces which Theme, Default paper, Home filter or viewer Paper is active.
  - Viewer toggles only add an "On" icon when on, so "off" is never announced.
- **Reproduce:** Turn on TalkBack → Settings → focus "Word wrap" (no state spoken) → focus the "Dark" theme chip (no "selected").
- **Fix:** Use `Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange)` on the row. Use the `Surface(selected = isSelected, onClick = …)` overload, or `Modifier.semantics { selected = isSelected; role = Role.RadioButton }`, plus `selectableGroup()` on the LazyRow. Use `toggleable` on the toggle `SheetRow`s.
- **Verify with:** Compose UI tests: `onNodeWithText("Word wrap").assertIsOn()` / `assertIsOff()`, and `onNodeWithText("Dark").assertIsSelected()` after a tap.

<a id="feat-7"></a>
#### FEAT-7 — Folder list never refreshes: after renaming a file from a folder, the old entry opens "no longer available"
- **Severity:** Medium
- **Status:** Confirmed bug
- **Where:** `feature/folder/FolderViewModel.kt:41-46` (`reload()` runs only in `init`); `feature/folder/FolderScreen.kt:135`
- **Problem:** Viewer offers Rename for tree documents (`UriAccess.canRename` accepts `isInsideTree`). After renaming, Back returns to the same FolderViewModel, which still lists the old name and docId. Tapping it builds the old document URI, and the Viewer shows "This file is no longer available." Files added, deleted or edited elsewhere (including sizes after an editor save) also stay stale until the user leaves and re-enters the folder. There is no pull-to-refresh.
- **Reproduce:** Home → Add a folder → open it → open a .txt → ⋮ → Rename → Back. The old name is still listed, and tapping it shows an error.
- **Fix:** Call `vm.reload()` on ON_RESUME (`LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.reload() }` in FolderScreen), keeping the current list on screen while it reloads.
- **Verify with:** The manual steps above, or an instrumented test on a temp tree.

<a id="feat-8"></a>
#### FEAT-8 — Empty and error states can't scroll and their button rows don't wrap: on first launch in landscape or with large fonts, the only "Open file" button can be cut off
- **Severity:** Medium
- **Status:** Unverified assumption (layout check)
- **Where:** `core/designsystem/components/States.kt:49-53, 84-104`; `feature/home/HomeScreen.kt:231, 262-270`; `core/viewer/ViewerError.kt:16-19`; `feature/viewer/ViewerScreen.kt:77-80`
- **Problem:**
  - `EmptyState` and `ErrorState` are `fillMaxSize` + `Arrangement.Center` columns with no scrolling. The first-launch Home stacks a header, "Add a folder" and EmptyState (a 120 dp illustration, title, body and button) into what is left of the height. On a landscape phone (~360 dp tall minus bars) at font scale ≥1.3, the content overflows both edges. The FAB is deliberately hidden in this state (line 231), so "Open file" may be unreachable.
  - `ErrorState` puts its actions in a single `Row`. "Open with another app" + "File info" (ViewerError) and "Remove from recents" + "Go back" are about 300 dp wide at default font, more than the 296 dp available on a 360 dp phone after `xxl` padding. The second button gets squeezed or wraps its label.
- **Reproduce:** Clear recents, rotate to landscape, set font size to the largest, and open Home. Separately, open a damaged file at the largest font.
- **Fix:** Add `verticalScroll(rememberScrollState())` to both columns, and use `FlowRow` (or a Column below a width threshold) for the actions.
- **Verify with:** A Compose UI test at `DeviceConfigurationOverride.FontScale(1.5f)` with a landscape size: `onNodeWithText("Open file").assertIsDisplayed()` after `performScrollTo()`.

<a id="feat-9"></a>
#### FEAT-9 — Home multi-select loses its selection on rotation (mode stays on with "0 selected")
- **Severity:** Low
- **Status:** Confirmed bug
- **Where:** `feature/home/HomeScreen.kt:113-115, 124`
- **Problem:** `selected` is rememberSaveable, so it is meant to survive rotation (comment on line 114). But `LaunchedEffect(state.filter, state.query, state.sort) { selected = emptySet() }` also runs on the first composition after recreation and clears the restored set. `selectionMode` stays true.
- **Reproduce:** Long-press a recent row, select two, rotate. The header reads "0 selected".
- **Fix:** Only clear when the keys actually change. Keep the last keys in a rememberSaveable and compare them, or use `snapshotFlow { Triple(...) }.drop(1).collect { selected = emptySet() }`.
- **Verify with:** A Compose `StateRestorationTester` test: select 2, emulate save and restore, assert the "2 selected" text.

<a id="feat-10"></a>
#### FEAT-10 — `closeOnSave` is never reset, so a later normal Save closes the editor unexpectedly
- **Severity:** Low
- **Status:** Confirmed bug
- **Where:** `feature/editor/EditorScreen.kt:98, 119-124, 210`
- **Problem:** Back → dialog → "Save" sets `closeOnSave = true` (rememberSaveable). If that save stops at the validation sheet ("Fix it"), fails, or turns into a cancelled Save-copy picker, the flag stays true. The next successful Save from the top bar closes the editor while the user expects to keep working. It is also replayed after rotation, because `LaunchedEffect(s.savedCount)` runs again with `savedCount > 0`.
- **Reproduce:** In a .json file, type invalid JSON → Back → Save → Fix it → correct the JSON → tap Save. The editor closes.
- **Fix:** Reset `closeOnSave = false` in `dismissValidation`, on save failure, and when the picker is cancelled (`uri == null`). Key the close on the savedCount value observed at the time Save was requested.
- **Verify with:** A Compose UI test of the steps above, asserting the editor is still displayed.

<a id="feat-11"></a>
#### FEAT-11 — Rename dialogs: Viewer shows a stale error when reopened; typed name lost on rotation
- **Severity:** Low
- **Status:** Confirmed bug
- **Where:** `feature/viewer/ViewerViewModel.kt:59-60, 107, 113`; `feature/viewer/ViewerScreen.kt:195`; `feature/viewer/ViewerSheets.kt:161-164`; `feature/home/HomeScreen.kt:121-122`
- **Problem:** `_renameError` is cleared only when the next rename starts, so cancelling and reopening the dialog shows the old error straight away. `RenameDialog`'s field is a plain `remember`, so after rotation the Viewer dialog (whose open flag is rememberSaveable) comes back with the original name. On Home, the dialog itself (`fileToRename`) is plain `remember` and disappears on rotation.
- **Fix:** Clear `_renameError` when the dialog opens or is dismissed. Use `rememberSaveable(stateSaver = TextFieldValue.Saver)` for the field, and save `fileToRename?.uri` instead of the row.
- **Verify with:** A Compose UI test: trigger an error, Cancel, reopen, and assert there is no error text.

<a id="feat-12"></a>
#### FEAT-12 — Backing out of the Viewer quickly can drop the file from Recents (and leave an orphan copy)
- **Severity:** Low
- **Status:** Potential risk
- **Where:** `feature/viewer/ViewerViewModel.kt:73-75, 95, 135-160` — `record()`
- **Problem:** `record()` runs in `viewModelScope` after the content is shown. For a shared (WhatsApp/Gmail) file it first copies up to `MAX_FILE_BYTES` (a blocking copy that cancellation can't stop), then calls the suspend `library.recordOpen`. If the user goes Back before that finishes, the entry is DESTROYED, `viewModelScope` is cancelled, and `recordOpen` throws CancellationException. The file never reaches Recents, and the copy stays on disk. A related cosmetic effect: if the record lands after Home has recomposed, `lastOpenedAt` > Home's `now` (HomeScreen.kt:308), and `fileSubtitle` shows a future relative time ("In 0 min.").
- **Fix:** Run `record` in `withContext(NonCancellable)` (or an app-scoped scope in AppContainer).
- **Verify with:** An instrumented test: launch the Viewer for a large content URI, pop immediately, and assert that the Library row eventually exists.

<a id="feat-13"></a>
#### FEAT-13 — Shared-files list crashes if the same URI is shared twice
- **Severity:** Low
- **Status:** Potential risk (depends on the sending app)
- **Where:** `feature/viewer/SharedFilesScreen.kt:40` (`key = { it.first }`); `ExternalOpenActivity.kt:62-66` (no `distinct()`)
- **Problem:** A duplicate URI in `EXTRA_STREAM` gives the LazyColumn a duplicate key, which throws `IllegalArgumentException`.
- **Fix:** `uris.map { it.toString() }.distinct()` in ExternalOpenActivity, or key by index.
- **Verify with:** An `ExternalOpenTest` case using ACTION_SEND_MULTIPLE with [a, a, b].

<a id="feat-14"></a>
#### FEAT-14 — JSON/XML validation runs on the main thread inside Save
- **Severity:** Low
- **Status:** Potential risk
- **Where:** `feature/editor/EditorViewModel.kt:120` (`Validation.check` is called synchronously from `save()`); `EditorScreen.kt:157, 200`
- **Problem:** Files can be up to 5 MB. Parsing JSON with kotlinx or XML with the pull parser on the main thread, plus `editor.text.toString()`, causes a visible freeze on a mid-range phone, and there is no "saving" state shown during it.
- **Fix:** Move the check into the `viewModelScope.launch` and run it on `Dispatchers.Default` after setting `saving = true`.
- **Verify with:** Manual: a 4.5 MB JSON file, tap Save, check the frame stats (`adb shell dumpsys gfxinfo`).

<a id="feat-15"></a>
#### FEAT-15 — UI strings are hardcoded; strings.xml holds only `app_name`
- **Severity:** Low
- **Status:** Confirmed (a product decision: Type.kt:38-39 says "Folio's UI is English")
- **Where:** `res/values/strings.xml:1-4`; every screen in feature/** and designsystem components
- **Problem:** Nothing can be localized. Plurals are hand-built (`HomeScreen.kt:354` "file"/"files", `SharedFilesScreen.kt:33` "N shared files" even when it should read differently).
- **Fix:** Move strings into `strings.xml` and `plurals` when localization is planned. Start with the shared components and error messages.
- **Verify with:** A lint `HardcodedText`/custom check, or a pseudo-locale (en-XA) run.

<a id="feat-16"></a>
#### FEAT-16 — Extension badge text is below WCAG AA contrast in the light theme
- **Severity:** Low
- **Status:** Confirmed (computed)
- **Where:** `core/designsystem/Color.kt:116-128` — `BadgeTint`; `components/Rows.kt:43-59` — `ExtBadge` (11 sp `labelSmall`)
- **Problem:** The text is the tint itself, drawn on that tint at 14% alpha over `#FAF9F5`. Computed ratios: Pdf ≈3.7:1, Sheet ≈3.4:1, Image ≈3.2:1, Slides/Archive ≈3.0:1, Text ≈4.4:1. AA needs 4.5:1 for 11 sp text. TalkBack is fine because the badge has a spoken label, but low-vision readers will find it hard to read.
- **Fix:** Use a darker ink for light mode, e.g. `lerp(base, Color.Black, 0.3f)` for the text while keeping the 14% background.
- **Verify with:** A unit test that computes contrast for each `BadgeTint` in both modes and asserts ≥4.5.

<a id="feat-17"></a>
#### FEAT-17 — Missing semantics: section headings, an unlabeled font-size slider, page pill without an action
- **Severity:** Low
- **Status:** Confirmed bug
- **Where:** `feature/settings/SettingsScreen.kt:146-154` (`Group`), `:98-105` (`Slider`); `feature/home/HomeScreen.kt:408-415` (`SectionLabel`); `components/Buttons.kt:70-92` + `ViewerScreen.kt:208` (page `FloatingPill`)
- **Problem:** Group and section titles have no `semantics { heading() }`, so TalkBack users can't jump between headings. The slider has no label, so it reads only a percentage, not "Font size, 14 sp". The page pill reads "4 / 28" with no hint that it opens "Go to page".
- **Fix:** Add `Modifier.semantics { heading() }`, `Modifier.semantics { contentDescription = "Editor font size"; stateDescription = "${font} sp" }`, and `onClickLabel = "Go to page"` (`Surface` → `Modifier.semantics { onClick(label = …) }`).
- **Verify with:** Compose UI tests: `onAllNodes(isHeading())` count, `onNode(hasStateDescription(...))`.

<a id="feat-18"></a>
#### FEAT-18 — Home search/filter/sort and folder sort reset after process death
- **Severity:** Low
- **Status:** Confirmed bug (design gap)
- **Where:** `feature/home/HomeViewModel.kt:70-72`; `feature/folder/FolderViewModel.kt:39`
- **Problem:** These are plain `MutableStateFlow`/var with no SavedStateHandle, so they reset when the process is restored. Rotation is fine because the ViewModel survives it.
- **Fix:** Back them with `SavedStateHandle.getStateFlow` (HomeViewModel needs a handle through `createSavedStateHandle()`, the way Folder does).
- **Verify with:** An instrumented `ActivityScenario.recreate()` test does not cover this. Run `adb shell am kill` with the app in the background and check that the query is restored.

### PDF, images, media, archives, DOCX

<a id="fmt-1"></a>
#### FMT-1 — PDF pages with a tall or large aspect render an uncapped bitmap at 1x zoom, which crashes the app or leaves the page blank
- **Severity:** High
- **Status:** Confirmed bug. Every read of the code path shows it. Whether the result is a crash or a blank page depends on heap size and page shape.
- **Runtime check:** Confirmed on the emulator: landscape crash `Canvas: trying to draw too large(538255944bytes) bitmap`. Portrait at 1× on 1080 px rendered fine.
- **Where:** `formats/pdf/PdfViewer.kt:327-333` (`renderWidth`), `:299` (`PdfPage`), `formats/pdf/PdfDocumentHandle.kt:83-86` (`render`)
- **Problem:** `renderWidth` returns `baseWidth` with no pixel cap when `zoom < 1.2f` (line 328). When zoomed, `.coerceAtLeast(baseWidth)` (line 332) overrides the `MAX_RENDER_PIXELS` cap. `render()` then allocates `widthPx * (widthPx / aspect) * 4` bytes. Example: a 595 x 14400 pt page (a long receipt, or a web page saved as one PDF page) on a 1080 px phone gives 1080 x 26,100 = 113 MB. A 1:6 page on a landscape phone 2,300 px wide also exceeds 100 MB. Because of largeHeap, the allocation usually succeeds. The LruCache evicts the bitmap immediately because it is larger than maxSize, but `bitmap` state still holds it. Compose then draws it through a RecordingCanvas, which throws `RuntimeException: Canvas: trying to draw too large(...) bitmap` (limit 100 MB). That exception is not caught, so the app crashes. On smaller heaps the OOM is swallowed by `runCatching` (line 299) and the page stays blank.
- **Reproduce:** Create a one-page PDF with MediaBox `[0 0 595 14400]` containing some text. Open it in portrait. Or open a `[0 0 595 3600]` page and rotate to landscape.
- **Fix:** Apply the pixel cap at every zoom level and drop the `coerceAtLeast(baseWidth)` override. For example: `val w = if (zoom < 1.2f) baseWidth else (baseWidth*zoom).toInt().coerceAtMost(MAX_RENDER_WIDTH)/256*256; return minOf(w.coerceAtLeast(baseWidth), sqrt(MAX_RENDER_PIXELS*size.aspect).toInt()).coerceAtLeast(1)`. `ContentScale.FillBounds` already scales a smaller bitmap up, so the page only looks softer.
- **Verify with:** A unit test of `renderWidth` that asserts `w * (w / aspect) <= MAX_RENDER_PIXELS` for aspect 0.04 at zoom 1 and zoom 4. Then a manual open of the tall-page PDF on the emulator in both orientations.

<a id="fmt-2"></a>
#### FMT-2 — Rotating while a video or song is playing leaves it paused (API 28+)
- **Severity:** Medium
- **Status:** Confirmed bug
- **Runtime check:** Emulator check inconclusive: the emulator could not decode the H.264 sample at all (a Codec2 BufferPool error).
- **Where:** `formats/media/MediaViewer.kt:91,119,135`. These are `playWhenReady` saved state, the `onPlayWhenReadyChanged` listener, and `LifecycleEventEffect(ON_STOP) { player.pause() }`.
- **Problem:** No `configChanges` is declared, so rotation runs onStop, then onSaveInstanceState (after onStop on API 28+), then onDestroy. The ON_STOP handler calls `player.pause()`. ExoPlayer delivers `onPlayWhenReadyChanged(false)` synchronously, which sets the saveable `playWhenReady = false` before the state is saved. The recreated player starts paused, which breaks the "Survive rotation: … whether it was playing" intent stated on line 89. The most common case is rotating to landscape to watch a video, and that always pauses it.
- **Reproduce:** Open any MP4 in portrait. Let it auto-play, rotate to landscape, and the video is paused at the right position.
- **Fix:** Skip the pause when the Activity is only changing configuration: `val activity = LocalActivity.current; LifecycleEventEffect(ON_STOP) { if (activity?.isChangingConfigurations != true) player.pause() }`. The player is released in onDispose right afterwards anyway.
- **Verify with:** An instrumented Compose test or manual check: play a video, call `activityScenario.recreate()` or rotate, then assert `player.playWhenReady == true` and that the position is roughly kept.

<a id="fmt-3"></a>
#### FMT-3 — `.m4a` files whose ftyp brand is not `M4A ` open in the video player
- **Severity:** Medium
- **Status:** Confirmed bug. This is routing code: `MimeSniffer` belongs to core/files, but the bug shows up in MediaViewer and FormatInfo.
- **Runtime check:** Confirmed on the emulator: an `isom` M4A opens in the video player.
- **Where:** `core/files/MimeSniffer.kt:85-91` (`ftypBrand`, where `else -> "video/mp4"`), `core/model/FormatInfo.kt:153-158` (`describe`), `formats/media/MediaViewer.kt:88`
- **Problem:** A `.m4a` file with major brand `isom`/`mp42`/`mp41`/`dash` is sniffed as `video/mp4`. `MimeSnifferTest.kt:34` asserts that `isom` maps to `video/mp4`. Android's MediaRecorder MPEG_4 output is a common source of these brands, for example voice recorder apps. In `describe("video/mp4", "m4a")`, the extension format (AUDIO, `audio/mp4`) and the MIME format (VIDEO) disagree on both MIME and family, so the MIME wins and the file becomes VIDEO. MediaViewer then shows a black PlayerView that auto-plays, with no title, artist or cover card.
- **Reproduce:** `ffmpeg -f lavfi -i sine=d=5 -c:a aac -f mp4 voice.m4a` (major brand `isom`). Push it and open it in Folio.
- **Fix:** In `FormatInfo.describe`, treat AUDIO and VIDEO as agreeing families so the extension wins: add `|| setOf(byType.family, byExtension.family) == setOf(AUDIO, VIDEO)` to the line 157 condition.
- **Verify with:** A unit test asserting `FormatInfo.describe("video/mp4", "m4a").family == AUDIO` and `describe("audio/mp4", "mp4").family == VIDEO`.

<a id="fmt-4"></a>
#### FMT-4 — A ZIP with duplicate entry names crashes the archive browser (duplicate LazyColumn key)
- **Severity:** Low (was Medium before the emulator check)
- **Status:** Potential risk. The code path is certain. Whether the platform `ZipFile` passes duplicates through depends on the Android version.
- **Runtime check:** Not reproduced on API 37: the platform rejects the duplicate and Folio shows "This file looks damaged" (no crash). Lowered to Low; on older APIs it is untested.
- **Where:** `formats/archive/ZipArchive.kt:63-86` (no de-duplication), `formats/archive/ArchiveViewer.kt:153` (`key = { "f/" + it.entryName }`)
- **Problem:** `ZipFile.entries()` (OpenJDK-based, Android 7+) enumerates every central-directory record, including repeated names. Archives built by appending, or crafted ones, can contain repeats. Both become `ArchiveItem`s with the same `entryName`, so the LazyColumn gets the same key twice and throws `IllegalArgumentException: Key "f/…" was already used`. That crashes the app, because the exception happens in layout and not in the `runCatching` around opening. Unsafe items listed at the root (line 83) behave the same way. `getEntry(name)` only ever returns one of the duplicates, so the second row would be useless anyway.
- **Reproduce:** In Python: `z=zipfile.ZipFile('dup.zip','w'); z.writestr('a.txt','1'); z.writestr('a.txt','2'); z.close()` (prints a warning but writes both). Open `dup.zip` in Folio.
- **Fix:** Skip repeated names in the listing loop: `val seenNames = HashSet<String>()` … `if (!seenNames.add(entry.name)) continue`.
- **Verify with:** A unit test in `ZipArchiveTest` that writes a two-entry zip and patches the second name in both the local and central headers to match the first (the same byte-patch style as `encryptedEntriesAreDetected`). Assert `root.files.size == 1`.

<a id="fmt-5"></a>
#### FMT-5 — On older Android, ZIPs with non-UTF-8 (DOS code page) names show "damaged" instead of using the CP437 fallback
- **Severity:** Medium
- **Status:** Potential risk. Depends on the platform's `ZipFile` implementation. Needs a runtime check on API 26-30.
- **Runtime check:** Correct on API 37 (`café.txt` shown). Still needs an API 26 to 30 check.
- **Where:** `formats/archive/ZipArchive.kt:30-49` (fallback only around the constructor), `:63-66` (`entries().nextElement()` outside the fallback)
- **Problem:** The CP437 retry only runs when the `ZipFile(file, cs)` constructor throws. Desktop JDK 9+ (which the unit tests run on) validates names in the constructor, so the fallback looks fine there. OpenJDK-8-based `ZipFile` (older Android) decodes names lazily in `getZipEntry`, where `ZipCoder.toString` throws `IllegalArgumentException("MALFORMED")` from `entries().nextElement()` at line 66. That exception propagates out of `ZipArchive.<init>` and `ArchiveViewer.kt:73` reports "This file looks damaged". Windows' built-in "Compressed folder" writes names with accents or Cyrillic characters in the OEM code page without the UTF-8 flag, so these archives are common. No test covers the CP437 path at all.
- **Reproduce:** Create a zip on Windows Explorer containing `café.txt`, or use Python with `ZipInfo` where `flag_bits &= ~0x800` and the name is encoded as cp437. Open it on an API 26-30 emulator.
- **Fix:** Move the listing loop into a function. On `IllegalArgumentException` during listing, close the zip and reopen and re-list with `CP437`.
- **Verify with:** An instrumented test on an API 28 emulator plus a unit test, both using a CP437-named fixture zip, asserting the display name is `café.txt`.

<a id="fmt-6"></a>
#### FMT-6 — Large or image-heavy DOCX can run the app out of memory on the main thread
- **Severity:** Medium
- **Status:** Potential risk. Depends on file size and heap. I could not check mammoth's stream path from source because the jar is not in the local gradle cache.
- **Where:** `formats/docx/DocxViewer.kt:44-48` (`convertToHtml(InputStream)`), `:53-61` (OOM caught only around conversion), `:79` (`HtmlTemplates.wrap` in `remember` on the main thread), `core/viewer/SafeWebView.kt:50,101` (`loadDataWithBaseURL` with the full string)
- **Problem:** mammoth inlines every image as a base64 `data:` URI. As far as I know, the `InputStream` overload also buffers the whole package in memory, including embedded videos and OLE objects. A 60 MB photo-heavy DOCX becomes about 80 MB of base64, held as a UTF-16 String (about 160 MB). `wrap` then copies it again on the main thread and the WebView takes another copy. Only the conversion step catches `OutOfMemoryError`. An OOM in `wrap` or the WebView load is an uncaught crash. No ZipSafety budget applies to DOCX, so a zip-bomb `document.xml` is also bounded only by the OOM catch. Printing (line 42) converts the file again.
- **Reproduce:** A DOCX with about 40 full-resolution phone photos (50-80 MB). Open it, then switch paper (which rebuilds the HTML).
- **Fix:** Smallest option: in `Content`, return the existing "This file is too large to preview on this phone." message when `file.sizeBytes` is above about 40 MB. Also move `HtmlTemplates.wrap` inside the producer's try/catch. Better option: convert from a `File` (via `RandomAccess.file`) so mammoth reads entries lazily.
- **Verify with:** A manual or instrumented open of a 60 MB image DOCX on a 2 GB RAM emulator: you get the message, not a crash.

<a id="fmt-7"></a>
#### FMT-7 — PDF documents in the middle of opening, and ZIP archives, leak native handles and file descriptors when the viewer leaves during load
- **Severity:** Low
- **Status:** Confirmed bug, given coroutine semantics: `withContext` discards its result when the caller is cancelled before it resumes.
- **Where:** `formats/pdf/PdfViewer.kt:115-118` with `PdfDocumentHandle.kt:48-65`. `formats/archive/ArchiveViewer.kt:72-74`. Also `PdfDocumentHandle.kt:61-63`, where `doc` is not closed if `getPageSizes` throws.
- **Problem:** If the user presses Back or rotates while `open()` is running (seekableDescriptor may be copying a large streamed PDF, so this can take seconds), the block finishes on the PDFium thread. Its `PdfDocumentHandle` is then thrown away with a `CancellationException`, and `close()` never runs. That leaks the PDFium document's native memory plus a ParcelFileDescriptor for the life of the process. `ZipArchive` leaks the same way. ZipFile has a cleaner, so that leak is smaller.
- **Reproduce:** Open a large PDF from a slow or streaming provider, for example a Drive file not yet cached, and press Back immediately. Repeat about 50 times and watch the fd count in `/proc/<pid>/fd`.
- **Fix:** In `open`: `var opened: PdfDocumentHandle? = null; try { return withContext(pdfium) { build().also { opened = it } } } catch (e: CancellationException) { opened?.close(); throw e }`. Use the same pattern for `ZipArchive`. In the inner catch, also call `runCatching { doc.close() }` once `doc` exists.
- **Verify with:** An instrumented test: start `open()` in a job, cancel it right after the block starts (a test hook or a large file), and assert the fd count returns to its baseline.

<a id="fmt-8"></a>
#### FMT-8 — PDF page content is invisible to TalkBack
- **Severity:** Medium
- **Status:** Confirmed bug (accessibility)
- **Where:** `formats/pdf/PdfViewer.kt:308` (`semantics { contentDescription = "Page ${index + 1}" }`), `:311` (image `contentDescription = null`)
- **Problem:** Each page is a bitmap labelled only "Page N". The text PDFium already extracts for search (`PdfDocumentHandle.kt:108-112`) is never exposed, so a screen-reader user cannot read any PDF, including text-based ones. Zoom works only by gesture.
- **Reproduce:** Turn TalkBack on and open any text PDF. It announces "Page 1" and nothing else.
- **Fix:** Add `suspend fun pageText(index): String` to the handle (run on the same `pdfium` dispatcher), load it in `PdfPage` next to the render, and set `semantics { contentDescription = "Page ${index+1}"; text = AnnotatedString(pageText) }`.
- **Verify with:** A Compose UI test asserting `onNodeWithText("Folio", substring = true)` exists for `Samples.pdf("Folio")`, plus a manual TalkBack pass.

<a id="fmt-9"></a>
#### FMT-9 — Printing a phone photo ignores EXIF orientation, so portrait photos print sideways
- **Severity:** Low
- **Status:** Confirmed bug
- **Where:** `formats/image/BitmapPrintAdapter.kt:71-79` (`decodeSampled`)
- **Problem:** The viewer (Coil) applies EXIF rotation, but the print path decodes with plain `BitmapFactory`, which does not. Most phone JPEGs are stored landscape with `Orientation=6`, so Print and Save-as-PDF output the photo rotated 90°. Fit-to-page also uses the wrong width and height.
- **Reproduce:** Take a portrait photo with the emulator camera, or use any JPEG with Orientation=6. Open it, choose Print, and look at the preview.
- **Fix:** Read `ExifInterface(stream).rotationDegrees` (the library is already used in ImageViewer) and draw with a rotated `Matrix`, or swap width and height before fitting. On API 28+, `ImageDecoder` handles orientation directly.
- **Verify with:** An instrumented test of `decodeSampled()` on an Orientation=6 fixture, asserting `height > width`.

<a id="fmt-10"></a>
#### FMT-10 — DOCX (and HTML) jump back to the top when paper changes or the phone rotates
- **Severity:** Low
- **Status:** Confirmed bug (UX)
- **Where:** `formats/docx/DocxViewer.kt:79` (html keyed on `paper, dark`), `core/viewer/SafeWebView.kt:101` (`LaunchedEffect(webView, html) { load }`). Rotation recreates the Activity and the WebView.
- **Problem:** Each paper or theme change rebuilds the whole HTML string and calls `loadDataWithBaseURL`, so the reading position is lost. Rotation also re-converts the DOCX and starts again at the top. In long documents the user loses their place.
- **Reproduce:** Scroll halfway through a long DOCX, then switch paper to Sepia. Or rotate.
- **Fix:** Before reloading, save `scrollY / contentHeight`. Restore it from `onPageFinished` / `postVisualStateCallback`. Keep the fraction in `rememberSaveable` so it survives rotation.
- **Verify with:** A manual check on the emulator: scroll, switch paper, rotate.

<a id="fmt-11"></a>
#### FMT-11 — Video can go dark when the screen times out during playback
- **Severity:** Low
- **Status:** Unverified assumption. Depends on the Media3 1.11 PlayerView default.
- **Where:** `formats/media/MediaViewer.kt:158-170` (`VideoPlayer`). There is no `keepScreenOn` anywhere in `app/src/main`, and WAKE_LOCK is removed (`AndroidManifest.xml:9`).
- **Problem:** If PlayerView does not set keep-screen-on itself, the display sleeps after the system timeout. That triggers ON_STOP, which pauses the video (line 135).
- **Reproduce:** Set the screen timeout to 30 s, play a video longer than 1 minute, and don't touch the phone.
- **Fix:** In the PlayerView factory, add a listener: `player.addListener(object: Player.Listener { override fun onIsPlayingChanged(p: Boolean) { keepScreenOn = p } })`.
- **Verify with:** The manual check above.

<a id="fmt-12"></a>
#### FMT-12 — ZIPs containing a `../` or absolute entry probably show "damaged" on Android 14+, not the planned "listed but blocked" row
- **Severity:** Low
- **Status:** Unverified assumption (platform behaviour)
- **Runtime check:** Not reproduced on API 37: `../evil.txt` and `ok.txt` are both listed, as designed. Keep only as an older-API check.
- **Where:** `formats/archive/ZipArchive.kt:33-45,78-83`, `ArchiveViewer.kt:78-81`
- **Problem:** For apps targeting API 34+, the platform `ZipFile` rejects entry names that contain `..` or start with `/` with `ZipException` ("Invalid zip entry path"), as part of its zip-path-traversal protection. That exception does not mention "encrypted", so it goes through the CP437 retry, fails again, and the whole archive shows "This file looks damaged". The behaviour is still safe, but the message is wrong, and `zipSlipEntriesAreListedButNeverExtracted` only passes because unit tests run on the desktop JVM.
- **Reproduce:** In Python: `zipfile.ZipFile('slip.zip','w').writestr('../evil.txt','x')` plus a normal entry. Open it on an API 34+ emulator.
- **Fix:** If confirmed, map that ZipException to a specific message such as "This archive contains unsafe paths, so Folio won't open it."
- **Verify with:** The emulator check above.

<a id="fmt-13"></a>
#### FMT-13 — AVI, MPEG-PS, FLV, MKA and M4B fall back to the Unknown viewer even though bundled Media3 extractors can play them
- **Severity:** Low
- **Status:** Confirmed bug (format registry gap)
- **Where:** `core/model/FormatInfo.kt:91-102`, `core/files/MimeSniffer.kt:28-38` (no `RIFF....AVI ` magic; an unlisted intent MIME such as `video/x-msvideo` is dropped by the `forMime(it) != null` filter at line 36)
- **Problem:** `movie.avi` resolves to `application/octet-stream`, so the family is UNKNOWN and the user sees "Folio can't show this kind of file yet". Media3 1.11 ships AVI, PS, FLV and Matroska extractors. `.m4b` only works when its brand is exactly `M4B `.
- **Fix:** Add `add("video/x-msvideo", VIDEO, "AVI video", Media, "avi")`, `add("video/mpeg", VIDEO, "MPEG video", Media, "mpg", "mpeg")`, `add("video/x-flv", VIDEO, "Flash video", Media, "flv")`, plus `mka` and `m4b`, and a `RIFF`+`AVI ` magic check. Leave out `.ts`, which already maps to TypeScript.
- **Verify with:** A unit test asserting `FormatInfo.describe("application/octet-stream", "avi").family == VIDEO`, plus an emulator check with an H.264 AVI.

<a id="fmt-14"></a>
#### FMT-14 — The archive "Opening…" dialog cannot be cancelled during large extractions
- **Severity:** Low
- **Status:** Potential risk (UX)
- **Where:** `formats/archive/ArchiveViewer.kt:113-116,166-168` (`onDismissRequest = {}`), `ZipArchive.kt:111-121` (blocking copy loop with no cancellation check)
- **Problem:** Extracting an entry of up to 500 MB (the `ZipSafety.MAX_TOTAL_BYTES` limit) blocks the UI behind a dialog that Back cannot dismiss. Even if the coroutine were cancelled, the copy loop would keep running.
- **Fix:** Keep the `Job`, let `onDismissRequest` call `job.cancel()`, and call `coroutineContext.ensureActive()` in the copy loop (by making `extract` suspend or passing an `isActive` lambda). The `.part` file is already deleted in the catch.
- **Verify with:** A manual check: open a 400 MB video inside a zip and press Back while "Opening…" is showing.

<a id="fmt-15"></a>
#### FMT-15 — Printing a password-protected PDF sends the still-encrypted bytes to the print spooler
- **Severity:** Low
- **Status:** Unverified assumption (spooler behaviour)
- **Where:** `formats/pdf/PdfViewer.kt:103`, `formats/pdf/PdfPrintAdapter.kt:29-34`
- **Problem:** `printJob` always copies the original file, even after the user unlocked it with a password. The print spooler renders the preview without the password, so it most likely fails with a generic "couldn't print" error.
- **Fix:** Hide Print for documents that needed a password, for example `if (passwords.containsKey(file.uri.toString())) null else PdfPrintAdapter(...)`. The better option, rendering pages through PDFium into a `PrintedPdfDocument`, is more code.
- **Verify with:** The emulator check: open an encrypted PDF, unlock it, and choose Print.

### Spreadsheets, slides, text, Markdown, code

<a id="txt-1"></a>
#### TXT-1 — Slides with a gradient or picture background render on white, so light theme text disappears
- **Severity:** Medium
- **Status:** Confirmed bug
- **Runtime check:** Confirmed on the emulator: a white slide with invisible text.
- **Where:** `formats/slides/PptxReader.kt:136-140`, `PptxReader.background`; `formats/slides/SlideView.kt:63-64`, `SlideView`
- **Problem:** `bg.child("bgPr")?.let { return color(it.child("solidFill"), ctx) }` returns null from the whole function when the background is `gradFill`, `blipFill` or `pattFill`. It never falls through to `bgRef`, and the result is not "dark" either. `readSlide` (line 130) then takes the layout's or master's background, and if those are also non-solid the slide gets `Color.White` (SlideView:63). Text colour still resolves correctly through the master `titleStyle`/`bodyStyle` → `schemeClr tx1`. Dark templates map `tx1` to `lt1` (white), so the result is white text on a white slide. `defaultInk` (line 64) only helps runs that have no colour.
- **Reproduce:** Create a master with `<p:bg><p:bgPr><a:gradFill>…dark stops…</a:gradFill></p:bgPr></p:bg>`, `clrMap bg1="dk1" tx1="lt1"`, and a title style coloured `schemeClr tx1`. The script is in runtime check 1. Open it in the pager and the title is invisible. Any deck using "Format Background → Gradient/Picture" with light text shows the same thing.
- **Fix:** In `background()`, read `gradFill/gsLst/gs[0]` (or the middle stop) as the colour. For `blipFill`/`pattFill`/unknown, fall back to `ctx.colors["bg1"]`. In `readSlide`, use `?: colors["bg1"]` before SlideView's white default.
- **Verify with:** A unit test in `PptxReaderTest`: master `bgPr/gradFill` plus a dark `clrMap` gives a `Slide.background` that is non-null and dark. Then a manual check with runtime check 1.

<a id="txt-2"></a>
#### TXT-2 — Truncated reads decode a window cut mid-character, so the whole file falls back to Windows-1252
- **Severity:** Medium
- **Status:** Confirmed bug
- **Where:** `formats/sheet/SheetViewer.kt:81-86`, `SheetViewer.open`; `formats/text/TextLoader.kt:36-39`, `TextLoader.load`
- **Problem:**
  - **CSV over 50 MB:** the code decodes `bytes.copyOf(50 MB)` first and only then cuts at the last `'\n'` in the decoded text (line 86). If the 50 MB boundary splits a multi-byte UTF-8 character, `TextCodec.decode`'s strict decoder fails (`TextCodec.kt:35-41`). The whole 50 MB is then decoded as windows-1252, so every non-ASCII cell becomes mojibake (`Привет` shows as `ÐŸÑ€Ð¸Ð²ÐµÑ‚`).
  - **Text over 5 MB with no `'\n'` in the window** (minified JSON/JS, single-line logs): `cut == 0`, so line 39 decodes the raw window. The same mid-character split sends the whole view to windows-1252.
- **Reproduce:** Runtime check 2: a CSV of `a,Привет\n` × 3,600,000. 52,428,800 mod 15 = 5, so the cut lands inside `р`.
- **Fix:** Cut the byte array at the last `0x0A` before decoding, as TextLoader already does, in `SheetViewer.open`. For no-newline windows in both places, back off to a character boundary: for UTF-8, step back while `(b & 0xC0) == 0x80`, then drop the lead byte. Better still, move this into one helper (e.g. `TextCodec.decodePrefix(bytes, limit)`).
- **Verify with:** A unit test for the helper: 50 MB-style cut inside a 2-byte and a 3-byte character gives `editable`-charset UTF-8 and the expected text. Then a manual check with runtime check 2.

<a id="txt-3"></a>
#### TXT-3 — "Pretty view" re-formats JSON/XML on the main thread inside composition
- **Severity:** Medium
- **Status:** Confirmed bug (the main-thread placement is certain; ANR length depends on device and file)
- **Where:** `formats/text/TextViewer.kt:91`, `TextContent`; `formats/text/Pretty.kt:17-23`
- **Problem:** `remember(text, pretty) { Pretty.format(...) }` runs `Json.parseToJsonElement` plus `encodeToString`, or the Xml pull/serialize loop, synchronously during composition. Inputs can be up to 5 MB (and more after "Load more"). A multi-MB JSON blocks the UI thread for seconds when the toggle is tapped. The same pattern, on a cheaper scale, exists at `HtmlViewer.kt:85` (`HtmlTemplates.page` regex over up to 5 MB).
- **Reproduce:** Runtime check 3: a 4.9 MB JSON array. Toggle "Pretty view". The UI freezes, and an ANR dialog may appear on a mid-range phone.
- **Fix:** Compute it in `produceState(null, text, pretty) { value = withContext(Dispatchers.Default) { … } }` and show the raw text until it is ready. Optionally skip Pretty above roughly 2 MB.
- **Verify with:** Manual check with runtime check 3 (no frozen frames or ANR), or a macrobenchmark frame-timing check.

<a id="txt-4"></a>
#### TXT-4 — Large or limit-hitting XLSX sheets are reported as "damaged"; big sheets can OOM
- **Severity:** Medium
- **Status:** Confirmed bug (wrong message) and Potential risk (memory)
- **Where:** `formats/sheet/SheetViewer.kt:124` and `:160`, `WorkbookContent`; `formats/sheet/XlsxReader.kt:28,37,173-190`
- **Problem:**
  - Sheet loading is `runCatching { book.sheet(selected) }`, which catches `OutOfMemoryError`. So do `IOException("Part too large")` from `LimitedStream` when `sharedStrings.xml` exceeds 50 MB (`MAX_PART_BYTES`) and other failures. All of them show "This sheet looks damaged". Only `open()` maps OOM to "too large" (lines 59-60, 66), and for XLSX `open()` reads only the workbook.
  - Memory is bounded only by 100,000 rows × 1,000 columns, i.e. up to 10⁸ `String`s, plus the full shared-string list. A 100k × 50 sheet already holds about 5 M strings (hundreds of MB, even with `largeHeap`).
  - CSV peaks at about 50 MB of bytes, ×2 during `toByteArray`/`copyOf`, plus the decoded `String`, plus `substringBeforeLast`, plus the cells.
- **Reproduce:** An XLSX with 100,000 rows × 60 columns of distinct strings, or a `sharedStrings.xml` over 50 MB uncompressed. Open it: you get "This sheet looks damaged" rather than "too large".
- **Fix:**
  1. In `WorkbookContent`, map `OutOfMemoryError` and the `Part too large` IOException to the "too large to preview" text, the same as `open()`.
  2. Add a cell budget in `readSheet`/`CsvReader.parse` (e.g. stop at about 2–3 M non-null cells and set `truncated`).
  3. Raise `MAX_PART_BYTES` for `sharedStrings.xml` only, or stream it.
- **Verify with:** A unit test where `XlsxReader` with a tiny injected limit gives truncated, not a throw. Then a manual check with a 100k × 60 generated workbook.

<a id="txt-5"></a>
#### TXT-5 — The spreadsheet grid is not usable with TalkBack
- **Severity:** Medium
- **Status:** Confirmed bug
- **Where:** `formats/sheet/SheetGrid.kt:174-186`, `SheetGrid`
- **Problem:** Each row is one drawn node. Its `contentDescription` is `"Row n: " + cells.take(20)`. It has no column letters, reads empty cells as ", , ,", and never announces columns past T. Cell opening uses `pointerInput { detectTapGestures }`, which exposes no click action, so TalkBack users can't open or copy a cell. Horizontal scrolling uses `Modifier.scrollable` with no scroll semantics. Column headers have no semantics.
- **Reproduce:** Turn on TalkBack and open any CSV with more than 20 columns. Swipe to a row: columns after 20 are never read, and double-tap does nothing.
- **Fix:** Build the description as `"Row 5. A: x. C: y"`, skipping empty cells and covering all columns or at least the visible range. Add `semantics { onClick(label = "Open cell") { opened = first non-empty visible cell; true } }`, or better, a custom action per visible cell (`customActions`). Add `horizontalScrollAxisRange`/`scrollBy` semantics to the scrollable.
- **Verify with:** An instrumented Compose test: `onNodeWithContentDescription("Row 1", substring = true)` contains "U: " for a 21+ column sheet, and `performSemanticsAction(OnClick)` opens the bottom sheet.

<a id="txt-6"></a>
#### TXT-6 — Markdown links are opened straight by the system UriHandler; `file://` links likely crash the app
- **Severity:** Low (was Medium before the emulator check)
- **Status:** Unverified assumption (depends on markdown-renderer 0.46 link handling)
- **Runtime check:** Emulator: no crash. `file://` opens the system "Open with" sheet straight away; `https://` opens Chrome with no confirmation; `javascript:` and relative links are ignored. Lowered to Low.
- **Where:** `formats/text/MarkdownViewer.kt:103-118`, `MarkdownBody`. No `LocalUriHandler` or annotator override exists anywhere (grep for `LocalUriHandler|UriHandler` in `app/src` matches only baseline profiles).
- **Problem:** With no custom handler, link taps go through Compose's default `AndroidUriHandler` (`startActivity(ACTION_VIEW, uri)`). Compose's text-link code swallows only `IllegalArgumentException`, which covers no-activity cases such as `javascript:` and relative `other.md`. A `file:///…` link on targetSdk ≥ 24 throws `FileUriExposedException` from `startActivity`, which is not caught, so the app crashes. `https:` links leave Folio for the browser with no confirmation. That is user-initiated, but surprising in an offline-first app.
- **Reproduce:** Runtime check 4. Tap each link in Preview.
- **Fix:** Wrap `MarkdownBody` in `CompositionLocalProvider(LocalUriHandler provides SafeUriHandler)`. The handler allows only `http`, `https` and `mailto`, asks first ("Open in browser?"), and catches every exception. Ignore everything else.
- **Verify with:** An instrumented Compose test that clicks a `file:///sdcard/x` link and asserts no crash and no intent (Espresso-Intents). Then a manual check with runtime check 4.

<a id="txt-7"></a>
#### TXT-7 — Grammar loading and compile failures are uncaught crashes, and no test loads the app's own 27 grammars
- **Severity:** Low (was Medium before the emulator check)
- **Status:** Unverified assumption
- **Runtime check:** Emulator: all 30 sample files (one per grammar) open with no crash. Lowered to Low; adding the test and the fallback is still worthwhile.
- **Where:** `formats/text/SoraText.kt:63` (`produceState { TextMate.ensureLoaded(context) }`) and `:89` (`TextMateLanguage.create(it, false)` inside the `AndroidView` factory); `formats/text/TextMate.kt:23-40`; `app/src/androidTest/.../LibrarySmokeTest.kt:85-110`
- **Problem:** `ensureLoaded` throwing (a bad theme or `languages.json`), or a grammar failing to compile on first use of that language (regex or embedded-include issues), would throw from a composition coroutine or a view factory. Neither is caught, so the app crashes every time that file type is opened. The only TextMate test uses the androidTest assets (`textmate/languages.json` with Python and `quietlight.json`), not `app/src/main/assets/textmate/*`. So no test proves the 27 shipped grammars, or the two folio themes, load and tokenize.
- **Reproduce:** Runtime check 5: open one small file per mapped extension.
- **Fix:** Wrap language creation in `runCatching { TextMateLanguage.create(it, false) }.getOrElse { EmptyLanguage() }` and `ensureLoaded` in `runCatching`, falling back to plain text. Add an instrumented test that calls `TextMate.ensureLoaded` with the app context, then for every value in `TextMate.scopes` creates the language and `setText`s a sample on a `CodeEditor`.
- **Verify with:** That instrumented test (it iterates all scopes and expects no throw).

<a id="txt-8"></a>
#### TXT-8 — Markdown preview composes the whole document at once (non-lazy)
- **Severity:** Medium
- **Status:** Potential risk
- **Where:** `formats/text/MarkdownViewer.kt:73-75` (loads up to 5 MB) and `:103-118` (`Markdown(...)` inside `verticalScroll`)
- **Problem:** Parsing is off-thread, but the rendered result is a single scrolling Column. Every heading, paragraph and table is composed and measured up front. A 1–5 MB Markdown file (changelogs, docs dumps) can take seconds on the main thread or exhaust memory.
- **Reproduce:** Push a 2 MB Markdown file (`python -c "open('big.md','w').write(''.join(f'## H{i}\n\nParagraph {i} with **bold** and `code`.\n\n- a\n- b\n\n' for i in range(25000)))"`). Open it in Preview and watch for jank or ANR.
- **Fix:** Use the renderer's lazy success layout if 0.46 provides one (`LazyMarkdownSuccess` / a lazy `success` slot), with the lazy list replacing `verticalScroll`. Otherwise cap Preview at about 512 KB with a "Showing the first part, open Source for all" banner.
- **Verify with:** A manual or macrobenchmark check opening the 2 MB file, measuring time to first frame and checking for ANR.

<a id="txt-9"></a>
#### TXT-9 — Very long single lines (minified JSON/JS) with word wrap and TextMate
- **Severity:** Medium
- **Status:** Unverified assumption
- **Where:** `formats/text/SoraText.kt:89-90,142`
- **Problem:** A 5 MB single-line file is handed to Sora with a TextMate language and the user's word-wrap setting. TextMate tokenizes per line, so one 5 MB line is a single tokenize call. Wrap layout of a 5 MB line may also be slow. Whether Sora 0.23.6 time-limits or backgrounds either of these needs a device check.
- **Reproduce:** `python -c "import json;open('min.json','w').write(json.dumps([{'id':i,'name':'item %d'%i} for i in range(160000)],separators=(',',':')))"` (about 4.9 MB, one line). Open it with word wrap on and with it off.
- **Fix:** If any line is longer than about 20k characters (or the file is over 1 MB), use `EmptyLanguage()` and show a "Highlighting off for long lines" hint.
- **Verify with:** A manual check with the file above (scrolling stays smooth, no ANR).

<a id="txt-10"></a>
#### TXT-10 — Elapsed-time formats (`[h]:mm:ss`, built-in 46) wrap at 24 hours
- **Severity:** Low
- **Status:** Confirmed bug
- **Where:** `formats/sheet/XlsxReader.kt:126-141` (`date`), `:218` (id 46 → `TimeSeconds`), `:245`
- **Problem:** `[h]` is kept as a time format, then rendered as time-of-day `HH:mm:ss`, so whole days are dropped. 1.25 shows as `06:00:00` instead of `30:00:00`. Timesheets show wrong totals.
- **Reproduce:** A cell `<v>1.25</v>` with custom format `[h]:mm:ss`, or built-in numFmtId 46.
- **Fix:** Add `CellFormat.Elapsed` when the code contains `[h]`, `[m]` or `[s]`, or for id 46. Format it as `totalHours:mm:ss` from `serial * 86400`.
- **Verify with:** A unit test in `XlsxReaderTest` asserting 1.25 with `[h]:mm:ss` gives `"30:00:00"`.

<a id="txt-11"></a>
#### TXT-11 — East Asian built-in date formats (ids 27–36, 50–58) show raw serial numbers
- **Severity:** Low
- **Status:** Confirmed bug
- **Where:** `formats/sheet/XlsxReader.kt:212-220`, `classify`
- **Problem:** Built-in ids that are not listed in `numFmts` fall to `General`. Japanese, Chinese and Korean workbooks use ids 27–36 and 50–58 for dates without writing them out, so they show `45000`.
- **Reproduce:** `<cellXfs><xf numFmtId="0"/><xf numFmtId="31"/></cellXfs>` with `<c s="1"><v>45000</v></c>`.
- **Fix:** Map `27..36, 50..58` to `CellFormat.Date` in the `when`. Also remove the unused `BUILTIN_DATE_FORMATS` (line 30) or use it.
- **Verify with:** A unit test asserting id 31 with value 45000 gives `"2023-03-15"`.

<a id="txt-12"></a>
#### TXT-12 — Hidden and veryHidden sheets (and chartsheets) appear as normal tabs; the first tab may be a hidden one
- **Severity:** Low
- **Status:** Confirmed bug
- **Where:** `formats/sheet/XlsxReader.kt:161-166` (`state` attribute ignored); `formats/sheet/SheetViewer.kt:121` (always opens index 0, ignoring `bookViews/workbookView@activeTab`)
- **Problem:** Workbooks whose first sheet is a hidden lookup or config sheet open on that sheet. Chartsheets show "This sheet is empty".
- **Reproduce:** In workbook.xml, `<sheet name="Lookup" state="hidden" …/>` followed by a visible sheet.
- **Fix:** Store `state` in `SheetRef`. Open the first visible sheet (or `activeTab`). Mark hidden sheets in the chip label (e.g. "Lookup (hidden)"), or drop `veryHidden` sheets.
- **Verify with:** A unit test asserting `sheets[0].hidden == true` and that the viewer's initial index is the first visible sheet.

<a id="txt-13"></a>
#### TXT-13 — The "Showing the first N rows" banner appears when nothing is missing, and when columns were cut
- **Severity:** Low
- **Status:** Confirmed bug
- **Where:** `formats/sheet/XlsxReader.kt:67` and `:87`; `formats/sheet/CsvReader.kt:21`; `formats/sheet/SheetViewer.kt:151,162-164`
- **Problem:** Any `<row r>` ≥ 100,000 sets `truncated` and stops, even an empty formatting-only row. Excel often writes these, e.g. `<row r="1048576" ht="15" customHeight="1"/>`, so a 10-row sheet shows "Showing the first 10 rows" and "Rows 10+". When the column cap (1,000) triggers, the banner still talks about rows.
- **Reproduce:** A sheet with rows 1–10 plus `<row r="1048576" customHeight="1" ht="15"/>`.
- **Fix:** Only set `truncated` for an over-cap row that contains a `<c>` with a value. Use a separate `columnsTruncated` flag and text ("Showing the first 1,000 columns").
- **Verify with:** A unit test asserting that an empty row at r=1048576 leaves `truncated == false`.

<a id="txt-14"></a>
#### TXT-14 — Cropped pictures in slides are drawn stretched
- **Severity:** Low
- **Status:** Confirmed bug
- **Where:** `formats/slides/PptxReader.kt:155-160` (`blipFill/srcRect` ignored); `formats/slides/SlideView.kt:102,181` (`ContentScale.FillBounds`)
- **Problem:** A picture cropped in PowerPoint is drawn uncropped, squashed into the cropped frame's aspect ratio. Cropping photos is very common.
- **Reproduce:** `<p:blipFill><a:blip r:embed="…"/><a:srcRect l="25000" r="25000"/>…` on a 4:3 image in a 2:3 frame.
- **Fix:** Read the `srcRect` l/t/r/b (1/1000 %) into `Picture`. In `SlidePicture`, draw with a clip plus scale and offset (e.g. `Modifier.clipToBounds().layout{…}` or `graphicsLayer`) so the source rect fills the frame.
- **Verify with:** A unit test that `Picture.crop` is parsed, plus a manual visual check.

<a id="txt-15"></a>
#### TXT-15 — PptxReader keeps every parsed XML tree until the viewer closes
- **Severity:** Low
- **Status:** Potential risk
- **Where:** `formats/slides/PptxReader.kt:73-77` (`parts` cache, never cleared); `formats/slides/SlidesViewer.kt:80-100` (the reader lives for the whole viewing session, for `media()`)
- **Problem:** After `read()`, the `Deck` holds everything needed, but every slide's, layout's, master's, theme's and rels' `XNode` tree (several times the XML size) stays reachable. With up to 2,000 slides this is tens to hundreds of MB, on top of the decoded images.
- **Fix:** At the end of `read()`, call `parts.clear()`. Or cache only layout, master and theme paths. `media()` doesn't use the cache.
- **Verify with:** A unit test that `read()` leaves the cache empty (e.g. an internal size accessor), plus a heap dump on a 300-slide deck.

<a id="txt-16"></a>
#### TXT-16 — "Load more" reloads and recreates the editor, jumping back to the top
- **Severity:** Low
- **Status:** Confirmed bug
- **Where:** `formats/text/TextViewer.kt:68-71,104`; `formats/text/SoraText.kt:72` (`key(text, scope)`)
- **Problem:** The banner sits at the end of the loaded window. Tapping it re-reads `limit + 5 MB` from the start, the new `text` re-keys the `AndroidView`, and a fresh `CodeEditor` opens at line 1. Every further "Load more" also re-reads and re-highlights everything already loaded.
- **Fix:** Before reloading, remember `editor.firstVisibleLine` (or the cursor line) and restore it in `onReady` (`editor.jumpToLine`). Pass an `initialLine` parameter to `SoraText`.
- **Verify with:** A manual check: a 12 MB log, scroll to the end, tap "Load more", and the view stays at the old end.

<a id="txt-17"></a>
#### TXT-17 — Markdown and HTML previews are silently cut at 5 MB
- **Severity:** Low
- **Status:** Confirmed bug
- **Where:** `formats/text/MarkdownViewer.kt:73-87`; `formats/text/HtmlViewer.kt:71-87`
- **Problem:** Both previews call `TextLoader.load(..., WINDOW_BYTES)` but ignore `truncated`. There is no banner, so the document just ends early (HTML may be cut mid-tag). Source mode does show `TruncatedBanner`.
- **Fix:** When `truncated`, show the same banner, with "Open Source to load more" or reuse `TruncatedBanner`.
- **Verify with:** A manual check with a 6 MB `.md` file. An instrumented test could assert the banner text exists.

<a id="txt-18"></a>
#### TXT-18 — Text printing: silent 2 MB cut, cancellation never answered, stale layout can win; Markdown prints raw source
- **Severity:** Low
- **Status:** Confirmed bug
- **Where:** `formats/text/TextPrintAdapter.kt:32,52` (2 MB limit, `truncated` ignored), `:67` (`return@thread` without `callback.onLayoutCancelled()`), `:72-76` (a cancelled-after-loop thread still posts `layout = built` and `onLayoutFinished`); `formats/text/MarkdownViewer.kt:56`
- **Problem:**
  - "Save as PDF" of a 3 MB log produces a PDF with only the first 2 MB, with no note.
  - A cancelled layout leaves the framework without a callback.
  - If the user changes paper size during a slow layout, the older thread can finish last and overwrite `layout`, so the PDF is wrapped for the wrong width.
  - Printing Markdown prints the source, not the rendered preview.
- **Fix:**
  - When `truncated`, append a final line "[Truncated: only the first 2 MB was printed]", or raise the limit.
  - On cancel, post `callback.onLayoutCancelled()`.
  - Give each layout a generation counter and ignore results that aren't the latest (also check `isCanceled` before posting).
  - For Markdown, either document the source print or render through `WebViewPrintAdapter`.
- **Verify with:** A manual print-preview check with a 3 MB log, plus switching A4↔Letter quickly during layout.

<a id="txt-19"></a>
#### TXT-19 — XML "Pretty view" drops the spaces around entity references
- **Severity:** Low
- **Status:** Confirmed bug (per the XmlPullParser `nextToken()` contract: `ENTITY_REF` is reported separately)
- **Where:** `formats/text/Pretty.kt:39,46`
- **Problem:** With `nextToken()`, `Tom &amp; Jerry` arrives as TEXT `"Tom "`, ENTITY_REF `"&"` and TEXT `" Jerry"`. Each piece is `trim()`med and written back to back, giving `Tom&amp;Jerry`, so the pretty view changes the content. The same happens for `&#169;` and similar.
- **Reproduce:** `<a>Tom &amp; Jerry</a>` in `x.xml` with Pretty view on.
- **Fix:** Collect consecutive TEXT, ENTITY_REF and CDSECT-free text into one buffer and write it once (trimmed only when it is whitespace-only). Or use `next()`, which merges text and entities, and keep `nextToken()` only for comments and PIs.
- **Verify with:** An instrumented test (android.util.Xml) asserting `Pretty.format("<a>Tom &amp; Jerry</a>","xml","")` contains `Tom &amp; Jerry`.

<a id="txt-20"></a>
#### TXT-20 — Syntax highlighting covers 27 grammars, not the "40+ languages" the README claims; name-only files are never highlighted
- **Severity:** Low
- **Status:** Confirmed bug (claim vs. code)
- **Where:** `formats/text/TextMate.kt:42-74`; `assets/textmate/languages.json` (27 entries); `README.md:59`; `core/model/FormatInfo.kt:74-79`
- **Problem:** README says "40+ languages with syntax colours". Grammars exist for 27. scss, sass and less use the CSS grammar, toml and env use INI, and vue and svelte use plain HTML. These CODE-family extensions open as plain text: ps1, bat, cmd, pl, r, scala, groovy, gradle, m, mm, hs, ex, exs, erl, clj, fs, vb, asm, proto, graphql, gql, tf, nix. `scopeFor(file.extension)` also never matches `Dockerfile` or `Makefile`, because they have no extension. The docker grammar only applies to `*.dockerfile`.
- **Fix:** Reword the README (e.g. "25+ languages"), or add grammars. In `scopeFor`, also map well-known file names (`Dockerfile`, `Containerfile`) by `file.name`.
- **Verify with:** A unit test on `TextMate.scopeFor` for name-based files, plus a README check.


## Product experience

### Installation and onboarding

- **First launch** is calm and clear. A one-line empty state ("Open anything", plus how to open a file from WhatsApp, Gmail or Files), one **Open file** button, a small "Add a folder" link and a New-file icon. There are no permission prompts, no account and no tour. This matches the product principles.
- **The empty state is the only Open button.** The floating button is hidden until Recents has a row, so at large font sizes or in landscape that button can go off-screen (FEAT-8).
- **Installation instructions** in the README are unusually good: each sideload warning is named, with what to tap and screenshots. The SHA-256 is published. Missing: the signing certificate fingerprint (DIST-5) and a Windows command for the checksum (`certutil -hashfile Folio-1.0.0.apk SHA256`).
- **Error recovery** is consistent. Every viewer falls back to `ViewerError`, with "Open with another app" and "File info". After a crash, the next launch offers an email-draft crash report ("Folio closed unexpectedly", Not now / Send report), and it appears even over a file opened from another app. Folder removal has no confirm or undo, unlike Recents removal.

### Document viewing (by format)

| Format | Renders with | Works well | Gaps found |
|---|---|---|---|
| PDF | PDFium (`io.legere:pdfiumandroid` 2.0.3), single-thread dispatcher | Continuous scroll, pinch and double-tap zoom with sharper re-render, search with highlights, user and owner passwords, annotations, corrupted files handled | Tall-page crash (FMT-1), not readable by TalkBack (FMT-8), no text selection, outline or links, encrypted print (FMT-15), leaks if Back is pressed during open (FMT-7) |
| DOCX | mammoth 1.13 → locked-down WebView | Headings, lists, tables, images, find, Sepia and Dark paper, print, XXE-safe | Simplified layout (no headers or footers, columns or text boxes), EMF/WMF images broken, memory on image-heavy files (FMT-6), scroll lost on paper change or rotation (FMT-10); `.doc`, `.rtf` and `.odt` go to Unknown |
| XLSX | Folio's streaming reader + Canvas grid | Shared and inline strings, cached formula values, 1900 and 1904 dates, sparse cells, column letters to XFD, search, copy | Number formats beyond date and percent are shown raw, `[h]` durations (TXT-10), CJK built-in dates (TXT-11), hidden sheets (TXT-12), misleading "first N rows" banner (TXT-13), large sheets reported as "damaged" (TXT-4), TalkBack (TXT-5) |
| PPTX | Folio's reader + Compose slides | Slide order from `presentation.xml`, placeholder and style inheritance, theme colours, bullets, pictures, outline view, search | Gradient or picture backgrounds come out white (TXT-1, confirmed), crops ignored (TXT-14), no notes, no layout or master decorations, charts and SmartArt shown as labels |
| Markdown | multiplatform-markdown-renderer 0.46 + Sora for Source | Headings, lists, tables, task lists, code blocks; remote images never load | No code highlighting in Preview, links open other apps without asking (TXT-6), whole document composed at once (TXT-8), silent 5 MB cut (TXT-17), prints the source (TXT-18) |
| TXT / logs | Sora editor, 5 MB window + "Load more" | UTF-8 and UTF-16 with BOM, read-only windows-1252 fallback, line endings kept, find, wrap and font settings | Legacy Cyrillic and CJK encodings become mojibake (CORE-11), mid-character cuts (TXT-2), "Load more" jumps to the top (TXT-16) |
| CSV / TSV | Folio's RFC 4180 parser | Quotes, newlines inside quotes, BOM, delimiter detection (`, ; \t \|`), editable up to 5 MB | Non-ASCII files over 50 MB turn into mojibake (TXT-2), memory peaks (TXT-4) |
| JSON | Sora + TextMate, Pretty view via kotlinx | Highlighting, Pretty view keeps number text | Pretty view runs on the main thread (TXT-3); long single lines (TXT-9) |
| XML | TextMate, Pretty view via `android.util.Xml` | Highlighting, Pretty view | Pretty view drops spaces around entities (TXT-19); billion-laughs risk in OOXML parsing (CORE-4) |
| YAML | TextMate | Highlighting | No Pretty view (not claimed) |
| HTML | SafeWebView (JS off, network blocked twice, file and content access off) | Local HTML with `data:` images, fit to screen, paper filters, find, print | Linked local CSS and images not shown (by design), silent 5 MB cut (TXT-17) |
| Code | Sora 0.23.6 + 27 TextMate grammars | All 30 sample files opened on the emulator | README says "40+ languages" (TXT-20); `Dockerfile` and `Makefile` are not detected by name |
| Images | Coil 3 + Telephoto | JPEG, PNG, WebP, GIF, BMP, SVG; HEIC on 28+ and AVIF on 31+; EXIF rotation; deep zoom; EXIF sheet | Prints ignore EXIF rotation (FMT-9); TIFF, DNG and JXL go to Unknown |
| Audio | Media3 ExoPlayer 1.11 | MP3, AAC, FLAC, WAV, Ogg, Opus, AMR; audio focus; pause when headphones are unplugged; cover art | `isom` M4A opens as video (FMT-3, confirmed); no background playback (by design); MKA and M4B not registered (FMT-13) |
| Video | Media3 + PlayerView | MP4, MKV, WebM, 3GP, MOV, decoder fallback, immersive mode | Rotation pauses playback (FMT-2, traced in code); screen may time out (FMT-11); AVI, MPG and FLV not registered (FMT-13) |
| ZIP | `java.util.zip` + ZipSafety | Central-directory listing, folder tree, zip-slip-safe extraction, bomb limits, encrypted entries detected | Duplicate names (FMT-4), CP437 names on old Android (FMT-5), extraction can't be cancelled (FMT-14); RAR, 7z and TAR go to Unknown |

### Markdown and code editing

- **Syntax highlighting:** 27 grammars plus Folio's own light and dark themes. Extensions without a grammar open as plain text (TXT-20).
- **Search and copy:** find works in every text, code and document viewer. Copying is by selection in the Sora editor and by long-press in sheet cells. TalkBack users cannot open or copy a cell (TXT-5).
- **Editing:** text, Markdown, CSV, code and HTML up to 5 MB, with undo and redo, a symbol bar, Preview for Markdown and HTML, and JSON/XML validation before saving (on the main thread: FEAT-14).
- **Autosave and recovery:** a draft every 5 s and on going to the background. On reopen, "You have unsaved changes from earlier" offers Restore or Discard, which the emulator confirmed after killing the app. The weak spots are all on the edges: Discard (FEAT-3), Save copy after recreation (FEAT-1), typing during a save (FEAT-4), and the editor being read before it exists (FEAT-5).
- **Saving** is careful: back up, write, read back, restore on mismatch, and fall back automatically to "Save copy" for read-only targets.

### General experience

- **Home and Recents:** search (names only), filters, sort, pins, multi-select, swipe-to-remove with Undo (tested). Multi-select loses its selection on rotation (FEAT-9). Search, filter and sort reset after the process is killed (FEAT-18).
- **Folders:** the list never refreshes, so a renamed or deleted file stays listed until you re-enter (FEAT-7). Removing a folder leaves dead Recents rows (CORE-8).
- **Settings and themes:** Light, Dark and Black app themes, and Light, Sepia and Dark paper, all applied instantly. "Clear recents and copies" gives no confirmation that it finished. Switches and chips don't announce their state to TalkBack (FEAT-6).
- **Accessibility:** icon buttons require labels, which is good. Missing: heading semantics, a slider label, a page-pill action (FEAT-17), PDF text (FMT-8), grid cells (TXT-5) and extension-badge contrast (FEAT-16).
- **Landscape and small screens:** empty and error states don't scroll and their buttons don't wrap (FEAT-8). The PDF crash in FMT-1 is landscape-specific.
- **Localization:** every UI string is hard-coded English (FEAT-15). This is a documented product decision, but it blocks translation.
- **Speed:** the README quotes 0.23 to 0.32 s from tap to file on a Motorola Edge 50. On the emulator, the first cold open of a text file still showed the loading skeleton at 4 s (TextMate start-up). An emulator isn't representative, but it is worth measuring on a low-end phone.

## Distribution and open source

| Topic | Finding |
|---|---|
| README | Accurate and user-focused. Corrections: "40+ languages" should be about 27 (TXT-20). Add the certificate fingerprint and a Windows checksum command. |
| Release documentation | `docs/release.md` is clear on key custody and the steps. Gaps: no check of which key signed the build (DIST-2), no signing-block or ID check (DIST-1), no changelog file (DIST-4). |
| Changelog | Only in GitHub release notes and the website repo (DIST-4). |
| License | Apache-2.0 `LICENSE` at the root. |
| Dependency licences | Checked at build time by `licensee`: Apache-2.0, MIT, BSD-2/3, Unlicense, EPL-2.0 (tm4e), plus explicitly allowed LGPL-2.1 Sora Editor 0.23.6, which is kept unobfuscated by ProGuard rules so it stays replaceable. AboutLibraries shows the list in the app. These are all compatible with Apache-2.0 distribution, as long as the LGPL notice and source pointer stay in the app. |
| Reproducible builds | Not verified (DIST-5). |
| F-Droid eligibility | **Not verified and not claimed.** Known blockers or questions: (1) the dependency-metadata block (DIST-1, a definite blocker until removed); (2) prebuilt native PDFium libraries inside the `io.legere:pdfiumandroid` AAR, where F-Droid normally requires native code built from source, so check with `fdroid scanner` and the maintainers; (3) whether the build server supports AGP 9.4.1, Gradle 9.8.2 and compileSdk 37; (4) signing: F-Droid signs with its own key unless the build is reproducible, so F-Droid and GitHub installs could not update each other; (5) no `fastlane` metadata (DIST-4). No anti-features are expected: no tracking, no network, and no non-free network services. IzzyOnDroid is a lighter first step, since it ships the developer's own APK. |
| Obtainium | Compatible as documented: GitHub Releases with one APK asset per tag, the same signing key every time, and `https://github.com/vishalsolanki-v/folio` resolves. Keep one APK asset per release and don't mark releases as pre-release unless intended. |
| APK checksum verification | The published SHA-256 matches the actual v1.0.0 APK (`9d4ce51d…0d9f17`). Add the certificate SHA-256 `19bf74536960acb7421491ba83096bf9f68ac68ea7ad5547810c557c328fe218`. |
| Signing scheme | v2 only. That's fine for minSdk 26, but v3 would allow key rotation later. Consider enabling it before 1.1, while the install base is small. |
| CI | None (DIST-3). |

## Strengths worth keeping

- **Privacy enforced by the build:** INTERNET, ACCESS_NETWORK_STATE and WAKE_LOCK removed with `tools:node="remove"`; WebView metrics opt-out; `allowBackup=false`; no analytics or crash SDK; feedback and crash reports are email drafts the user sees first.
- **Locked-down rendering:** SafeWebView has JS off, network blocked twice, all sub-resources except `data:` intercepted, navigation swallowed, and file and content access off. Markdown has no image loader, and raw HTML in Markdown never reaches a WebView.
- **Hostile-file hygiene:** canonical-path zip-slip guard, streaming bomb budget, sniffer peeks capped at 2 MB and 200 entries, `LimitedStream` on XML parts, DOCTYPE off, and external PPTX relationships dropped.
- **Data safety when saving:** `FileSaver` backs up, writes with "wt", reads back to verify and restores on failure, and never truncates with "w". `TextCodec` refuses to write back text it can't round-trip. Drafts are written to a temp file and renamed into place.
- **Careful grant and storage bookkeeping:** persisted grants stay under the platform limit, private copies are capped at 25 MB each and 300 MB in total with LRU eviction that spares pins, the Room schema is exported with no destructive fallback, and per-entry ViewModels prevent stale-file races.
- **A real test base:** 85 unit and 34 instrumented tests, all passing, including the hard parts (FileSaver failure modes, XLSX date edge cases, PPTX inheritance, zip slip and bombs, undoable removal).
