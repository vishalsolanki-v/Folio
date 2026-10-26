# Decisions and deviations from plan.md

One line each: what, why.

- M0: AGP 9 built-in Kotlin is used (no `org.jetbrains.kotlin.android` plugin applied; it is declared `apply false` in the root only to pin the Kotlin version), because AGP 9 rejects applying it by default.
- M0: compileSdk/targetSdk 37 (latest stable platform in the SDK on 2026-10-27).
- M0: Toolchain lives on D: (`D:\Android\{Studio,Sdk,.gradle,.android}`, Studio config in `D:\Android\StudioData`); JDK = Android Studio's bundled JBR.
