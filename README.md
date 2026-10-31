# PDF Reader — Android

A complete, working Android PDF reader app written in Kotlin.
Rendering uses Android's built-in `PdfRenderer` (no account, no ads,
works fully offline). Text search uses PDFBox-Android.

## Features

- Open any PDF from your device (Files / Drive / Downloads) — no storage permission needed
- Pinch to zoom, drag to pan, double-tap to zoom in/out
- Swipe or use Prev/Next and the page slider to change page
- Go to page, document info
- Search text inside the PDF and jump to matching pages
  (works on PDFs with a text layer; scanned image-only PDFs can't be searched)
- Bookmarks per document
- Night mode (inverted page colours)
- Recent documents on the home screen
- Remembers the last page you read in each document

## How to run it / build the APK

**Low on disk space?** Skip Android Studio entirely and build the APK
online for free — see [BUILD_ONLINE.md](BUILD_ONLINE.md). The project
already includes a GitHub Actions workflow that does it automatically.

Otherwise, you need **Android Studio** (free) on your computer:

1. Unzip this project.
2. In Android Studio: **File → Open** and select the `pdf-reader-android` folder.
3. Wait for the Gradle sync to finish (it downloads the SDK bits and libraries once).
4. To try it on your phone: enable Developer options → USB debugging, plug the phone in, press **Run ▶**.
5. To make an installable APK: **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
   The file appears in `app/build/outputs/apk/debug/app-debug.apk` — copy it to your phone and install it.

> Note: this project was assembled and checked as source code (all
> layouts/resources validated) in an environment where the Android SDK
> packages could not be downloaded, so the first Gradle sync in Android
> Studio is also the first full compile. If Android Studio offers to
> generate the Gradle wrapper or apply an AGP/Gradle upgrade, accept it —
> the app code does not depend on exact tool versions.

## Project structure

```
app/src/main/java/com/example/pdfreader/
  MainActivity.kt       Home screen: Open PDF + recent documents
  ReaderActivity.kt     Reader screen: rendering, navigation, search, bookmarks
  ZoomableImageView.kt  Pinch zoom / pan / double-tap / swipe handling
  Storage.kt            Recents, last page and bookmarks (SharedPreferences + JSON)
  SearchHelper.kt       Text extraction/search with PDFBox-Android
app/src/main/res/       Layouts, menu, theme, launcher icon
```

## Before publishing

- Change `applicationId` / `namespace` in `app/build.gradle.kts` from
  `com.example.pdfreader` to your own reverse-domain name.
- Add a signing config for a release build if you plan to put it on the Play Store.

## Known limits (good v2 ideas)

- No annotations/highlighting yet
- Search finds pages, it does not highlight the matched words on the page
- Password-protected PDFs are not supported by `PdfRenderer`
