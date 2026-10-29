# Folio — universal offline file viewer & editor for Android

> Working name **Folio**, package `com.folio.app`. Rename before launch if wanted.
> This document is both the design spec and the build plan. It is written so a builder (human or Sonnet) can
> execute it milestone by milestone without asking questions. Anything marked **(builder: verify)** must be
> checked against the library's current README / Maven Central before use, because library APIs and versions move.

---

## 0. Context

**The problem.** Files arrive on the phone from everywhere: WhatsApp, email, chat apps, Downloads. Each type
needs a different app (a PDF app, an Office app, a text editor, an archive app...). That's frustrating. Existing
"all document reader" apps are full of ads, ask for scary permissions, crash on big files and look cluttered.

**The product.** One calm, premium Android app that:
- shows up in Android's **Open with** and **Share** lists for (almost) every file type, from any app;
- previews nearly any file instantly;
- lets you **edit only the formats it can edit without breaking them** (text-like formats); everything else is view-only, with no edit button;
- works **100% offline**. Folio has **no INTERNET permission**, account, analytics or ads, and does not upload files. Files stay on the phone unless the user explicitly chooses Share/Open with another app, which grants that selected app access;
- is **free on the Play Store**;
- looks like NotebookLM / Notion / Zoho Notebook: warm neutral surfaces, editorial typography, hairline borders, very few things per screen.

**What the user decided:**
- Native Android, published free on Play Store, no ads.
- Full editing wanted, but **edit button only for formats that are safely editable**; others view-only.
- **No AI**, fully offline, no automatic collection or transfer. Explicit user-selected Share/Open with flows can pass a file to the chosen app.
- **No storage permission.** Home = Recents + Pinned + folders the user adds once + the system "Open file" picker.
- Name: Folio.

**My calls (changeable):** Kotlin + Jetpack Compose + Material 3; single Gradle module with strict packages (not
multi-module); no DI framework (a small hand-written `AppContainer`); in-house XLSX and PPTX readers built on
Android's XmlPullParser (no Apache POI in v1.0); PDFium for PDF; Sora Editor for text/code; brand colors fixed
(no Material "dynamic color").

### Goals
1. Tap any common file in WhatsApp/Gmail/Files → Folio opens it in < 1 s (small files) with a clean preview.
2. Text-like files (TXT, MD, JSON, YAML, XML, CSV, code, HTML) can be edited and saved safely, preserving encoding and line endings.
3. A home screen a non-technical person understands in 3 seconds.
4. Never crash on big, broken or hostile files. Show a friendly message instead.

### Non-goals (v1.0)
- Editing DOCX/XLSX/PPTX (layout would break). Annotating PDFs (v2).
- Cloud storage, sync, accounts, AI, OCR.
- Being a full file manager (copy/move/rename across folders).

### Anti-goals (failure even if it "works")
- Any ad, nag screen, rating pop-up on first use, or subscription prompt.
- Any network access, tracking, or permission request at launch.
- Ever corrupting or silently overwriting a user's original file.
- Cluttered toolbars (more than 4 icons in a top bar), saturated rainbow file grids.

---

## 1. Format support by release

Legend: **V** = view, **E** = edit. License column is for the library used. Only permissive licenses
(Apache-2.0, MIT, BSD, Unlicense, OFL for fonts) are allowed, except **Sora Editor (LGPL-2.1)**: used
unmodified as a Maven dependency, credited on the licenses screen, with keep rules so it is replaceable (see §9).
**Never add GPL/AGPL code** (e.g. MuPDF is AGPL — forbidden).

### v1.0 (MVP)

