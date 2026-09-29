# Native PDF Reader (Android)

A high-performance, production-ready Android PDF Reader built with Kotlin, Jetpack Compose, and Clean Architecture.

This project bridges modern declarative UI with a hardware-accelerated C++ PDF engine (PDFium), ensuring smooth 60fps rendering for massive documents while strictly adhering to Android's modern 16 KB memory page size requirements.

##  Key Features
*   **Hardware-Accelerated Rendering:** Smooth tile-based loading for 300+ page textbooks without UI lag.
*   **Custom Reading Modes:** Instant GPU-level ColorMatrix offloading for Dark, Sepia, and Dimmed reading themes.
*   **Smart Gesture Interception:** Custom touch event pipelines wrapper (`FrameLayout`) that enables double-tap zooming while preserving the engine's internal swipe gestures.
*   **Persistent State Management:** Room database caching that remembers the exact page you left off on, managed via the Storage Access Framework (SAF).
*   **Native Localization:** Full RTL (Right-to-Left) Arabic support seamlessly integrated via Android's `AppCompatDelegate` and `locales_config.xml`.
*   **Quality of Life:** Hardware screen-lock management ("Keep Awake") and horizontal/vertical scroll toggles.

##  Architecture & Tech Stack
This application is strictly structured using **MVVM and Clean Architecture**, completely decoupling the Android framework from business logic.

*   **UI:** Jetpack Compose (Declarative UI), Material 3
*   **Architecture:** MVVM, Clean Architecture (Domain/Data/Presentation layers)
*   **Dependency Injection:** Dagger Hilt
*   **Asynchronous Programming:** Kotlin Coroutines & StateFlow
*   **Local Storage:** Room Database, SharedPreferences
*   **Core Engine:** C++ PDFium (via AndroidPdfViewer)

##  Technical Highlights
*   **Jetpack Compose Interoperability:** Implemented `AndroidView` with strict separation between the `factory` (one-time C++ engine initialization) and `update` (dynamic UI state changes) blocks to prevent wasteful recompositions.
*   **Repository Pattern:** Extracted all `Context`, `ContentResolver`, and URI parsing logic into isolated Data Repositories, leaving the Domain and ViewModel layers 100% pure Kotlin and highly testable.
*   **Race Condition Prevention:** Engineered strict StateFlow emissions to prevent UI threading collisions when reading database indices during file initialization.

##  Getting Started
To clone and run this project locally:
1. Open Android Studio and select **File > New > Project from Version Control**.
2. Paste the repository URL and clone.
3. Sync the project with Gradle files.
4. Run on any emulator or physical device running Android 8.0 (API 26) or higher.

##  Acknowledgments
*   PDF rendering is powered by the [marain87 fork of AndroidPdfViewer](https://github.com/marain87/AndroidPdfViewer), which provides the critical NDK recompilation required for Android 15's 16 KB memory page compliance.