# Gradle JDK (Java 25 incompatible)

**Gradle 8.13 supports Java 17–23 only.** Using Java 25 causes:
`Unsupported class file major version 69` and "maximum compatible Gradle JVM version is 23".

## What’s already configured

- **gradle.properties**: `org.gradle.java.home` points to Android Studio’s JBR (JDK 21) so the Gradle daemon uses a compatible JDK.
- **gradlew**: Script sets `JAVA_HOME` to a compatible JDK (Android Studio JBR or Homebrew JDK 21) so command-line builds use it.

## If sync still fails in the IDE

The IDE may be starting Gradle with Java 25. Do this:

1. **Stop Gradle daemons** (so the next run uses the JDK from `gradle.properties`):
   ```bash
   cd /path/to/StanfordScreenomics_External_062825
   chmod +x gradlew
   ./gradlew --stop
   ```

2. **Set “Gradle JDK” to JDK 21 in the IDE**
   - **Android Studio**: Settings → Build, Execution, Deployment → Build Tools → Gradle → **Gradle JDK** → choose **jbr-21** or **Embedded JDK** (not Java 25).
   - **IntelliJ**: Same path; choose a JDK 17–23 (e.g. **jbr-21** or **21**).
   - **Cursor / VS Code**: In Java / Gradle extension settings, set the JDK used for Gradle to JDK 21 (or 17–23). If you only have Java 25, install [Eclipse Temurin 21](https://adoptium.net/) and point the extension to it.

3. **Sync again** (e.g. “Sync Project with Gradle Files” or reload the project).

## If you don’t have Android Studio

Install JDK 21 (e.g. [Eclipse Temurin](https://adoptium.net/)) and in **gradle.properties** set:

```properties
org.gradle.java.home=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
```

(Adjust the path to match your install; on Homebrew it may be `/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`.)
