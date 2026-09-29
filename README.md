# Native PDF Reader

An offline-first, high-performance Android PDF reader built with modern Android development standards. 

This project demonstrates the implementation of **Clean Architecture**, **Jetpack Compose**, and **C++ NDK integration** to handle 16KB memory page compliance securely and efficiently.

## 📱 Features
* **High-Performance Rendering:** Integrates a compiled C++ PDFium NDK engine.
* **Custom GPU Filters:** Uses `ColorMatrixColorFilter` for hardware-accelerated Sepia and Dimmed reading modes.
* **Advanced Touch Handling:** Custom `FrameLayout` wrapper to intercept gestures and enable double-tap zooming without conflicting with the PDF engine.
* **Native RTL Localization:** Full Arabic support utilizing Android's `AppCompatDelegate`.
* **Local Persistence:** Room database integration to remember recently opened files and specific page numbers.

## 🛠️ Tech Stack
* **UI:** Jetpack Compose, Material 3
* **Architecture:** Clean Architecture (Data, Domain, Presentation), MVVM, StateFlow
* **Dependency Injection:** Dagger Hilt
* **Local Storage:** Room Database
* **Engine:** C++ PDFium (marain87 fork)
* **CI/CD:** GitHub Actions (Automated Debug APK Builds)

## 📸 Screenshots
*(Drag and drop 2-3 screenshots of your app here. Show the dark mode, the Arabic UI, and the PDF reader in action)*

## 🚀 Installation
1. Go to the [Releases](../../releases) tab.
2. Download the latest `app-release.apk`.
3. Install on any Android 8.0+ device.
