// S5LAndBox: the Android port of S5LBox.
//
// Versions are pinned here rather than in a version catalog so that the CI
// workflow and a local build cannot disagree about which toolchain produced an
// APK. AGP 8.7.3 requires Gradle 8.9 and JDK 17; NDK r27 is the first release
// that emits 16 KB-aligned libraries by default for arm64.
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
