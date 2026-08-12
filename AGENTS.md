# Agent guide for Swift and SwiftUI

This repository contains an Xcode project written with Swift and SwiftUI. Please follow the guidelines below so that the development experience is built on modern, safe API usage.


## Role

You are a **Senior iOS Engineer**, specializing in SwiftUI, SwiftData, and related frameworks. Your code must always adhere to Apple's Human Interface Guidelines and App Review guidelines.


## Core instructions

- Target iOS 26.0 or later. (Yes, it definitely exists.)
- Swift 6.2 or later, using modern Swift concurrency. Always choose async/await APIs over closure-based variants whenever they exist.
- SwiftUI backed up by `@Observable` classes for shared data.
- Do not introduce third-party frameworks without asking first.
- Avoid UIKit unless requested.


## Swift instructions

- `@Observable` classes must be marked `@MainActor` unless the project has Main Actor default actor isolation. Flag any `@Observable` class missing this annotation.
- All shared data should use `@Observable` classes with `@State` (for ownership) and `@Bindable` / `@Environment` (for passing).
- Strongly prefer not to use `ObservableObject`, `@Published`, `@StateObject`, `@ObservedObject`, or `@EnvironmentObject` unless they are unavoidable, or if they exist in legacy/integration contexts when changing architecture would be complicated.
- Assume strict Swift concurrency rules are being applied.
- Prefer Swift-native alternatives to Foundation methods where they exist, such as using `replacing("hello", with: "world")` with strings rather than `replacingOccurrences(of: "hello", with: "world")`.
- Prefer modern Foundation API, for example `URL.documentsDirectory` to find the app’s documents directory, and `appending(path:)` to append strings to a URL.
- Never use C-style number formatting such as `Text(String(format: "%.2f", abs(myNumber)))`; always use `Text(abs(change), format: .number.precision(.fractionLength(2)))` instead.
- Prefer static member lookup to struct instances where possible, such as `.circle` rather than `Circle()`, and `.borderedProminent` rather than `BorderedProminentButtonStyle()`.
- Never use old-style Grand Central Dispatch concurrency such as `DispatchQueue.main.async()`. If behavior like this is needed, always use modern Swift concurrency.
- Filtering text based on user-input must be done using `localizedStandardContains()` as opposed to `contains()`.
- Avoid force unwraps and force `try` unless it is unrecoverable.
- Never use legacy `Formatter` subclasses such as `DateFormatter`, `NumberFormatter`, or `MeasurementFormatter`. Always use the modern `FormatStyle` API instead. For example, to format a date, use `myDate.formatted(date: .abbreviated, time: .shortened)`. To parse a date from a string, use `Date(inputString, strategy: .iso8601)`. For numbers, use `myNumber.formatted(.number)` or custom format styles.

## SwiftUI instructions

- Always use `foregroundStyle()` instead of `foregroundColor()`.
- Always use `clipShape(.rect(cornerRadius:))` instead of `cornerRadius()`.
- Always use the `Tab` API instead of `tabItem()`.
- Never use `ObservableObject`; always prefer `@Observable` classes instead.
- Never use the `onChange()` modifier in its 1-parameter variant; either use the variant that accepts two parameters or accepts none.
- Never use `onTapGesture()` unless you specifically need to know a tap’s location or the number of taps. All other usages should use `Button`.
- Never use `Task.sleep(nanoseconds:)`; always use `Task.sleep(for:)` instead.
- Never use `UIScreen.main.bounds` to read the size of the available space.
- Do not break views up using computed properties; place them into new `View` structs instead.
- Do not force specific font sizes; prefer using Dynamic Type instead.
- Use the `navigationDestination(for:)` modifier to specify navigation, and always use `NavigationStack` instead of the old `NavigationView`.
- If using an image for a button label, always specify text alongside like this: `Button("Tap me", systemImage: "plus", action: myButtonAction)`.
- When rendering SwiftUI views, always prefer using `ImageRenderer` to `UIGraphicsImageRenderer`.
- Don’t apply the `fontWeight()` modifier unless there is good reason. If you want to make some text bold, always use `bold()` instead of `fontWeight(.bold)`.
- Do not use `GeometryReader` if a newer alternative would work as well, such as `containerRelativeFrame()` or `visualEffect()`.
- When making a `ForEach` out of an `enumerated` sequence, do not convert it to an array first. So, prefer `ForEach(x.enumerated(), id: \.element.id)` instead of `ForEach(Array(x.enumerated()), id: \.element.id)`.
- When hiding scroll view indicators, use the `.scrollIndicators(.hidden)` modifier rather than using `showsIndicators: false` in the scroll view initializer.
- Use the newest ScrollView APIs for item scrolling and positioning (e.g. `ScrollPosition` and `defaultScrollAnchor`); avoid older scrollView APIs like ScrollViewReader.
- Place view logic into view models or similar, so it can be tested.
- Avoid `AnyView` unless it is absolutely required.
- Avoid specifying hard-coded values for padding and stack spacing unless requested.
- Avoid using UIKit colors in SwiftUI code.


## SwiftData instructions

If SwiftData is configured to use CloudKit:

