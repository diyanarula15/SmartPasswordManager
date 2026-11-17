# Verification record

The workspace has a separate ignored `verification/` Android application. It includes
this module via `project(":feature-tools").projectDir = file("../feature-tools")`.
It supplies only a launcher activity, four demo navigation destinations, and a theme
wrapper observing the same injected settings repository. It adds no authentication
or vault implementation. Do not copy it into the production application.

Environment: JDK 17, Gradle 8.10.2, AGP 8.7.3, Kotlin 2.0.21, SDK 35, min SDK 26.
The local SDK is `/opt/homebrew/share/android-commandlinetools`.

Commands (run from the USS workspace, adjust JAVA_HOME/SDK paths on another machine):

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./verification/gradlew -p verification :feature-tools:testDebugUnitTest :app:assembleDebug
./verification/gradlew -p verification :feature-tools:connectedDebugAndroidTest
./verification/gradlew -p verification :feature-tools:lintDebug :feature-tools:assembleRelease
```

Results will be recorded here after completion. Independent agents have executed
pure Kotlin/JUnit repository tests; Android build/instrumentation results are only
reported after the integrated commands finish.
