# Folio test matrix

_Companion to the [audit](AUDIT.md) and the [roadmap](ROADMAP.md). It lists the test scenarios Folio needs, which already exist, and which need building. Status as of commit `c657420`, 25 November 2026._

**Legend:** ✅ an automated test exists · 🟡 partly covered, or checked by hand in the audit · ❌ missing · 🐞 currently fails (a known bug; the finding ID is linked)

**Current baseline:** 85 unit tests and 34 instrumented tests (Android 16, API 37 emulator), all passing.

> ⚠️ `HomeRemoveUndoTest` calls `library.clearRecents()` on the app's real database. Run instrumented tests only on an emulator or a test device, never on a phone with real Recents.

## Existing automated tests

| Suite | Type | What it covers |
|---|---|---|
| `MimeSnifferTest` | Unit | Magic numbers, ZIP-container classification (DOCX/XLSX/PPTX/EPUB), priority rules, BOM text, capped ZIP peek |
| `TextCodecTest` | Unit | UTF-8 and UTF-16 with and without BOM, line endings, emoji, invalid UTF-8 is read-only |
| `ZipSafetyTest`, `ZipArchiveTest` | Unit (desktop JVM) | Traversal, bombs, budgets, encrypted flag, folder tree, extract and reuse |
| `FileSaverTest` | Unit | Atomic overwrite, failure in the middle of a write, restore, silent truncation caught |
| `LocalCopiesTest`, `DraftsTest`, `CacheCleanerTest`, `SourceAppsTest`, `SettingsRepositoryTest` | Unit | Copies and LRU, drafts, cache age, labels, settings defaults and clamping |
| `XlsxReaderTest`, `CsvReaderTest`, `PptxReaderTest` | Unit | Strings, dates (1900/1904), caps, delimiters, slide order, inheritance, colours |
| `HtmlTemplatesTest`, `HexDumpTest` | Unit | Viewport, paper styles, script stripping; hex rows |
| `ExternalOpenTest` | Instrumented | VIEW for 10 formats, ZIP `../` refusal, SEND text, SEND_MULTIPLE, empty SEND |
| `LibraryTest` | Instrumented | Recents order, pins, de-duplication, trim, grant budget, folders, clear |
| `FileResolverTest` | Instrumented | FileProvider, extension-less DOCX, `file://`, missing file, saver |
| `PdfDocumentHandleTest` | Instrumented | Render plus search rectangles, truncated PDF |
| `HomeRemoveUndoTest` | Instrumented (Compose) | Swipe, Undo, timeout commit |
| `LibrarySmokeTest`, `MarkdownSmokeTest` | Instrumented | PDFium, mammoth (including XXE), Sora/TextMate, Coil PNG and SVG, Media3 WAV, Markdown |

**Nothing tests:** EditorViewModel, ViewerViewModel, HomeViewModel or FolderViewModel logic; `FormatInfo` routing; `UriAccess`; `Sharing`; `RandomAccess`; `TextLoader`; `Pretty`; MediaViewer's lifecycle; the print adapters; state restoration; accessibility semantics; Room migrations.

---

## A. Small files (one per format: the "golden set")

