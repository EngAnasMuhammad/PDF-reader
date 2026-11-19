# PDF Reader [![Android CI](https://github.com/EngAnasMuhammad/PDF-reader/actions/workflows/build.yml/badge.svg)](https://github.com/EngAnasMuhammad/PDF-reader/actions/workflows/build.yml) [![Version](https://img.shields.io/badge/version-2.0.0-blue.svg)](https://github.com/EngAnasMuhammad/PDF-reader/releases)

A lightweight, **100% offline-first** Android PDF reader, annotator, and document utility suite built with **Jetpack Compose**, **Clean Architecture**, and a native **C++ PDFium NDK** engine.

Designed for speed, low RAM usage, and strict privacy—everything runs locally on your device with zero cloud uploads, zero tracking, and zero broad storage permission bloat, while maintaining full **16KB memory page size compliance**.

---

## Features

### Reading & Viewing
- **Native C++ PDFium Rendering:** Fast, smooth rendering and zooming via a modified C++ PDFium NDK engine.
- **Custom GPU Reading Modes:** Hardware-accelerated `ColorMatrixColorFilter` support for **Sepia**, **Dimmed**, and **Dark** reading modes.
- **Adaptive Portrait & Landscape UI:** Responsive layouts with edge-to-edge inset handling, 2-pane side-by-side landscape sheets, and scroll-safe toolbars.
- **Viewing Modes & Preferences:** Continuous vertical and horizontal scroll options, and a keep-screen-awake toggle.
- **Bilingual & Theme Support:** Full bidirectional **English** & **Arabic (RTL)** localization and per-app **Theme (System / Light / Dark)** switching powered by `AppCompatDelegate`.

### Annotations & Digital Signatures
- **1-Tap Text Highlighting:** Select text directly in Reading Mode to apply multi-color pastel highlights in a single tap, with tap-to-recolor/delete and eraser support.
- **Freehand Pen & Highlighter:** Smooth Bézier ink strokes with customizable colors, stroke thickness, and a precision eraser.
- **Draggable & Resizable Text Boxes:** Place editable notes anywhere on a page with full **Arabic/RTL**, **LTR**, and **emoji** rendering.
- **Reusable Vector Digital Signatures:** Draw and save a personal signature on a dedicated canvas, stamp it onto any page, drag, and resize with locked aspect ratio.
- **Non-Destructive Editing & Offline Vector Export:** Full **Undo/Redo** history stored locally in Room, with 1-tap **Save Copy** exporting crisp, rotation-aware (`0° / 90° / 180° / 270°`) vector annotations via `PdfBox-Android` straight to `Downloads/PDFReader/`.

### Offline PDF Tools
- **Images to PDF:** Pick multiple images via Android's native Photo Picker (no storage permissions required), reorder pages, set a custom filename, and convert offline with automatic **EXIF orientation correction**, OOM-safe downscaling, and uniform `595 pt` (A4 width) page sizing for consistent viewing across all PDF readers.
- **Merge PDFs:** Combine multiple PDFs from different folders in any order using low-RAM temp-file streaming (`MemoryUsageSetting.setupTempFileOnly()`) with graceful handling for encrypted files.

### Organization & Persistence
- **Document Collections:** Group study materials, books, or work files into custom persistent collections.
- **Recent Files & Resume Reading:** Automatically tracks recently opened documents, remembers your last read page, and recovers cleanly from moved or deleted files.

---

## Engineering Challenges & Solutions

- **Rotation-Aware Vector Export (`0° / 90° / 180° / 270°`):**
    - *Challenge:* Android's top-left screen coordinates do not align with PDF's bottom-left coordinate system, and rotated PDF pages shift annotation positions on export.
    - *Solution:* Stored all strokes, text highlights, text boxes, and vector signatures in page-normalized (`0f..1f`) coordinates, then mapped them through a dedicated rotation-aware transform into `PdfBox-Android` cubic Bézier paths and `BlendMode.MULTIPLY` vector rectangles—verified with unit tests across all four page rotations.

- **OOM-Safe Offline Image Conversion & PDF Merging:**
    - *Challenge:* Converting 12MP+ camera photos or merging multi-megabyte PDFs in memory can trigger `OutOfMemoryError` crashes on low-RAM Android devices.
    - *Solution:* Implemented a two-pass `BitmapFactory` (`inSampleSize`) decoder with automatic `ExifInterface` orientation correction and immediate per-page `Bitmap.recycle()` after embedding. For merging, streamed documents via `MemoryUsageSetting.setupTempFileOnly()` into app cache with atomic `MediaStore` writes and guaranteed cleanup.

- **Cross-Viewer Page Consistency (`Images to PDF`):**
    - *Challenge:* Mixing portrait photos and wide banners in one PDF caused desktop/office viewers (such as WPS Office and Adobe Acrobat) to render tall pages as narrow columns due to shared document-wide zoom scaling.
    - *Solution:* Standardized generated page widths to `595 pt` (A4 width) while scaling height proportionally, ensuring uniform edge-to-edge rendering across both mobile and desktop PDF viewers.

- **Non-Destructive Editing Without Storage Bloat:**
    - *Challenge:* Modifying PDFs in place requires broad file permissions and destroys undo history, while exporting a new copy on every edit clutters the user's storage.
    - *Solution:* Persisted editable annotations locally in Room (with explicit schema migrations and zero broad storage permissions via SAF and Android's Photo Picker), offering instant **Undo/Redo** and on-demand **Save Copy** export to `Downloads/PDFReader/`.

---

## Tech Stack
- **UI:** Jetpack Compose, Material 3
- **Architecture:** Clean Architecture (`data`, `domain`, `presentation`), MVVM, `StateFlow` / Coroutines
- **Dependency Injection:** Dagger Hilt
- **Local Storage:** Room Database (with explicit schema migrations) & scoped local storage
- **PDF Engines:** Native C++ PDFium (NDK) for rendering + `PdfBox-Android` for offline vector export, merging, and image conversion
- **Image & EXIF:** `androidx.exifinterface` for image rotation handling
- **CI/CD:** GitHub Actions (Automated Android builds & unit tests)

---

## Installation
1. Go to the [Releases](../../releases) tab.
2. Download the latest `v2.0.0` release (`app-release.apk`).
3. Install on any Android 8.0+ device (API 26+).

---

## Author
Designed & Developed by **Anas Muhammad** — [GitHub](https://github.com/EngAnasMuhammad)
