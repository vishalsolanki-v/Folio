# Releasing Folio on Google Play

Everything here is a draft for the owner to review. Nothing has been uploaded.

## 1. Decide before the first upload (can't be changed later)

- **Application ID.** It is `com.folio.app` today. Once published it is permanent, and `com.folio.app` may already be
  taken. Recommended: `com.<yourname>.folio`. Changing it means editing `applicationId` and `namespace` in
  `app/build.gradle.kts`, moving the Kotlin packages, and updating the FileProvider authority (it is built from the
  application ID, so it follows automatically). App data (recents, settings) is not tied to the ID.
- **Developer account type.** Personal accounts created after November 2023 must run a closed test with at least
  12 testers for 14 days before production. Organisation accounts are exempt.

## 2. Upload key (do this yourself; Folio never needs it in git)

1. Create a key once (keep the file and passwords somewhere safe, outside the repo):
   `keytool -genkeypair -v -keystore folio-upload.jks -keyalg RSA -keysize 4096 -validity 10000 -alias folio`
2. Create `keystore.properties` in the project root (it is git-ignored):
   ```
   storeFile=../folio-upload.jks
   storePassword=...
   keyAlias=folio
   keyPassword=...
   ```
3. Use **Play App Signing** (default for new apps): Google holds the app signing key, you keep the upload key.

Without `keystore.properties`, release builds are signed with the debug key so they can be tested locally. Those
builds must not be uploaded.

## 3. Build

```
./gradlew testDebugUnitTest lintDebug :app:licensee
./gradlew :app:generateReleaseBaselineProfile   # optional refresh; needs an API 33+ emulator with the samples
./gradlew bundleRelease                          # app/build/outputs/bundle/release/app-release.aab
```

Checks already done on the release build (see docs/decisions.md, M14): R8 keep rules, only an app-private
AndroidX permission in the merged manifest (no network, storage, wake lock), every native library 16 KB aligned,
licensee passing, every format opening on API 26 and 37.

## 4. Store listing (draft)

- **App name:** Folio: Open Any File
- **Short description (≤ 80):** Open any file. Offline. No ads.
- **Full description:**

  > Folio opens the files people send you, all in one calm app: PDF, Word, Excel, PowerPoint, text and code,
  > Markdown, JSON, CSV, web pages, photos, music, video and ZIP archives.
  >
  > Tap a file in WhatsApp, Gmail, Files or Downloads and choose Folio. Or open one from inside the app.
  >
  > • Read PDFs with search, zoom, dark paper and password support
  > • View Word documents, spreadsheets and slides in a clean, simple layout
  > • Edit text, code, Markdown, JSON and CSV safely, with drafts that survive a crash
  > • Look inside ZIP files and open what's in them
  > • Recents, pinned files and your own folders on one simple home screen
  >
  > Private by design: Folio has no internet access and uploads nothing. Files stay on your phone unless you
  > choose Share or Open with another app. No account, no ads, no tracking, nothing collected by Folio.

- **Category:** Productivity. **Tags:** document viewer, PDF reader, file viewer.
- **Screenshots:** Home (light), a PDF (dark paper), a spreadsheet, slides, the editor, Settings with the privacy
  card. Take them from the release build at 1080 × 2400.

## 5. Data safety form

- Does the app collect or share any of the required user data types? Folio has no network access and processes
  files locally. The user can explicitly transfer a selected document to another app with Share/Open with. Google
  Play's policy exempts reasonably expected user-initiated transfers from "sharing" in some cases; confirm the
  current form's treatment for Folio's document types and intended flows before submitting the declaration.
- Is all user data encrypted in transit? Folio itself has no network access. The system share sheet can transfer a
  user-selected file to another app; assess the relevant Play Console answer for that user-initiated flow.
- Do you provide a way for users to request that their data is deleted? Data stays on the device; Settings →
  "Clear recents and copies" removes everything Folio keeps, and uninstalling removes the rest.

## 6. Privacy policy

Host `docs/privacy-policy.md` (for example on GitHub Pages) and paste its URL in Play Console.

## 7. Content rating

Answer the questionnaire as a utility app with no user-generated content shared, no social features, no ads, no
purchases. Expected rating: Everyone / PEGI 3.