| Scenario | Expected | Coverage |
|---|---|---|
| Open a small valid file of each claimed format: PDF, DOCX, XLSX, CSV, TSV, PPTX, TXT, MD, JSON, XML, YAML, HTML, a code file, JPEG, PNG, WebP, GIF, SVG, HEIC, AVIF, MP3, M4A, FLAC, OGG, MP4, MKV, WebM, ZIP | Correct viewer, no error | 🟡 `ExternalOpenTest` covers 10 families. Missing: TSV, YAML, XML, GIF, HEIC, AVIF, FLAC, OGG, MKV, WebM |
| One sample per syntax grammar (27 grammars) | Opens with colours, no crash | 🟡 30 samples opened by hand in the audit, all fine. ❌ No automated test over `TextMate.scopes` (TXT-7) |
| `FormatInfo.describe` routing table (MIME × extension) | Right family | ❌ No `FormatInfoTest`. Add with FMT-3 and FMT-13 |
| `.m4a` with `isom` or `mp42` brand | Audio card | 🐞 [FMT-3](AUDIT.md#fmt-3): opens the video player (confirmed) |

## B. Large files

| Scenario | Expected | Coverage |
|---|---|---|
| PDF with 500+ pages | Smooth scroll, memory flat | ❌ Manual |
| PDF with one tall page (595 × 14,400 pt), portrait and landscape | Renders (softer), no crash | 🐞 [FMT-1](AUDIT.md#fmt-1): landscape crash (confirmed) |
| Text file over 5 MB, then "Load more" | Banner, position kept | 🟡 Banner exists. 🐞 [TXT-16](AUDIT.md#txt-16): jumps to the top |
| Text over 5 MB with no newline (minified JSON) | Correct UTF-8, responsive | 🐞 [TXT-2](AUDIT.md#txt-2), ❓ [TXT-9](AUDIT.md#txt-9) |
| CSV over 50 MB with non-ASCII text | First rows shown, UTF-8 correct | 🐞 [TXT-2](AUDIT.md#txt-2) |
| XLSX, 100k rows × 60 columns | "Too large" or truncated, never "damaged" | 🐞 [TXT-4](AUDIT.md#txt-4) |
| DOCX, 50 to 80 MB with many photos | Message, not a crash | ❓ [FMT-6](AUDIT.md#fmt-6) |
| 4.9 MB JSON with Pretty view on | No ANR | 🐞 [TXT-3](AUDIT.md#txt-3) |
| 2 MB Markdown in Preview | No ANR | ❓ [TXT-8](AUDIT.md#txt-8) |
| ZIP over 1 GB, and extracting a 400 MB entry | Listing is fast; extraction can be cancelled | ❓ [CORE-5](AUDIT.md#core-5), 🐞 [FMT-14](AUDIT.md#fmt-14) |
| Image of 12,000 × 12,000 px | Tiled zoom, no OOM | ❌ Manual |
| Editing a 4.9 MB text file, then Save | Saved and verified, no freeze | 🟡 `FileSaverTest` (logic); ❌ UI timing ([FEAT-4](AUDIT.md#feat-4), [FEAT-14](AUDIT.md#feat-14)) |

## C. Corrupted files

| Scenario | Expected | Coverage |
|---|---|---|
| Truncated PDF | "Damaged" error, no password prompt | ✅ `PdfDocumentHandleTest` |
| Garbage PDF, DOCX and XLSX; 4 KB of random bytes; empty `.txt` | Error or empty state, no crash | 🟡 Checked by hand in the audit (all passed). ❌ Not automated |
| ZIP bomb, zip slip | Stopped, listed but never extracted | ✅ `ZipArchiveTest`, `ZipSafetyTest` (desktop JVM only) |
| ZIP with duplicate entry names | Listed once, or a clear message | 🟡 API 37 shows "damaged" ([FMT-4](AUDIT.md#fmt-4)). ❌ Older APIs untested |
| PPTX or XLSX whose XML has a billion-laughs DOCTYPE | Rejected quickly | ❓ [CORE-4](AUDIT.md#core-4) (needs the Android parser, so an instrumented test) |
| DOCX with an XXE entity | Entity not resolved | ✅ `LibrarySmokeTest` |
| Corrupt `settings.preferences_pb` | Defaults; settings still writable | 🐞 [CORE-6](AUDIT.md#core-6) |

## D. Unsupported files

| Scenario | Expected | Coverage |
|---|---|---|
| `.doc`, `.rtf`, `.odt`, `.rar`, `.7z`, `.tiff` | Unknown viewer with a friendly name, "Open with another app", and "Open as text" when the file is text | 🟡 `ExternalOpenTest` (unknown). ❌ A per-type naming check |
| `.avi`, `.mpg`, `.flv` | Should play (Media3 can) | 🐞 [FMT-13](AUDIT.md#fmt-13): Unknown viewer |
| Password-protected DOCX (OLE container) | Clear "protected" message | ❌ |

## E. Unicode file names

| Scenario | Expected | Coverage |
|---|---|---|
| File named in Devanagari, CJK, Arabic (RTL) or emoji, opened from Files | Name shown in full with the middle ellipsis; extension kept | ❌ Manual |
| Same, received from WhatsApp (private copy) with a name over 85 CJK characters | Copy kept; still in Recents later | 🐞 [CORE-3](AUDIT.md#core-3) (copy silently dropped) |
| SEND text with a long non-Latin subject | Opens as "Subject.txt" | 🐞 [CORE-3](AUDIT.md#core-3): crash (confirmed) |
| Rename to a Unicode name | Renamed; Recents updated | ❌ |
| ZIP with UTF-8 names, and CP437 names (no UTF-8 flag) | Correct names | 🟡 CP437 is correct on API 37 (checked by hand). ❓ [FMT-5](AUDIT.md#fmt-5) on API 26 to 30 |

## F. Unicode document content

| Scenario | Expected | Coverage |
|---|---|---|
| Devanagari, CJK, Arabic and emoji text | Correct shaping and RTL | 🟡 Checked by hand (correct). ❌ Not automated |
| UTF-16 LE/BE with and without BOM | Decoded; editable only when it round-trips | ✅ `TextCodecTest` (with BOM). ❌ Without a BOM |
| windows-1251, GBK, Shift-JIS | Readable, read-only | 🐞 [CORE-11](AUDIT.md#core-11): mojibake |
| CSV with BOM and non-ASCII text | Correct | ✅ `CsvReaderTest` (BOM) |
| XLSX shared strings with CJK and phonetic runs | Phonetics skipped | ✅ `XlsxReaderTest` |
| XML Pretty view with `&amp;`, `&#169;` | Text unchanged | 🐞 [TXT-19](AUDIT.md#txt-19) |

## G. Low-memory devices

Run on an AVD with 2 GB RAM, `hw.ramSize=2048` and an API 26 image, plus one real low-end phone.

| Scenario | Expected | Coverage |
|---|---|---|
| Golden set (section A) | All open | ❌ |
| Large set (section B) | Clear messages, no crash | ❌ |
| Several PDFs opened in a row (Back each time) | Memory returns to its baseline; fd count stable | ❌ ([FMT-7](AUDIT.md#fmt-7)) |
| Phone under memory pressure (`am send-trim-memory <pkg> RUNNING_CRITICAL`) | No crash; caches dropped | ❌ |

## H. Android version compatibility

| API | Why it matters | Coverage |
|---|---|---|
| 26 to 27 (Android 8) | minSdk; HEIC and AVIF messages; OpenJDK-8 `ZipFile` (FMT-5) | ❌ The AVD no longer exists here. Re-create it, then run the golden set and `connectedDebugAndroidTest` |
| 28 to 30 | HEIC on; AVIF message; scoped-storage edge cases | ❌ |
| 31 to 33 | AVIF on; device-to-device transfer rules (CORE-7) | ❌ |
| 34+ | ZipPathValidator (FMT-12); predictive back | 🟡 API 37 passes the full suite |
| 16 KB page-size devices | Native libraries aligned | ✅ API 37 `ps16k` image runs; `zipalign -P 16` check in RELEASE_CHECKLIST |

## I. Rapid file switching

| Scenario | Expected | Coverage |
|---|---|---|
| Open a PDF, press Back immediately; repeat 50 times | No leaked descriptors or native memory | 🐞 [FMT-7](AUDIT.md#fmt-7) |
| Open file A, Back, then file B from Recents, quickly | B shown, never A's content | 🟡 Per-entry ViewModels (by design). ❌ No test |
| Back during a shared-file copy | File still lands in Recents | 🐞 [FEAT-12](AUDIT.md#feat-12) |
| SEND_MULTIPLE with the same URI twice | List shown | 🐞 [FEAT-13](AUDIT.md#feat-13): duplicate-key crash |

## J. App backgrounding

| Scenario | Expected | Coverage |
|---|---|---|
| Editor with unsaved edits → Home → kill the app → reopen and Edit | Draft banner; Restore works | 🟡 Passed by hand in the audit. ❌ Not automated |
| Video playing → Home → back | Paused, at the same position | ❌ |
| Video playing → screen times out | Screen stays on while playing | ❓ [FMT-11](AUDIT.md#fmt-11) |
| Rotate while playing | Keeps playing | 🐞 [FMT-2](AUDIT.md#fmt-2) (traced in code; the emulator decoder failed) |

## K. Process recreation and configuration change

Use `ActivityScenario.recreate()`, "Don't keep activities", and `adb shell am kill <pkg>` while the app is in the background.

| Scenario | Expected | Coverage |
|---|---|---|
| Editor: Save copy with the picker open while the activity is destroyed | Copy contains the edits; draft kept until success | 🐞 [FEAT-1](AUDIT.md#feat-1) (needs a runtime check) |
| Editor: rotation with unsaved edits | Edits kept, dirty state kept | 🟡 `liveText` (by design). ❌ [FEAT-5](AUDIT.md#feat-5) timing |
| Home: multi-select, then rotate | Selection kept | 🐞 [FEAT-9](AUDIT.md#feat-9) |
| Home: search, filter and sort, then process death | Restored | 🐞 [FEAT-18](AUDIT.md#feat-18) |
| Viewer: rename dialog open, then rotate | Typed name kept | 🐞 [FEAT-11](AUDIT.md#feat-11) |
| DOCX and HTML: scroll position after rotation or a paper change | Kept | 🐞 [FMT-10](AUDIT.md#fmt-10) |
| PDF: zoom and page after rotation | Kept | ❌ |

## L. File permission revocation

| Scenario | Expected | Coverage |
|---|---|---|
| Open a file from WhatsApp (temporary grant), close WhatsApp, reopen from Recents | Private copy opens (≤ 25 MB) | 🟡 `LibraryTest` (logic). ❌ End-to-end |
| Same, with the file over 25 MB or "Keep copies" off | "No longer available", with Remove | ❌ |
| Picked file (persisted grant), then the same file shared from another app | Persisted access kept | 🐞 [CORE-2](AUDIT.md#core-2) (`LibraryTest` currently asserts the bug) |
| Rename a picked file on Internal storage, reboot, reopen | Opens; second rename works | 🐞 [FEAT-2](AUDIT.md#feat-2) |
| Remove an added folder, then tap a Recents row from inside it | Row removed, or a clear message | 🐞 [CORE-8](AUDIT.md#core-8) |
| File deleted in another app while open in Folio | Error on next action, no crash | ❌ |
| Over 128 persisted grants (Android's limit) | Oldest released; newest still work | ✅ `LibraryTest` (budget) |
| `file://` URI from another app (readable path) | Opens or refuses, never crashes | 🐞 [CORE-1](AUDIT.md#core-1): crash (confirmed) |

## M. Upgrade from v1.0.0

The v1.0.0 APK (SHA-256 `9d4ce51d…0d9f17`) is the starting point for every future release.

1. Install v1.0.0 on a clean emulator. Open about 10 files of mixed types (some shared, so private copies exist), pin 2, add a folder, leave one editor draft, and change the theme, paper and font.
2. `adb install -r Folio-<new>.apk` (same key). Expect `Success`; `INSTALL_FAILED_UPDATE_INCOMPATIBLE` means the wrong key ([DIST-2](AUDIT.md#dist-2)).
3. Launch. Check that Recents, pins, folders, draft banner, settings and private copies are all present, and that nothing crashes on the first database access.
4. When the Room version changes, also run the `MigrationTestHelper` test ([CORE-9](AUDIT.md#core-9)). Today: ❌ the setup is missing.

Coverage: ❌ manual only. Do it for every release (it's in RELEASE_CHECKLIST).

## N. Release signing

| Check | Command | Coverage |
|---|---|---|
| Signed with the release key | `apksigner verify --print-certs Folio-x.apk`: SHA-256 must be `19bf74536960acb7421491ba83096bf9f68ac68ea7ad5547810c557c328fe218` | ❌ Not in docs/release.md ([DIST-2](AUDIT.md#dist-2)) |
| No dependency-metadata block | The script in section R lists only `0x7109871a`, `0x42726577` (and `0xf05368c0` if v3 is enabled) | 🐞 [DIST-1](AUDIT.md#dist-1) |
| Published SHA-256 matches the uploaded file | `sha256sum`; on Windows `certutil -hashfile Folio-x.apk SHA256` | 🟡 Manual (matches for v1.0.0) |
| Installs over the previous release | Section M | ❌ |

## O. Accessibility

| Scenario | Expected | Coverage |
|---|---|---|
| TalkBack: Settings switches and theme or paper chips | On/off and selected states announced | 🐞 [FEAT-6](AUDIT.md#feat-6) |
| TalkBack: PDF page | Page text read | 🐞 [FMT-8](AUDIT.md#fmt-8) |
| TalkBack: spreadsheet row past column T; open a cell | All columns read; cell opens | 🐞 [TXT-5](AUDIT.md#txt-5) |
| Headings navigation in Settings and Home | Headings available | 🐞 [FEAT-17](AUDIT.md#feat-17) |
| Font scale 2.0 and landscape: Home empty state, error screens | Everything reachable | ❓ [FEAT-8](AUDIT.md#feat-8) |
| Contrast of extension badges | ≥ 4.5:1 | 🐞 [FEAT-16](AUDIT.md#feat-16) |
| Accessibility Scanner pass on every screen | No errors | ❌ |

## P. Landscape and small screens

| Scenario | Expected | Coverage |
|---|---|---|
| Every viewer in landscape (a 360 × 640 dp phone, and a tablet at 1280 × 800) | Usable; PDF, slides and grid fill the width | 🐞 [FMT-1](AUDIT.md#fmt-1) for PDF. ❌ The rest is manual |
| Small screen (a 320 dp-wide AVD) with font scale 1.3 | Buttons don't overlap; dialogs fit | ❓ [FEAT-8](AUDIT.md#feat-8) |

## Q. Privacy guarantees (regression guard)

| Check | How | Coverage |
|---|---|---|
| No INTERNET, network-state or storage permission in the release APK | `aapt2 dump permissions Folio-x.apk` lists only `online.vishalsolanki.folio.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | 🟡 Manual (verified for v1.0.0) |
| No network attempts while opening HTML with remote resources, or Markdown with remote images | `adb logcat` plus an emulator proxy shows no connections | 🟡 Manual in the audit (Markdown image not loaded). ❌ No `SafeWebView` request-blocking test |
| Markdown or HTML links never leave the app without the user confirming | Confirmation dialog | 🐞 [TXT-6](AUDIT.md#txt-6) |
| Device-to-device transfer excludes Recents, copies and drafts | `bmgr` D2D test | 🐞 [CORE-7](AUDIT.md#core-7) |

## R. Helpers used in the audit

**Signing-block IDs** (DIST-1):

```python
import sys, struct
d = open(sys.argv[1], 'rb').read()
eocd = d.rfind(b'PK\x05\x06'); cd = struct.unpack('<I', d[eocd+16:eocd+20])[0]
assert d[cd-16:cd] == b'APK Sig Block 42'
size = struct.unpack('<Q', d[cd-24:cd-16])[0]   # block size, excluding its leading 8-byte size field
p = cd - size; ids = []                          # first id-value pair
while p < cd - 24:
    ln, = struct.unpack('<Q', d[p:p+8]); i, = struct.unpack('<I', d[p+8:p+12]); ids.append(hex(i)); p += 8 + ln
print(ids)   # 0x504b4453 = dependency metadata (must be absent)
```

**Opening a test file in a debug build without storage permissions:** push it into Folio's cache and open it through the cache root.

```powershell
adb push tall.pdf /data/local/tmp/
adb shell run-as online.vishalsolanki.folio cp /data/local/tmp/tall.pdf cache/
adb shell am start -a android.intent.action.VIEW -d file:///data/user/0/online.vishalsolanki.folio/cache/tall.pdf -t application/pdf -n online.vishalsolanki.folio/com.folio.app.ExternalOpenActivity
```

**Test-file recipes** (Python): the tall PDF (MediaBox `0 0 595 14400`), `dup.zip` / `slip.zip` / `cp437.zip`, the dark-gradient PPTX, and `links.md` are described in the reproduction steps of FMT-1, FMT-4, FMT-12, FMT-5, TXT-1 and TXT-6 in AUDIT.md. `ffmpeg -f lavfi -i sine=d=5 -c:a aac -f mp4 voice.m4a` makes an `isom` M4A (FMT-3).