| Family | Extensions | V/E | How | License |
|---|---|---|---|---|
| PDF | pdf | V | `io.legere:pdfiumandroid` (PDFium) render + text search; passwords supported | Apache-2.0/BSD |
| Word | docx | V | `org.zwobble.mammoth:mammoth` DOCX→HTML → `SafeWebView`. Fallback: in-house DOCX→HTML walker (§7.M10) | BSD-2 |
| Spreadsheet | xlsx | V | **In-house** reader (XmlPullParser) → Compose grid | — |
| Spreadsheet | csv, tsv | V + E | In-house CSV parser → same grid; edit as text | — |
| Slides | pptx | V | **In-house** reader (XmlPullParser) → positioned Compose slides + outline toggle | — |
| Text | txt, log, text | V + E | Sora Editor (read-only in viewer) | LGPL-2.1 (+ tm4e EPL-2.0, joni/jcodings MIT) |
| Markdown | md, markdown | V + E | `com.mikepenz:multiplatform-markdown-renderer-m3` (rendered) + Sora (source) | Apache-2.0 |
| Data/config | json, xml, yaml, yml, toml, ini, conf, properties, env | V + E | Sora + TextMate grammars | LGPL-2.1 / grammars: only MIT/Apache/BSD ones (check each grammar's source repo license before copying; skip any GPL grammar) |
| Code | ~60 extensions (kt, java, py, js, ts, c, cpp, go, rs, sh, sql, css, ...) | V + E | Sora + TextMate grammars | same |
| HTML | html, htm, xhtml | V + E (source) | `SafeWebView` (rendered) / Sora (source) | platform |
| Images | jpg, jpeg, png, webp, gif, bmp, heic*, heif*, avif*, svg | V | Coil 3 (+gif, +svg) + `me.saket.telephoto:zoomable-image-coil3` | Apache-2.0 |
| Audio | mp3, m4a, aac, wav, flac, ogg, opus | V | AndroidX Media3 ExoPlayer | Apache-2.0 |
| Video | mp4, mkv, webm, 3gp, mov | V | AndroidX Media3 ExoPlayer | Apache-2.0 |
| Archive | zip | V (browse + open entries) | `java.util.zip.ZipInputStream` (built-in) | platform |
| Anything else | * | V (info) | Unknown viewer: file info, "Open as text" if it looks like text, hex peek, "Open with another app" | — |

\* HEIC/HEIF needs API 28+, AVIF API 31+. On older phones show the "can't preview on this Android version" state.

### v1.1 (after MVP ships)
EPUB (Readium kotlin-toolkit, BSD-3) · RAR/7Z/TAR/GZ (libarchive `me.zhanghai.android.libarchive`, BSD-2) ·
password ZIP (zip4j, Apache-2.0) · EML (Apache Mime4j) · VCF (ez-vcard) · ICS (biweekly) · fonts TTF/OTF
(specimen via `Typeface.Builder`) · APK info (`PackageManager.getPackageArchiveInfo`, metadata only) ·
DOC/XLS/PPT/MSG text extraction (Apache POI `poi` + `poi-scratchpad`, spike first) · ODT/ODS/ODP (in-house,
content.xml) · RTF (in-house text stripper) · TIFF · PDF page thumbnails sheet · reading mode · text-to-speech
(Android `TextToSpeech`, offline voices) · JSON tree view · swipe between images in a folder.

### v2.0
PDF ink/highlight/text annotations + signatures, PDF merge/split/reorder (pdfbox-android, Apache-2.0) ·
biometric app lock (androidx.biometric) · full-text search across recent files (Room FTS) ·
camera scanner → PDF (CameraX + `PdfDocument`; **not** ML Kit, which depends on Play services downloads).

---

## 2. UX: screens and flows

 Four main screens. No bottom navigation bar, no drawer, no tabs.

```
Home ──tap file / Open file──► Viewer ──Edit pill──► Editor
 │  └─tap folder──► Folder ──tap file──► Viewer
 └─gear──► Settings ──► Open-source licenses
External app (WhatsApp/Gmail/Files) ──Open with / Share──► Viewer (Back returns to that app)
```

### 2.1 Home
- **Top row:** "Folio" wordmark (headlineMedium, left), Create file and Settings icon buttons (right). Create file offers plain text, Markdown, JSON and CSV, then asks Android where to save it; Folio seeds a small starter template and opens the supported editor.
- **Search field** (pill, 48dp tall, `surfaceContainerHigh`, placeholder "Search recent files"). Filters Recents + Pinned by file name, live.
- **Filter chips** (single row, horizontally scrollable): All · Pinned · Documents · Sheets · Text & code · Media · Archives. Default All.
- **Folders** (only if the user added any): a horizontal row of folder cards (icon + name), last card is "+ Add folder". If none, show a single subtle text button "+ Add a folder" under the chips.
- **Recents list:** `LazyColumn` of `FileRow`s. Sort by Recent, Oldest, Name (A–Z), or Size (largest first). Pinned files appear in a small "Pinned" section above Recents only for All + Recent.
- **FileRow** = extension badge, name, subtitle and overflow. Rename appears only when the document provider confirms it supports renaming and Folio holds durable write access; Pin/Unpin, Share and Remove from recents are also available.
  Swipe left = remove from recents (with Undo snackbar). Long-press or the selection control enters multi-select; Select all, Pin/Unpin, Share and Remove apply to the current visible results. Remove affects Folio's recent list, not the original files.
- **Primary action:** extended FAB bottom-right "Open file" (opens system picker `ACTION_OPEN_DOCUMENT` with `*/*`).
- **Empty state** (no recents): simple line illustration (vector, monochrome), title "Open anything", body "Tap any file in WhatsApp, Gmail or Files and choose Folio. Or open one from here.", button "Open file".

### 2.2 Folder
- Top bar: back, folder name, sort icon (Name / Newest).
- List of `FileRow`s (folders first, with a folder icon instead of badge). Tap folder → drill down (same screen, new route). Tap file → Viewer.
- Each nested folder remains one Back step; a top-bar shortcut returns directly to the selected folder's root.
- Overflow on the top bar: "Remove folder" (only at the folder root).
- Note: Android 11+ does not allow picking the root of `Download` as a folder; the picker explains this itself. Our empty-folder copy says: "Tip: pick a folder inside Downloads, like WhatsApp Documents."

### 2.3 Viewer (one screen for all formats)
- **Top bar (max 4 items):** Back · title (file name, 1 line, middle-ellipsized; tap → File info sheet) · Search icon (only if the format supports search) · ⋯ More.
- **Find mode:** search icon turns the top bar into: text field · "3 / 14" counter · ↑ ↓ · ✕.
- **Content:** the format's composable fills the screen. **Single tap on content toggles immersive mode** (hide top bar, pills and system bars with a 200ms fade).
- **Bottom-left pill (paged formats: PDF, PPTX):** "4 / 28". Tap → v1.0: a small dialog to type a page number; v1.1: thumbnails sheet.
- **Bottom-right pill (editable formats only):** "✎ Edit". Hidden for everything else. Hidden if the file > 5 MB or its text encoding can't be round-tripped safely (then the More sheet shows a disabled "Editing not available for this file" row explaining why).
- **More sheet** (modal bottom sheet, 24dp top corners), rows with icon + label:
 Share · Open in browser (for web, text, image and PDF types; launches an installed external browser handler) · Open with another app · Rename (only when the source provider allows it) · Print / Save as PDF (formats that support it) · Paper: Auto / Light / Sepia / Dark (segmented, for PDF, DOCX, HTML, text, markdown) · Pin / Unpin · File info.
- **File info sheet:** name, type (friendly, e.g. "Word document"), size, last modified (if known), source app ("From WhatsApp", from `Activity.referrer`), pages/sheets/slides count (if known), image dimensions + EXIF date/camera (images).
- **Password sheet** (encrypted PDF): "This PDF is password protected" + password field + Open. Wrong password → field error "Wrong password". Password stays in memory only.
- **Loading:** skeleton shimmer of the content area for > 150 ms loads. **Errors:** friendly full-screen state with icon, one sentence ("This file looks damaged, so Folio can't show it."), buttons "Open with another app" and "File info".

### 2.4 Editor (text-like formats only)
- **Top bar:** ✕ Close · file name · Undo · Redo · (Markdown/HTML: Preview toggle) · **Save** filled pill. Save label is "Save" if the source is writable, otherwise "Save copy".
- **Keyboard accessory bar** (40dp, above IME, horizontally scrollable): Tab ← → { } [ ] ( ) : " ' / = # - * ` (code/data/markdown files only).
- Closing with unsaved changes → dialog "Discard changes?" [Keep editing] [Discard] [Save].
- **Draft safety:** while dirty, write a draft to `filesDir/drafts/<sha1(uri)>.txt` every 5 s and on `onStop`. Reopening the same file with a newer draft → banner "You have unsaved changes from earlier" [Restore] [Discard]. Delete the draft after a successful save or discard.
- **Validation (JSON, YAML, TOML, XML):** on Save, parse; if invalid show a sheet "This JSON has an error on line 12: ..." [Fix it] [Save anyway].
- **After "Save copy":** the session switches to the new file (now writable), recents updated, label becomes "Save".

### 2.5 Settings (one scrolling screen, grouped)
- **Appearance:** Theme: System / Light / Dark / Black (AMOLED).
- **Documents:** Default paper: Auto / Light / Sepia / Dark.
- **Editor:** Font size (slider 12–20sp), Word wrap (switch), Line numbers (switch).
- **Storage:** "Keep copies of shared files for Recents" (switch, default on, explained: "Files opened from WhatsApp or email disappear after you close them. Folio keeps a private copy on this phone so they stay in Recents. Up to 300 MB."), "Clear recents and copies" (confirm dialog).
- **Privacy card** (static, `surfaceContainerLow` card): lock icon, "Folio has no internet access and sends nothing on its own. Files stay here unless you choose Share or Open with another app. Nothing is collected."
- **About:** version, "Open-source licenses" → licenses screen (AboutLibraries).

### 2.6 Copy style
Sentence case everywhere. Short, human sentences. No exclamation marks. Never say "error code". Buttons are verbs ("Open file", "Save copy").

---

## 3. Design system

No dynamic color. Brand palette is warm neutral, monochrome primary (Notion-like), one cobalt accent.
Elevation is **0 everywhere**; separation comes from tonal surfaces + 1dp hairline borders.

### 3.1 Color tokens (`core/designsystem/Color.kt`)
| Token | Light | Dark | Black (AMOLED) |
|---|---|---|---|
| background / surface | `#FAF9F5` | `#141413` | `#000000` |
| surfaceContainerLow (cards, rows on press) | `#F3F1EC` | `#1C1C1A` | `#0E0E0D` |
| surfaceContainer | `#EEECE6` | `#21211F` | `#141413` |
| surfaceContainerHigh (search, chips) | `#EAE7DF` | `#262624` | `#1C1C1A` |
| onSurface (ink) | `#191918` | `#ECEAE4` | `#ECEAE4` |
| onSurfaceVariant (metadata) | `#63625E` | `#9E9C96` | `#9E9C96` |
| primary (monochrome) | `#1F1F1E` | `#ECEAE4` | `#ECEAE4` |
| onPrimary | `#FAF9F5` | `#141413` | `#000000` |
| tertiary / accent (links, selection, active chip dot, find highlight) | `#2E5AAC` | `#8AB0F5` | `#8AB0F5` |
| outlineVariant (hairlines) | `#E2DFD7` | `#2E2E2B` | `#232321` |
| error | `#B3261E` | `#F2B8B5` | `#F2B8B5` |

**Paper themes** (document content only): Light `#FFFFFF` bg / `#191918` ink · Sepia `#F4ECD8` / `#3B2F1E` · Dark `#1A1A19` / `#E6E3DC`. "Auto" = Light paper in light theme, Dark paper in dark/black theme.

**Extension badge tints** (badge bg = tint at 14% alpha, text = tint; in dark theme lighten tint 25%):
PDF `#C0504D` · Word `#3B6EA5` · Sheet/CSV `#3D8B5F` · Slides `#C2793A` · Text/MD `#6B6A65` · Code/data `#7E57C2` · Image `#2F8F9D` · Audio/Video `#B0476E` · Archive `#B87D2D` · Unknown `#6B6A65`.

### 3.2 Typography (`Type.kt`)
Bundle fonts in `res/font` (all SIL OFL): **Inter** (UI + prose), **JetBrains Mono** (badges, code, numbers). v1.1 adds **Literata** for reading mode.

| Style | Font | Size / line height | Tracking |
|---|---|---|---|
| headlineMedium (screen titles, wordmark) | Inter SemiBold | 24 / 32 sp | -0.5 sp |
| titleLarge (sheet titles) | Inter SemiBold | 20 / 28 | -0.3 |
| titleMedium (file names, top bar title) | Inter Medium | 16 / 22 | -0.15 |
| bodyLarge (prose, markdown) | Inter Regular | 16 / 26 | 0 |
| bodyMedium (UI text) | Inter Regular | 14 / 20 | 0 |
| bodySmall (metadata) | Inter Regular | 13 / 18 | 0 |
| labelLarge (buttons) | Inter Medium | 14 / 20 | 0.1 |
| labelSmall (badges, page counter) | JetBrains Mono Medium | 11 / 16 | 0.2, `tnum` |
| code | JetBrains Mono Regular | 14 / 21 (editor setting overrides size) | 0 |

### 3.3 Spacing, shape, motion, icons
- Spacing scale (dp): 4, 8, 12, 16, 20, 24, 32, 48. Screen horizontal padding 20dp (phones); on width ≥ 600dp content max width 760dp, centered.
- Radii: badge 6dp · chips/search/pills fully rounded · cards & rows 12dp · sheets/dialogs 24dp (top corners for sheets).
- Borders: `BorderStroke(1.dp, outlineVariant)` on cards, search field (unfocused), floating pills.
- Touch targets ≥ 48dp. List row height 64dp.
- Motion: screen transitions = fade + 24dp slide, 220ms, `FastOutSlowInEasing`. Sheets use default M3. Pills fade/scale 150ms. No bouncy springs. Respect "Remove animations" system setting.
- Icons: **Material Symbols Outlined, weight 300, grade 0, 24dp**, imported as vector drawables into `res/drawable/ic_*.xml` (download SVGs from fonts.google.com/icons at weight 300; import via Android Studio Vector Asset). Do **not** add `material-icons-extended`.
- Edge-to-edge on (`enableEdgeToEdge()`), predictive back enabled.

### 3.4 Shared components (`core/designsystem/components/`)
`FolioTheme`, `FolioTopBar`, `FileRow`, `ExtBadge`, `SearchField`, `FilterChipsRow`, `PillButton` (filled/tonal/outline), `FloatingPill`, `FolioBottomSheet`, `SheetRow`, `EmptyState`, `ErrorState`, `LoadingSkeleton`, `FindBar`, `ConfirmDialog`. Every screen uses only these + plain M3 primitives.

---

## 4. Architecture

### 4.1 Project layout (single module `:app`)
```
Folio/
  settings.gradle.kts, build.gradle.kts, gradle/libs.versions.toml, gradle.properties
  app/
    build.gradle.kts, proguard-rules.pro
    src/main/AndroidManifest.xml
    src/main/res/{font, drawable, values, xml/file_paths.xml}
    src/main/assets/textmate/   (grammars + themes, copied from Sora Editor's sample app)
    src/main/java/com/folio/app/
      FolioApp.kt            Application; creates AppContainer
      AppContainer.kt        holds singletons: db, settingsRepo, fileResolver, localCopies, viewerRegistry
      MainActivity.kt        launcher; NavHost starting at Home
      ExternalOpenActivity.kt  Open-with / Share entry; NavHost starting at Viewer
      navigation/            Routes.kt (kotlinx-serialization @Serializable routes), FolioNavHost.kt
      core/designsystem/     Color, Type, Shape, Spacing, Theme, components/
      core/model/            FileFamily, OpenedFile, FormatInfo
      core/files/            FileResolver, MimeSniffer, TextCodec, FileSaver, LocalCopies, UriAccess, ZipSafety
      core/data/             FolioDatabase, RecentFile(+Dao), PinnedFolder(+Dao), SettingsRepository
      core/viewer/           FormatViewer, ViewerState, ViewerRegistry, SafeWebView, Printing
      feature/home/          HomeScreen, HomeViewModel
      feature/folder/        FolderScreen, FolderViewModel
      feature/viewer/        ViewerScreen, ViewerViewModel, MoreSheet, FileInfoSheet, PasswordSheet, PageJumpDialog
      feature/editor/        EditorScreen, EditorViewModel, AccessoryBar, Drafts
      feature/settings/      SettingsScreen, LicensesScreen
      formats/pdf/ docx/ sheet/ slides/ text/ html/ image/ media/ archive/ unknown/
    src/test/...             JVM unit tests (+ resources/corpus/)
    src/androidTest/...      instrumentation smoke tests (+ assets/samples/)
```
**Rule:** `formats/*` may depend on `core/*` only, never on `feature/*` or on each other. `feature/viewer` reaches formats only through `ViewerRegistry`.

### 4.2 Core types (`core/model`, `core/viewer`)
```kotlin
enum class FileFamily { PDF, WORD, SHEET, SLIDES, TEXT, MARKDOWN, CODE, HTML, IMAGE, AUDIO, VIDEO, ARCHIVE, UNKNOWN }

data class OpenedFile(
    val uri: Uri,               // content:// (or file:// for our own cache files)
    val name: String,           // display name incl. extension
    val extension: String,      // lowercase, no dot, "" if none
    val mime: String,           // final MIME after sniffing
    val family: FileFamily,
    val sizeBytes: Long,        // -1 if unknown
    val writable: Boolean,      // we can overwrite the source
    val sourceApp: String?,     // e.g. "WhatsApp" from Activity.referrer, null if unknown
)

enum class Paper { Auto, Light, Sepia, Dark }

@Stable class ViewerState {
    var query by mutableStateOf("")          // set by FindBar; "" = not searching
    var matchCount by mutableIntStateOf(0)   // set by the viewer
    var matchIndex by mutableIntStateOf(0)   // 0-based; FindBar ↑/↓ changes it, viewer scrolls to it
    var paper by mutableStateOf(Paper.Auto)
    var pageLabel by mutableStateOf<String?>(null)  // "4 / 28" for paged formats, else null
    var jumpToPage by mutableStateOf<Int?>(null)    // set by PageJumpDialog, viewer consumes and resets
    var immersive by mutableStateOf(false)
    var infoExtras by mutableStateOf<Map<String, String>>(emptyMap()) // e.g. "Pages" to "28"
}

interface FormatViewer {
    val families: Set<FileFamily>
    val supportsSearch: Boolean
    val supportsPaper: Boolean
    fun canEdit(file: OpenedFile): Boolean = false
    fun printJob(context: Context, file: OpenedFile): PrintDocumentAdapter? = null  // null = no print row
    @Composable fun Content(file: OpenedFile, state: ViewerState, onTap: () -> Unit, modifier: Modifier)
}

class ViewerRegistry(private val viewers: List<FormatViewer>, private val fallback: FormatViewer) {
    fun forFile(file: OpenedFile) = viewers.firstOrNull { file.family in it.families } ?: fallback
}
```
Each `Content` is responsible for its own loading/error UI (using the shared `LoadingSkeleton` / `ErrorState`)
and must do all IO on `Dispatchers.IO` inside `LaunchedEffect`/`produceState`. Errors become `ErrorState`,
never a crash: wrap engine calls in `runCatching` and also catch `OutOfMemoryError` around decoding.

### 4.3 Opening a file (`core/files/FileResolver`)
`suspend fun resolve(uri: Uri, intentMime: String?, referrer: String?): Result<OpenedFile>`
1. Query `OpenableColumns.DISPLAY_NAME` and `SIZE` (fallback: last path segment, -1).
2. Read the first 4 KB → `MimeSniffer.sniff(head, openStream = { contentResolver.openInputStream(uri) }, name)`.
3. Final MIME priority: sniffed (magic bytes) > extension map > intent MIME > `application/octet-stream`.
4. `FormatInfo.familyFor(mime, extension)` → family.
5. `writable = UriAccess.canWrite(uri)`.
6. `sourceApp`: `Activity.referrer` (may be null) → host = package name → look up a **hard-coded map** in `SourceApps.kt` (we can't query other apps' labels on API 30+ without `QUERY_ALL_PACKAGES`): `com.whatsapp`/`com.whatsapp.w4b` → WhatsApp, `com.google.android.gm` → Gmail, `org.telegram.messenger` → Telegram, `com.google.android.apps.nbu.files` → Files, `com.google.android.documentsui`/`com.android.documentsui` → Files, `com.android.chrome` → Chrome, `com.google.android.apps.docs` → Drive, `com.microsoft.office.outlook` → Outlook, `com.Slack` → Slack, `com.instagram.android` → Instagram, `com.facebook.orca` → Messenger, `com.google.android.apps.messaging` → Messages, `com.samsung.android.email.provider` → Samsung Email, `com.sec.android.app.myfiles` → My Files, `com.mi.android.globalFileexplorer` → File Manager. Unknown package → null (subtitle just omits it). Opened from our own picker/folder → null.

### 4.4 MIME sniffing (`MimeSniffer`, pure Kotlin, unit-tested)
- **Weak signatures** (`BM`, `FF Fx`) only count when the extension is not a known text/code extension; `BM` additionally requires a valid DIB header size (UInt32 LE at byte 14 ∈ {12, 40, 52, 56, 108, 124}). This stops "BM25 notes.txt" being treated as an image.
- `%PDF-` → pdf · `89 50 4E 47` → png · `FF D8 FF` → jpeg · `GIF8` → gif · `RIFF....WEBP` → webp · `....ftypheic|heix|mif1` → heic · `....ftypavif` → avif · `BM` (weak) → bmp · `ID3` → mp3 · `FF Fx` (weak) frame sync → mp3/aac · `fLaC` → flac · `OggS` → ogg · `RIFF....WAVE` → wav · `....ftyp(isom|mp4|M4V|qt)` → mp4 · `1A 45 DF A3` → mkv/webm · `52 61 72 21` → rar · `37 7A BC AF` → 7z · `1F 8B` → gzip · `D0 CF 11 E0` → OLE2 (doc/xls/ppt/msg → UNKNOWN family in v1.0, label "Older Office file").
- `50 4B 03 04` (ZIP): stream entry names with `ZipInputStream` (stop after 200 entries or 2 MB read):
  `word/document.xml` → docx · `xl/workbook.xml` → xlsx · `ppt/presentation.xml` → pptx · `mimetype`=`application/epub+zip` or `META-INF/container.xml` → epub (UNKNOWN in v1.0) · `AndroidManifest.xml` + `classes.dex` → apk (UNKNOWN in v1.0) · `content.xml` → odf (UNKNOWN in v1.0) · else zip.
- If nothing matched: extension map. If still nothing: **text heuristic** on the 4 KB head: no `0x00` bytes and ≥ 95% decodes as valid UTF-8 printable/whitespace → `text/plain` (TEXT family).
- `FormatInfo` holds one table: extension → (mime, family, friendly name, badge label, badge tint). ~120 extensions. Code extensions → CODE family; json/xml/yaml/toml/ini/etc → CODE family too (they share the Sora path) but friendly name "JSON file" etc.

### 4.5 Reading text safely (`TextCodec`)
```kotlin
data class TextDoc(val text: String, val charset: Charset, val bom: ByteArray?, val lineSep: String, val endsWithNewline: Boolean, val editable: Boolean)
```
- Detect BOM (UTF-8, UTF-16 LE/BE). Else try strict UTF-8 (`CodingErrorAction.REPORT`). If that fails, decode as windows-1252 for viewing and set `editable = false` (reason: "unusual text encoding").
- `lineSep`: the first of `\r\n` / `\n` / `\r` found (default `\n`). Normalize to `\n` for the editor; on save convert back.
- `encode(doc, newText)`: restore line separators, re-add BOM, encode with same charset. **Unit test: decode→encode with no edits returns byte-identical output** for UTF-8, UTF-8+BOM, UTF-16LE+BOM, CRLF, no trailing newline.
- Viewer limit: files > 5 MB show the first 5 MB + a bottom banner "Showing the first 5 MB" with "Load more" (+5 MB each); editing disabled for > 5 MB.

### 4.6 Saving (`FileSaver`)
- `UriAccess.canWrite(uri)`: `checkCallingOrSelfUriPermission(uri, FLAG_GRANT_WRITE_URI_PERMISSION) == GRANTED` **and** (if `DocumentsContract.isDocumentUri`) `DocumentFile.fromSingleUri(...).canWrite()`. Our own cache copies count as **not** writable (they are copies).
- **Overwrite (writable)** — `"wt"` truncates the original before the copy, so protect it:
  1. Encode new content to `cacheDir/save/<id>.new`.
  2. Copy the **current original** bytes to `cacheDir/save/<id>.bak` (skip if > 50 MB; editing is capped at 5 MB anyway).
  3. `contentResolver.openOutputStream(uri, "wt")`, copy `.new` in, flush, close.
  4. Read the URI back and compare length + SHA-256 with `.new`.
  5. Success → delete `.new`, `.bak` and the draft. Failure at 3 or 4 → try to write `.bak` back with `"wt"`; keep the draft; show "Folio couldn't save over this file. Your changes are safe." with [Save copy].
  If `"wt"` itself throws (`IllegalArgumentException`/`FileNotFoundException`/`SecurityException`), do **not** fall back to `"w"`; go straight to the failure message.
- **Save copy:** `ActivityResultContracts.CreateDocument(mime)` with the original name; write to the returned URI; `takePersistableUriPermission(read|write)`; switch the editor session to the new `OpenedFile` (writable = true); update recents (old entry stays).
- After any save, re-read size for recents.

### 4.7 Recents, pinned, folders (`core/data`)
Room DB `folio.db`:
```kotlin
@Entity(tableName = "recent") data class RecentFile(
  @PrimaryKey val uri: String, val name: String, val mime: String, val family: String,
  val sizeBytes: Long, val lastOpenedAt: Long, val pinned: Boolean,
  val sourceApp: String?, val localCopyPath: String?, val persisted: Boolean)
@Entity(tableName = "folder") data class PinnedFolder(@PrimaryKey val treeUri: String, val name: String, val addedAt: Long)
```
- On every open: upsert recent (keep `pinned`). Cap 500 rows (delete oldest unpinned).
- **Persisted-permission budget:** Android caps persisted URI grants per app (128 on API 26–29, 512 on API 30+) and silently drops the oldest. Keep `persisted = true` rows (+ folders) under **100** (API < 30) / **450** (API ≥ 30): when over budget, for the oldest unpinned persisted recents call `contentResolver.releasePersistableUriPermission(...)` and set `persisted = false` (they then rely on a local copy if one exists, else they drop out of recents). Pinned files and folders are never released.
- **Persistable vs temporary URIs:** when opened via our own picker/folder, `takePersistableUriPermission` (read, + write if granted) → `persisted = true`. When opened via Open-with/Share (WhatsApp, Gmail), permission is temporary: if "Keep copies" is on and size ≤ 25 MB, copy to `filesDir/recent_copies/<sha1(uri)>/<name>` (`LocalCopies`), store path. LRU-evict copies over 300 MB total (never evict pinned).
- Opening a recent: if `persisted` → original URI; else if `localCopyPath` exists → `Uri.fromFile(copy)` (read-only); else show "This file is no longer available" with [Remove from recents].
- Folders: `ACTION_OPEN_DOCUMENT_TREE` → `takePersistableUriPermission` → insert. List children with `DocumentsContract.buildChildDocumentsUriUsingTree` + one `contentResolver.query` (columns: DOCUMENT_ID, DISPLAY_NAME, MIME_TYPE, SIZE, LAST_MODIFIED). Do **not** use `DocumentFile.listFiles()` (slow).
- `SettingsRepository`: Preferences DataStore keys: `theme`, `paper`, `keepCopies`, `editorFontSp`, `wordWrap`, `lineNumbers`.

### 4.8 Entry points & manifest
- `MainActivity`: launcher, `singleTop`. Hosts NavHost(start = Home).
- `ExternalOpenActivity`: `exported="true"`, **standard launch mode, no taskAffinity change** → opens on top of the sender's task, so Back returns to WhatsApp/Gmail. Hosts NavHost(start = Viewer). Its "Back" from Viewer calls `finish()`.
  - `ACTION_VIEW` / `ACTION_EDIT`: `intent.data`.
  - `ACTION_SEND`: `IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)` or `clipData[0]`. If only `EXTRA_TEXT` (no stream): write it to `cacheDir/shared_text/<timestamp>.txt` (name = `EXTRA_SUBJECT` sanitized, else "Shared text.txt") and open that file URI like any other (read-only copy → editing uses Save copy).
  - `ACTION_SEND_MULTIPLE`: show a simple sheet listing the files (name + badge); tap one → Viewer. Back from that viewer returns to the list.
  - `file://` URIs: try to open; on `SecurityException` show "The other app shared this file in an old way Folio can't read. Try Share instead."
- **No** `INTERNET`, `READ/WRITE_EXTERNAL_STORAGE`, `MANAGE_EXTERNAL_STORAGE`, `REQUEST_INSTALL_PACKAGES`, `QUERY_ALL_PACKAGES`. Add `tools:node="remove"` entries for INTERNET and ACCESS_NETWORK_STATE in case a library merges them in.
- `<queries>` not needed (we use a chooser for "Open with another app").
- `FileProvider` (`${applicationId}.files`) exposing `cacheDir/` and `filesDir/recent_copies/` for Share / Open-with-another-app of local files.
- `android:enableOnBackInvokedCallback="true"`, `android:largeHeap="true"` on application.

Intent filters on `ExternalOpenActivity` (no `BROWSABLE`, so we never hijack web links). `ACTION_EDIT` is
registered **only** for types we can edit; view-only types get `ACTION_VIEW` only. (`text/*` also catches
`text/vcard` / `text/calendar`; in v1.0 they open as readable text, which is fine.)
```xml
<intent-filter> <!-- EDIT: editable types only -->
  <action android:name="android.intent.action.EDIT"/>
  <category android:name="android.intent.category.DEFAULT"/>
  <data android:scheme="content"/>
  <data android:mimeType="text/*"/>
  <data android:mimeType="application/json"/> <data android:mimeType="application/xml"/>
  <data android:mimeType="application/x-yaml"/> <data android:mimeType="application/yaml"/>
  <data android:mimeType="application/toml"/> <data android:mimeType="application/javascript"/>
  <data android:mimeType="application/xhtml+xml"/> <data android:mimeType="application/x-sh"/>
</intent-filter>
<intent-filter> <!-- VIEW: explicit types -->
  <action android:name="android.intent.action.VIEW"/>
  <category android:name="android.intent.category.DEFAULT"/>
  <data android:scheme="content"/> <data android:scheme="file"/>
  <data android:mimeType="application/pdf"/>
  <data android:mimeType="application/vnd.openxmlformats-officedocument.wordprocessingml.document"/>
  <data android:mimeType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"/>
  <data android:mimeType="application/vnd.openxmlformats-officedocument.presentationml.presentation"/>
  <data android:mimeType="text/*"/>
  <data android:mimeType="application/json"/> <data android:mimeType="application/xml"/>
  <data android:mimeType="application/x-yaml"/> <data android:mimeType="application/yaml"/>
  <data android:mimeType="application/toml"/> <data android:mimeType="application/javascript"/>
  <data android:mimeType="application/xhtml+xml"/> <data android:mimeType="application/x-sh"/>
  <data android:mimeType="image/*"/> <data android:mimeType="audio/*"/> <data android:mimeType="video/*"/>
  <data android:mimeType="application/zip"/> <data android:mimeType="application/x-zip-compressed"/>
  <data android:mimeType="application/octet-stream"/>
</intent-filter>
<intent-filter> <!-- everything else -->
  <action android:name="android.intent.action.VIEW"/>
  <category android:name="android.intent.category.DEFAULT"/>
  <data android:scheme="content"/> <data android:scheme="file"/>
  <data android:mimeType="*/*"/>
</intent-filter>
<intent-filter>
  <action android:name="android.intent.action.SEND"/>
  <action android:name="android.intent.action.SEND_MULTIPLE"/>
  <category android:name="android.intent.category.DEFAULT"/>
  <data android:mimeType="*/*"/>
</intent-filter>
```
(v1.1 adds epub, rar, 7z, eml, vcf, ics, font, apk, legacy Office and ODF MIME types to the first filter.)

### 4.9 Navigation
Type-safe Navigation Compose routes (`@Serializable`): `Home`, `Folder(treeUri: String, docId: String, title: String, rootTitle: String)`, `Viewer(uri: String, mimeHint: String?)`, `Editor(uri: String)`, `Settings`, `Licenses`. Only URI strings travel in routes; `ViewerViewModel` re-resolves the `OpenedFile` with `FileResolver` (cheap). **Never put file contents in routes, Bundles or SavedState.**

ViewModels are created with `viewModel(factory = ...)` using `AppContainer` (via `(LocalContext.current.applicationContext as FolioApp).container`). No Hilt/Koin.

### 4.10 Security rules (apply everywhere)
- **SafeWebView** (`core/viewer/SafeWebView.kt`, the only WebView in the app): `javaScriptEnabled=false`, `blockNetworkLoads=true`, `allowFileAccess=false`, `allowContentAccess=false`, `domStorageEnabled=false`, `setGeolocationEnabled(false)`; load with `loadDataWithBaseURL(null, html, "text/html", "utf-8", null)`; `WebViewClient.shouldOverrideUrlLoading` returns true for everything (links do nothing in v1.0; v1.1: confirm dialog → open in browser via ACTION_VIEW). Find via `findAllAsync` + `setFindListener`. Print via `createPrintDocumentAdapter(name)`.
- **ZipSafety:** every extracted entry: reject names containing `..` segments or absolute paths; `canonicalPath` must start with target dir + separator. Abort if total extracted bytes > 500 MB or a single entry's uncompressed:compressed ratio > 100 with size > 50 MB. Max 10,000 entries listed.
- **XML parsing:** use `XmlPullParserFactory.newInstance().newPullParser()` with `FEATURE_PROCESS_DOCDECL=false`; never resolve external entities. Cap any single XML part read at 50 MB.
- Catch `OutOfMemoryError` around image/PDF decoding and show "This file is too large to preview on this phone."

---

## 5. Tech stack (`gradle/libs.versions.toml`)

Use the **latest stable** versions at build time (builder: verify each on Maven Central / Google Maven; versions
below are the minimums seen in research and may be outdated).

- Android Gradle Plugin (latest stable), Kotlin 2.x + `org.jetbrains.kotlin.plugin.compose`, KSP, `kotlin.plugin.serialization`.
- `compileSdk`/`targetSdk` = latest stable API offered by the installed Android Studio; `minSdk = 26`; Java 17.
- Compose BOM (latest), `material3`, `activity-compose`, `navigation-compose` (≥ 2.8, type-safe), `lifecycle-viewmodel-compose`, `lifecycle-runtime-compose`.
- `androidx.room:room-runtime/room-ktx/room-compiler(ksp)`, `androidx.datastore:datastore-preferences`.
- `androidx.documentfile:documentfile`, `androidx.core:core-ktx`, `androidx.print:print` (PrintHelper for images), `androidx.exifinterface:exifinterface`.
- `org.jetbrains.kotlinx:kotlinx-serialization-json` (routes + JSON validation), `kotlinx-coroutines-android`.
- PDF: `io.legere:pdfiumandroid` (builder: verify artifact + API; fallback documented in M9).
- DOCX: `org.zwobble.mammoth:mammoth` (≥ 1.8; builder: verify it runs on Android in M1).
- Text: `io.github.Rosemoe.sora-editor:bom`, `:editor`, `:language-textmate` (builder: verify artifact names).
- Markdown: `com.mikepenz:multiplatform-markdown-renderer-m3` (+ `-coil3` for images).
- Images: `io.coil-kt.coil3:coil-compose`, `coil-gif`, `coil-svg` (**not** `coil-network-*`), `me.saket.telephoto:zoomable-image-coil3`.
- Media: `androidx.media3:media3-exoplayer`, `media3-ui`.
- Licenses: `com.mikepenz.aboutlibraries.plugin` + `aboutlibraries-compose-m3`.
- License guard: `app.cash.licensee` Gradle plugin: `allow("Apache-2.0")`, `MIT`, `BSD-2-Clause`, `BSD-3-Clause`, `Unlicense`, `EPL-2.0` (needed for tm4e, which Sora's TextMate module uses; weak per-file copyleft, fine unmodified), plus `allowDependency(...)` entries for each Sora artifact (LGPL-2.1). Many POMs lack SPDX ids: add `allowUrl(...)` for their license URLs after checking each one by hand. Never allow GPL/AGPL. Build fails on anything else.
- Tests: JUnit 4, `kotlinx-coroutines-test`, `net.sf.kxml:kxml2` (testImplementation, gives XmlPullParser on the JVM), `androidx.test.ext:junit`, `androidx.compose.ui:ui-test-junit4`.
- Release: `isMinifyEnabled = true`, `isShrinkResources = true`, Baseline Profile (`androidx.profileinstaller`) in M14.

---

## 6. Prerequisites (do once, M0)
- Android Studio (latest stable) with Android SDK, platform-tools, an emulator image **API 26** and one **latest API** (Google Play image so "Files by Google" exists).
- JDK 17 (bundled with Android Studio is fine).
- Project folder: `D:\code\Document App\Folio` (the repo root). `git init` there.
- Put real sample files in `D:\code\Document App\sample-files\` (not in the repo): at least one of each v1.0 format, including: a 200-page PDF, a password PDF, a DOCX with tables+images, an XLSX with 3 sheets and dates, a PPTX with images, a 20 MB log, a UTF-16 txt, a CRLF txt, a broken (truncated) PDF, a zip with folders. Push to emulator with `adb push sample-files /sdcard/Download/samples`.

---

## 7. Milestones (build in this order; each ends green)

Every milestone ends with: `./gradlew testDebugUnitTest lintDebug assembleDebug` passing, the app launching on both
emulators, and the milestone's acceptance checks done by hand. Commit after each milestone
(`feat(m3): ...`). Don't start the next milestone with failing tests.

### M0 — Skeleton
1. New Android Studio project "Empty Activity (Compose)", package `com.folio.app`, minSdk 26. Convert to version catalog, Kotlin 2.x compose plugin, KSP, serialization plugin.
2. Create the package tree from §4.1 (empty files where needed). Add `FolioApp` + `AppContainer`, register in manifest.
3. Manifest per §4.8 (both activities, filters, FileProvider, removed permissions). `ExternalOpenActivity` just shows the received URI as text for now.
4. Add licensee + aboutlibraries plugins.
**Accept:** app installs; launching shows a placeholder; `adb shell am start -a android.intent.action.VIEW -d content://media/external/file/1 -t application/pdf` (or opening any file from Files app) shows Folio in the chooser and displays the URI. `./gradlew :app:licensee` passes.

### M1 — Risky dependency check (keep as androidTests)
Add PDFium, mammoth, Sora, markdown renderer, Coil, Media3. Put tiny samples in `src/androidTest/assets/samples/`. Write one instrumentation test per library that opens a sample and asserts a basic result (PDF page count > 0 and a page renders to a non-blank bitmap; mammoth returns HTML containing a known word; Sora `CodeEditor` accepts text with a TextMate language; markdown renderer composes; ExoPlayer prepares an mp3). Run on **API 26 and latest**.
Also check **16 KB page-size compliance** (required by Play for apps targeting Android 15+): build a release APK and confirm every `.so` is 16 KB aligned (Android Studio APK Analyzer shows an alignment warning, or run `zipalign -c -P 16 -v 4 app-release.apk`), and run the PDF test on a 16 KB-page emulator image.
**Accept:** all pass on both. **If mammoth fails on Android**, mark it and plan the in-house DOCX walker in M10. **If pdfiumandroid fails, isn't available, or its `.so` isn't 16 KB aligned**, switch to `android.graphics.pdf.PdfRenderer` (reduced features: password PDFs and text search/highlight only on API 35+ via its newer APIs; older devices show "Password-protected PDFs need Android 15+" and hide the search icon for PDFs) and note it in `docs/decisions.md`.

### M2 — Design system
Implement §3 fully: colors (3 schemes), typography with bundled Inter + JetBrains Mono, shapes, spacing object, `FolioTheme(themeMode)`, and all components in §3.4 with `@Preview`s (light + dark). Import the needed icons (list: arrow_back, search, more_vert, more_horiz, settings, close, edit, undo, redo, save, share, print, open_in_new, info, push_pin, folder, folder_open, add, sort, expand_less, expand_more, lock, visibility, description, image, music_note, movie, inventory_2 (archive), code, table, slideshow, picture_as_pdf, error, check).
**Accept:** a debug-only `DesignCatalogScreen` (reachable from Settings in debug builds) shows every component in both themes; looks calm, no Material purple anywhere.

### M3 — Core files (pure logic + unit tests first)
Implement `FormatInfo`, `MimeSniffer`, `TextCodec`, `ZipSafety`, `UriAccess`, `FileResolver`, `LocalCopies`, `FileSaver` (§4.3–4.6, 4.10). TDD the pure parts:
- `MimeSniffer`: every magic number in §4.4; zip→docx/xlsx/pptx/plain zip (build zips in-test with `ZipOutputStream`); text heuristic (UTF-8 text → text; random bytes → unknown); wrong extension (`photo.pdf` that is a PNG → image/png).
- `TextCodec`: byte-identical round trips (§4.5); invalid UTF-8 → `editable=false`.
- `ZipSafety`: `../evil`, `/abs`, `a/../../b` rejected; normal paths accepted.
**Accept:** all unit tests green.

### M4 — Data
Room DB + DAOs (`RecentDao`: observeAll(): Flow, upsert, setPinned, delete, trimTo(500), `PinnedFolderDao`), `SettingsRepository` (DataStore, `Flow<Settings>`), wire into `AppContainer`. Unit test DAO with in-memory Room (androidTest).
**Accept:** tests green.

### M5 — Home, Folder, Settings
- `HomeViewModel`: combines recents Flow + folders Flow + search text + chip filter → `HomeUiState`. Chip → family mapping: Documents = PDF, WORD, SLIDES, MARKDOWN, HTML; Sheets = SHEET; Text & code = TEXT, CODE; Media = IMAGE, AUDIO, VIDEO; Archives = ARCHIVE.
- `HomeScreen` per §2.1 incl. empty state, swipe-to-remove + Undo, overflow menu, FAB → `OpenDocument` (persist permission, then navigate to Viewer).
- "Add folder" → `OpenDocumentTree` → persist → insert.
- `FolderScreen` per §2.2 with DocumentsContract query on IO, sort, drill-down.
- `SettingsScreen` per §2.5, writing to `SettingsRepository`; theme applies immediately app-wide. `LicensesScreen` with AboutLibraries.
**Accept:** add a folder → browse into subfolders; open file via FAB → (viewer placeholder) → it appears in Recents with right badge/size/time; pin/unpin/remove/undo work; search + chips filter; theme switch works incl. Black.

### M6 — Viewer shell + Unknown viewer + Open-with flow
- `ViewerViewModel`: resolve file (`FileResolver`), record recent (+ local copy if temporary), expose `ViewerUiState(file, viewer, loading/error)`, handle "file no longer available".
- `ViewerScreen` per §2.3: top bar, FindBar wired to `ViewerState`, More sheet (Share, Open with another app [`Intent.createChooser` + `EXTRA_EXCLUDE_COMPONENTS` = our two activities, grant read], Print [if `printJob != null`, `PrintManager.print`], Paper [if `supportsPaper`], Pin, File info), File info sheet (incl. `state.infoExtras`), immersive toggle (`WindowInsetsControllerCompat`), page pill + `PageJumpDialog`, Edit pill (if `viewer.canEdit(file)` → navigate Editor).
- `ExternalOpenActivity` real handling of VIEW/EDIT/SEND/SEND_MULTIPLE/EXTRA_TEXT (§4.8).
- `UnknownViewer` (fallback): big badge, friendly type ("ZIP-based file", "Older Office file", "Unknown file"), size, "Open as text" (forces TEXT family, read-only), hex peek of first 512 bytes (JetBrains Mono, 16 bytes per row), "Open with another app" button.
**Accept:** opening any file from WhatsApp-like flow (Files app → tap → Folio) shows the Unknown viewer for everything (other viewers not yet registered); Back returns to Files app; Share sheet flow works with 1 and 3 files; recents show "Files"/"WhatsApp" source.

### M7 — Text family + Editor
- `formats/text/TextViewer` (TEXT, CODE): `TextCodec.decode` on IO → Sora `CodeEditor` in `AndroidView`, `editable=false`, TextMate language chosen by extension (map ext → grammar scope; default plain text), Sora color scheme built from our tokens + paper theme, font JetBrains Mono, word wrap / line numbers from settings. Search: Sora's `EditorSearcher` → matchCount/matchIndex. Large-file banner (§4.5). `canEdit` = size ≤ 5 MB && decode `editable`. Print: render text into HTML `<pre>` and print via an offscreen SafeWebView.
- JSON/XML viewer extra (More sheet row "Pretty view"): pretty-print into the viewer only (not saved).
- `formats/text/MarkdownViewer` (MARKDOWN): rendered view with mikepenz renderer in a `LazyColumn`-backed scroll (paper colors, bodyLarge type); top-bar-adjacent segmented toggle "Preview / Source" (Source uses TextViewer). Search in Preview mode: v1.0 searches the source and switches to Source view on first match (keep simple). Images in markdown: local relative images are not resolvable from a content URI → show alt text.
- `feature/editor`: `EditorViewModel` (load TextDoc, dirty tracking, undo/redo via Sora, save via `FileSaver`, drafts via `Drafts`), `EditorScreen` per §2.4 incl. accessory bar, discard dialog, validation sheet (JSON via kotlinx-serialization `Json.parseToJsonElement`; XML via XmlPullParser walk; YAML/TOML: v1.0 skip validation), Save copy flow, predictive back handling.
- CSV: viewer comes in M11; but CSV `canEdit` → Editor works now (text editing).
**Accept:** open/edit/save a UTF-8 LF file and a UTF-16 CRLF file → bytes identical except the edit (check with `adb pull` + diff); WhatsApp-like read-only file → "Save copy" → new file in Downloads, editor now says "Save"; kill app mid-edit (`adb shell am kill`) → reopen → Restore banner works; invalid JSON → validation sheet; 20 MB log → first 5 MB + Load more, no Edit pill.

### M8 — Images and media
- `ImageViewer`: Coil 3 `ImageLoader` with GIF (`AnimatedImageDecoder` API 28+, `GifDecoder` below) + SVG decoders; telephoto `ZoomableAsyncImage` (double-tap zoom, pinch, fling); black/neutral background per paper; EXIF + dimensions into `infoExtras`; print via `PrintHelper`. HEIC/AVIF below required API → friendly state.
- `MediaViewer`: video = `PlayerView` in `AndroidView` (controller themed minimal; fullscreen = immersive); audio = Compose card (cover art via `MediaMetadataRetriever.embeddedPicture`, title/artist, play/pause, seek slider, time labels mono). Release player in `DisposableEffect`; pause on `ON_STOP`.
**Accept:** 50 MP JPEG zooms smoothly without OOM; GIF animates; SVG renders; MP4 plays/rotates; MP3 shows art and seeks; leaving screen stops audio.

### M9 — PDF
- `PdfViewer` with a `PdfDocumentHandle` wrapper (only class touching the PDFium API; one `Mutex`, single-thread dispatcher `Dispatchers.IO.limitedParallelism(1)`).
- Open: `openFileDescriptor(uri, "r")`; if the provider isn't seekable (`statSize == -1` or open fails), copy to `cacheDir/open/` first with a progress indicator. Password-protected → PasswordSheet → reopen with password.
- Layout: `LazyColumn` of pages, each `Box` with the page aspect ratio, 8dp gaps, page bitmap rendered at `containerWidthPx` (ARGB_8888), `LruCache<Int, Bitmap>` sized to 1/6 of `maxMemory`. Placeholder = paper-colored box. Render on IO when an item becomes visible; cancel when it leaves.
- Zoom: telephoto `Modifier.zoomable(rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 4f)))` around the LazyColumn; after a zoom gesture settles at factor `z > 1.2`, re-render visible pages at `containerWidthPx * z` (cap 4096 px wide) and swap in.
- Page pill: `state.pageLabel = "${first visible + 1} / $count"`; consume `state.jumpToPage`.
- Search: on query change (debounce 300 ms), iterate pages, extract text per page, collect matches (page, charIndex); `matchCount`; on `matchIndex` scroll to page and draw highlight rects (accent at 35% alpha) from PDFium's char-box API. If char rects prove unreliable, scroll to the page only (acceptable for v1.0; note it).
- Dark/sepia paper: apply `ColorFilter.colorMatrix` (invert + warm tint for Dark; multiply sepia for Sepia) to page bitmaps.
- `infoExtras`: Pages, Title, Author, Producer (metadata).
- Print: `PrintDocumentAdapter` that streams the original bytes (`onWrite` copies the input stream).
**Accept:** 200-page PDF scrolls smoothly, memory stays under ~250 MB in Android Studio profiler; zoom sharp after settle; password PDF works + wrong password message; truncated PDF shows ErrorState; search finds and jumps; dark paper readable; print preview opens.

### M10 — DOCX + HTML (SafeWebView)
- `SafeWebView` (§4.10) composable: `html: String`, `paper: Paper`, `state: ViewerState`; wires find (query→`findAllAsync`, `setFindListener`→matchCount, matchIndex→`findNext`), tap → `onTap` (use a `GestureDetector` on the WebView for single-tap-confirmed), print adapter.
- `HtmlTemplates.wrap(bodyHtml, paper, typography)`: one CSS template: Inter via `@font-face` from `file:///android_res/font/...` (allowed even with file access off? builder: verify; else embed the font as base64 `data:` URL once and cache the string), max-width 760px, 20px padding, `line-height:1.62`, tables with hairline borders and horizontal scroll wrappers, images `max-width:100%`, paper colors as CSS variables.
- `DocxViewer`: mammoth `DocumentConverter().convertToHtml(stream)` on IO (images as base64 data URIs — default), wrap, render. Warnings from mammoth ignored. Show a subtle top chip "Simplified layout" (tap → explanation sheet: "Folio shows Word files in a clean reading layout. Some formatting may look different."). If M1 showed mammoth fails: implement `DocxHtmlWriter` with XmlPullParser over `word/document.xml` (+ `word/_rels/document.xml.rels`, `word/media/*`, `word/numbering.xml` for bullets): paragraphs, headings (pStyle Heading1–6/Title), bold/italic/underline/strike, hyperlinks (text only), bullet/numbered lists, tables (incl. gridSpan), inline images (base64), page breaks → `<hr>`.
- `HtmlViewer` (HTML family): rendered in SafeWebView (original HTML, scripts stripped by regex `(?is)<script.*?</script>` as defense-in-depth on top of JS-off); "Source" toggle shows TextViewer; `canEdit` like text.
- Print for both via WebView adapter.
**Accept:** DOCX with headings/tables/images reads well in light, sepia, dark; find works with counter; HTML with external images shows no network attempt (no INTERNET permission anyway) and doesn't crash; print preview works.

### M11 — Sheets (XLSX + CSV)
- `XlsxReader` (pure Kotlin + XmlPullParser, unit-tested with xlsx files built in-test):
  `xl/workbook.xml` (sheet names, r:ids) + `xl/_rels/workbook.xml.rels` (targets) → sheet list; `xl/sharedStrings.xml` (incl. rich text runs `<r><t>`); `xl/styles.xml` (`cellXfs` → numFmtId, custom `numFmts`); `xl/worksheets/sheetN.xml`: `<c r="B3" t="s|str|inlineStr|b|n|e" s="..."><v>`/`<is><t>`. Formula cells: show cached `<v>`. Dates: numFmtId 14–22, 45–47 or custom format containing d/m/y/h/s (ignoring quoted text) → convert Excel serial (1900 system, handle `date1904`) → `yyyy-MM-dd` / `HH:mm`. Numbers: show as plain (`toPlainString`, strip trailing zeros), percent formats ×100 + "%". Merged cells (`<mergeCell ref>`): show value in the top-left cell only (v1.0).
  Limits: stop at 100,000 rows or 1,000 columns per sheet → banner "Showing the first 100,000 rows". Read the sheet XML streaming; store rows as `List<Array<String?>>` (sparse rows ok).
- `CsvReader`: RFC 4180 (quotes, escaped quotes, newlines in quotes), delimiter auto-detect from first 5 lines among `,` `;` `\t` `|`; encoding via `TextCodec`.
- `SheetGrid` composable (shared): column header row (A, B, C...) and row-number column both **sticky**; one horizontal `ScrollState` shared by header + all rows; vertical `LazyColumn`; column widths = clamp(max chars in first 200 rows × 8dp + 24dp, 64dp, 280dp); cell text 1 line ellipsized, JetBrains Mono for numbers right-aligned, Inter for text left-aligned; hairline grid; tap cell → bottom sheet with full cell value (selectable, Copy). Sheet tabs: bottom pill row (scrollable) when > 1 sheet. Search: scan cells, matches ordered row-major, scroll to row + horizontal scroll to column, highlight cell with accent border.
- `infoExtras`: Sheets, Rows (current sheet).
**Accept:** 3-sheet XLSX with dates/percent/shared strings shows correct values (compare with LibreOffice); 50k-row CSV scrolls smoothly; semicolon CSV detected; search jumps to cell; CSV Edit pill opens text editor.

### M12 — Slides (PPTX)
- `PptxReader` (pure Kotlin + XmlPullParser, unit-tested):
  `ppt/presentation.xml` (`p:sldSz cx/cy`, `p:sldIdLst` order) + rels → slide paths. For each slide parse `p:spTree`: `p:sp` (text shapes: `a:xfrm a:off/a:ext`, paragraphs `a:p` → runs `a:r a:t` with `a:rPr sz/b/i/u` and `a:solidFill a:srgbClr`, paragraph `a:pPr algn`, bullets → "•"), `p:pic` (`a:blip r:embed` → rels → `ppt/media/*` bytes), `p:grpSp` (apply `a:chOff/a:chExt` transform, one level deep is enough; deeper groups flatten without transform), slide background `p:bg/p:bgPr/a:solidFill`. **Placeholder positions:** if a shape has no `a:xfrm`, look up `p:nvPr/p:ph type/idx` in the slide's layout (via slide rels), then the layout's master; if still missing, mark "unpositioned". Default text sizes: title 32pt, body 18pt when `sz` missing. Tables/charts/SmartArt: v1.0 render a light rounded box with label "Table"/"Chart"/"Diagram" at their position (extract table cell text into the outline).
- `SlidesViewer`: `HorizontalPager`; each page = `Box` with slide aspect ratio, white (or bg) fill, hairline border, 12dp radius; children positioned with `Modifier.offset` + `size` from EMU→fraction of slide size × box size; text size = pt × (boxWidthPx / slideWidthPt) converted to sp-independent px (use `with(density){ px.toSp() }`, ignore font scale for slides); images via Coil from bytes. Unpositioned shapes stacked from top. Outline toggle (More sheet "Outline view") shows a LazyColumn: per slide "Slide 3" + title + bullet text + thumbnails of its images. Page pill "3 / 24". Chip "Simplified preview" like DOCX. Search: over slide text; jump to slide; highlight shape with accent border. Paper theme: not applied to slides (slides keep their own colors); `supportsPaper=false`.
**Accept:** a typical 20-slide deck (title + bullets + images) shows recognizable slides with correct order and image placement; outline view lists all text; broken PPTX → ErrorState.

### M13 — Archives (ZIP)
- `ArchiveViewer`: list entries with `ZipInputStream` on IO (UTF-8; on `ZipException`/`IllegalArgumentException` retry with `Charset.forName("CP437")`), build a virtual folder tree; UI like FolderScreen (in-content navigation with breadcrumb row "archive.zip › docs › 2024"; system Back goes up a level before leaving). Rows show size; header shows "128 files · 45 MB".
- Tap a file → stream to that entry, extract to `cacheDir/archive/<sha1(uri)>/<sanitized path>` through `ZipSafety` → open in Viewer with `Uri.fromFile` (read-only, `sourceApp` = archive name). Don't add extracted files to recents.
- Encrypted entries (`ZipException: encrypted`) → friendly "Password-protected ZIPs are coming soon."
- "Extract all" is v1.1.
- Clean `cacheDir/archive` and `cacheDir/open` on app start if older than 24 h.
**Accept:** zip with nested folders browses; open a PDF and a DOCX inside it; `../` zip from the test corpus refuses that entry with a message; zip bomb sample stops with a message.

### M14 — Polish & release prep
- Motion per §3.3; shimmer skeletons; empty/error states everywhere; check TalkBack labels (badges read as "PDF file"), font scale 200%, landscape, a 7" tablet emulator (centered 760dp column), RTL pseudo-locale.
- Performance: Baseline Profile for Home → open PDF → scroll; cold start of ExternalOpenActivity < 500 ms on a mid emulator; verify no work on main thread (StrictMode in debug).
- R8: release build with `isMinifyEnabled=true`; add keep rules: Sora (`-keep class io.github.rosemoe.sora.** { *; }` + TextMate/`org.eclipse.tm4e.**`), mammoth (`-keep class org.zwobble.mammoth.** { *; }`), PDFium JNI classes per its README, kotlinx-serialization rules. **Re-run the full manual acceptance list on the release build.**
- Verify permissions: `apkanalyzer manifest permissions app/build/outputs/apk/release/app-release.apk` must print nothing network/storage related (and `licensee` passes).
- **Application ID is permanent once published:** before the first Play upload, confirm the final ID with the owner (recommended `com.<ownername>.folio`; `com.folio.app` may be taken). Changing it means renaming the package, the FileProvider authority and reinstalling (DB/DataStore names are not tied to it).
- **Personal Play developer accounts** created after Nov 2023 must run a closed test (currently ≥ 12 testers for 14 days) before production access; plan for that window. Organisation accounts are exempt.
- Play listing: app name, short description ("Open any file. Offline. No ads."), screenshots (light + dark), Data safety answers reviewed against Folio's user-initiated Share/Open with flows and current Play policy, privacy policy page (one paragraph, hosted on e.g. GitHub Pages), content rating questionnaire, target API = latest required.
**Accept:** release AAB builds; full checklist in §8 passes on release build on API 26 + latest.

---

## 8. Verification checklist (run on release build, API 26 and latest)
1. Fresh install → Home empty state → Open file → each sample format opens with the right viewer.
2. From Files app and from a messaging app (Gmail or any chat app on the emulator): tap PDF, DOCX, XLSX, PPTX, JSON, MP4, ZIP → Folio in "Open with" → opens → Back returns to the source app.
3. Share 3 images from Gallery to Folio → picker list → open each.
4. Edit + save round trips (UTF-8 LF, UTF-16 CRLF, read-only → Save copy); `adb pull` + `fc /b` (Windows) or `cmp` shows only intended changes.
5. Kill during edit → Restore draft.
6. Recents survive reboot for picker-opened files; WhatsApp-style temporary files reopen from local copies; "Clear recents and copies" empties `filesDir/recent_copies`.
7. Theme System/Light/Dark/Black and paper Light/Sepia/Dark on PDF, DOCX, text, markdown.
8. Hostile corpus: zero-byte files, wrong extensions, truncated PDF/XLSX/MP4, zip-slip, zip bomb, 100 MP image, 20 MB log → no crash, friendly messages.
9. Airplane mode changes nothing (app never needs network).
10. `./gradlew testDebugUnitTest connectedDebugAndroidTest licensee` green.

---

## 9. Risks & mitigations
| # | Risk | Mitigation |
|---|---|---|
| 1 | Library doesn't work on Android (mammoth, PDFium artifact) | M1 instrumentation checks on API 26 + latest; documented fallbacks (in-house DOCX walker; PdfRenderer) |
| 2 | Corrupting user files on save | Only text formats editable; byte-exact codec tests; temp-file-then-"wt" write; no "w" fallback; Save copy for read-only; drafts |
| 3 | OOM on big files | Size gates (5 MB text edit, row caps), LRU bitmap cache, PDF tile re-render cap 4096px, catch OOM → friendly state, largeHeap |
| 4 | Hostile files (zip slip/bomb, XXE, HTML/SVG scripts) | §4.10 rules; JS off, no network permission, no file access in WebView; doc-decl off; corpus tests |
| 5 | DOCX/PPTX fidelity complaints | View-only, "Simplified layout/preview" chip explains it, Open with another app always one tap away |
| 6 | LGPL (Sora) compliance — relinking is hard to guarantee once R8 merges it into dex | Unmodified dependency, keep rules (no obfuscation of Sora), license + source link in Licenses screen, written offer of source in the licenses screen; licensee allow-list only Sora artifacts + EPL tm4e. If this is ever judged unacceptable: publish Folio's own source (removes the issue) or replace the editor with a Compose `BasicTextField` + own highlighter |
| 11 | 16 KB page-size requirement fails because of PDFium `.so` | Checked in M1; PdfRenderer fallback |
| 12 | Persisted URI grants silently dropped (128/512 cap) | Budget + release in §4.7 |
| 7 | A dependency adds INTERNET permission | `tools:node="remove"` + apkanalyzer check in M14 |
| 8 | R8 breaks reflection-heavy libs in release | Keep rules (M14) + full manual pass on release build |
| 9 | Temporary URIs (WhatsApp) vanish from Recents | Local copies (≤ 25 MB, 300 MB LRU), clear "no longer available" state |
| 10 | Play policy | No special permissions, no APK install, honest listing; Data safety "no data collected" |

---

## 10. Notes for the builder (Sonnet)
- Work strictly milestone by milestone; read §2 (UX) and §3 (design) before any UI work and use only the shared components.
- When a library API in this doc differs from the real one, follow the library's README and keep the wrapper interface from this doc.
- Record every deviation from this plan in `docs/decisions.md` (one line each: what, why).
- Put this plan in the repo as `docs/plan.md` at M0 so it's versioned with the code.
- Keep the UI rules: ≤ 4 top-bar items, one primary action per screen, sentence-case copy, no ads/nags, no new permissions.
