## 第一性原理
请使用第一性原理思考。你不能总是假设我非常清楚自己想要什么和该怎么得到。请保持审慎，从原始需求和问题出发，如果动机和目标不清晰，停下来和我讨论。

## 代码规范
当你需要编写任何 TypeScript 代码时，强制使用 `typescript-project-specifications` skill。

代码请采用 UTF-8 阅读，中文不要当成乱码。`-Encoding UTF8`

如果遇到无法编译的情况，请跳过，让我来编译。

## 方案规范
当需要你给出修改或重构方案时必须符合以下规范：

- 不允许给出兼容性或补丁性的方案
- 不允许过度设计，保持最短路径实现且不能违反第一条要求
- 不允许自行给出我提供的需求以外的方案，例如一些兜底和降级方案，这可能导致业务逻辑偏移问题
- 必须确保方案的逻辑正确，必须经过全链路的逻辑验证

# Repository Guidelines

## Project Structure & Module Organization
YuanAssist is a single-module Android app. Main code lives in `app/src/main/java/com/example/yuanassist/`, split into `core/`, `ui/`, `model/`, `network/`, and `utils/`. UI resources are in `app/src/main/res/`; OCR and automation assets are in `app/src/main/assets/`. Local JVM tests live in `app/src/test/`, and device or emulator tests live in `app/src/androidTest/`. Treat `app/build/`, `app/release/`, `.gradle/`, and IDE folders as generated output and avoid editing or committing changes there.

## Build, Test, and Development Commands
Run all commands from the repository root with the Gradle wrapper.

- `./gradlew assembleDebug` builds the debug APK.
- `./gradlew assembleRelease` builds the release APK.
- `./gradlew testDebugUnitTest` runs JVM unit tests.
- `./gradlew connectedAndroidTest` runs instrumented tests on a connected device.
- `./gradlew lint` runs Android lint checks.
- `./gradlew clean` removes generated build artifacts.

On Windows, use `.\gradlew.bat assembleDebug`.

## Coding Style & Naming Conventions
Use Kotlin with 4-space indentation and Java 11 compatibility. Keep package placement consistent with the current layers. Use `UpperCamelCase` for classes, `lowerCamelCase` for methods and properties, and verb-driven method names such as `loadFragment` or `dispatchClick`. Resource files should stay `lowercase_snake_case`, for example `activity_main_dashboard.xml`. Keep comments short and add them only where control flow or coordinate logic is not obvious.

## Testing Guidelines
Use JUnit 4 for local tests and AndroidX JUnit/Espresso for instrumented coverage. Name test files `*Test.kt` or `*InstrumentedTest.kt` and mirror the production package path where possible. Prioritize unit tests for model parsing, coordinate calculations, and engine helpers; reserve instrumented tests for overlay UI, accessibility-service behavior, and navigation.

## Commit & Pull Request Guidelines
This workspace snapshot does not include `.git` history, so no repository-specific commit convention can be verified. Use short, imperative subjects with a clear scope, for example `core: fix target switch timing`. Pull requests should describe user-visible impact, list the commands or devices used for testing, link related issues, and include screenshots for UI changes. Do not include APKs, `local.properties`, or other generated/local files unless the change explicitly requires them.

## Security & Configuration Tips
Keep secrets, signing files, and local SDK paths out of version control. Dependency repositories are defined in `settings.gradle.kts`; change them deliberately and document why. Because the app uses accessibility, overlay, storage, and network permissions, call out any manifest or backend endpoint changes in the PR description.
