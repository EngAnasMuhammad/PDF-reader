# Native PDF Reader [![Android CI](https://github.com/EngAnasMuhammad/PDF-reader/actions/workflows/build.yml/badge.svg)](https://github.com/EngAnasMuhammad/PDF-reader/actions/workflows/build.yml)

An offline-first, high-performance Android PDF reader built with modern Android development standards. 

This project demonstrates the implementation of **Clean Architecture**, **Jetpack Compose**, and **C++ NDK integration** to handle 16KB memory page compliance securely and efficiently.

## Features
* **High-Performance Rendering:** Integrates a compiled C++ PDFium NDK engine.
* **Custom GPU Filters:** Uses `ColorMatrixColorFilter` for hardware-accelerated Sepia and Dimmed reading modes.
* **Advanced Touch Handling:** Custom `FrameLayout` wrapper to intercept gestures and enable double-tap zooming without conflicting with the PDF engine.
* **Native RTL Localization:** Full Arabic support utilizing Android's `AppCompatDelegate`.
* **Local Persistence:** Room database integration to remember recently opened files and specific page numbers.

## Tech Stack
* **UI:** Jetpack Compose, Material 3
* **Architecture:** Clean Architecture (Data, Domain, Presentation), MVVM, StateFlow
* **Dependency Injection:** Dagger Hilt
* **Local Storage:** Room Database
* **Engine:** C++ PDFium (marain87 fork)
* **CI/CD:** GitHub Actions (Automated Debug APK Builds)

## Screenshots
|     PDF Screen (Dark Mode)    |    My Documents (RTL Arabic, Light Mode)    |     Settings (RTL Arabic, Dark Mode)     |
|     :---:     |     :---: |     :---:     |
<img width="250" alt="image" src="https://github.com/user-attachments/assets/30ecb02f-fa88-497f-948f-5cf646a8fc56" />
<img width="250"  alt="image" src="https://github.com/user-attachments/assets/07906948-d7fd-4fac-b7ff-de4a412bea71" />
<img width="250" alt="image" src="https://github.com/user-attachments/assets/a92a399f-2939-4e2f-8300-27bd6e228a75" />


## Installation
1. Go to the [Releases](../../releases) tab.
2. Download the latest `app-release.apk`.
3. Install on any Android 8.0+ device.