- Never use `@Attribute(.unique)`.
- Model properties must always either have default values or be marked as optional.
- All relationships must be marked optional.


## Project structure

- Use a consistent project structure, with folder layout determined by app features.
- Follow strict naming conventions for types, properties, methods, and SwiftData models.
- Break different types up into different Swift files rather than placing multiple structs, classes, or enums into a single file.
- Write unit tests for core application logic.
- Only write UI tests if unit tests are not possible.
- Add code comments and documentation comments as needed.
- If the project requires secrets such as API keys, never include them in the repository.
- If the project uses Localizable.xcstrings, prefer to add user-facing strings using symbol keys (e.g. helloWorld) in the string catalog with `extractionState` set to "manual", accessing them via generated symbols such as  `Text(.helloWorld)`. Offer to translate new keys into all languages supported by the project.


## PR instructions

- If installed, make sure SwiftLint returns no warnings or errors before committing.

## XcodeBuildMCP
                                            
- If using XcodeBuildMCP, use the installed XcodeBuildMCP skill before calling XcodeBuildMCP tools.

## Android maimaid port

This section is the persistent source of truth for porting the current iOS maimaid app to Android.

### Technical baseline

- Use Kotlin, Jetpack Compose, MIUIX 0.9.3, Room, DataStore, and ONNX Runtime.
- Keep `compileSdk` and `targetSdk` at 37 or later.
- Use modern APK packaging with legacy packaging disabled.
- Maintain English, Simplified Chinese, Traditional Chinese, and Japanese resources together.

### UI requirements

- Use Backdrop for the Apple-style Liquid Glass floating tab bar.
- When MIUIX provides a suitable component, use it directly and remove parallel app-owned component implementations.
- Use MIUIX basic components directly. Use MIUIX squircle modifiers with a `1.2f` extension for app-owned rounded rectangular surfaces, and adapt `Path.addSquircleRect` only where an API requires a Compose `Shape`.
- Allow page content to extend behind the floating tab bar without an opaque bottom layer.
- Preserve global predictive back gestures: detail screens return to their source, non-home root tabs return to Home, and Home delegates back to the system.
- Match the established tab bar interaction: a gray default ripple, a briefly enlarged liquid selection during a short press, and an enlarged draggable ripple and bar during a long press on the selected tab.
- Use `com.kowx712.supermanager` on the PLZ110 device as the interaction reference where needed.

### OCR and vision requirements

- Use PaddleOCR PP-OCRv6 for OCR.
- Run vision models with ONNX Runtime.
- Apply class-aware NMS to relevant detector output.

### Data requirements

- Use Room and the current backend static-data protocol as Android data sources.
- Exclude the legacy iOS JSON data from the Android app.
- Preserve profile-scoped scores and play records throughout all repository, calculation, import, and synchronization flows.

### Current Android status

- The app skeleton, four root tabs, basic navigation, catalog synchronization, Room entities, basic settings, vision models, Liquid Glass tab bar, and Squircle components are present.
- Stage 1, Score domain foundation, is complete: score entry, update rules, score history, profile isolation, and focused unit/device validation are in place.
- Stage 2, Best 50, is complete: B35/B15 version classification, Rating calculation, Room-driven cache invalidation, list presentation, and image export are in place.
- The project currently uses `compileSdk = 37`, `targetSdk = 37`, MIUIX 0.9.3, Backdrop 2.0.0, and ONNX Runtime 1.28.0.
- PLZ110 runs Android 16 and is connected through ADB for installation, interaction checks, screenshots, logs, and performance validation.
- Existing Android worktree changes are active project work and must be preserved.

### Fixed implementation order

1. Score domain foundation: fully port `ScoreService`, score entry, update rules, score history, and multi-profile isolation.
2. Best 50: implement B35/B15 grouping, version boundaries, Rating calculations, cache invalidation, list presentation, and image export.
3. Song details: implement chart information, personal scores, play records, chart statistics, and the community-alias entry point.
4. Score query: implement combined filters, sorting, persisted filter settings, and empty-result states.
5. Rating recommendations: implement B35/B15 replacement thresholds, target achievements, recommendation lists, and caching.
6. Utilities: implement random song selection, constant tables, and constant-table image export.
7. Progress: implement plate progress, version groups, achievement conditions, Dan lists, and Dan details.
8. Community aliases: implement candidate submission, duplicate detection, voting board, daily quota, and approved-alias synchronization.
9. Accounts and synchronization: implement profiles, DivingFish/LXNS imports, backend authentication, incremental synchronization, conflict resolution, backup, and restore.
10. Complete OCR pipeline: implement camera and photo input, PP-OCRv6 detection and recognition, all three vision models, NMS, song/chart matching, result confirmation, and score persistence.
11. Final acceptance: complete all four localizations, accessibility, dark theme, offline and failure states, predictive back behavior, device performance validation, and visual regression checks.

For every stage, port the current iOS business contract and its test cases first, then implement the Android Repository, ViewModel, and Compose UI. Complete each stage with focused unit tests, `compileDebugKotlin`, `assembleDebug`, and PLZ110 device validation.
