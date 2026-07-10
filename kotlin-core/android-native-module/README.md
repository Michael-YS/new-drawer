# android-native-module

Android-side counterpart to `:server-ktor`. Provides:

- `SafFileSystemGateway` — production implementation of
  `com.drawer.core.scanner.FileSystemGateway` against
  [Storage Access Framework](https://developer.android.com/guide/topics/providers/document-provider).
  This is the only module in the rewrite allowed to import
  `android.*` per refactor-plan §3.5.

- `ImageOrganizerTurboModule` — thin wrapper that exposes the
  gateway methods to the React Native runtime.

Package namespace: `com.drawer.android.saf`. The source root was
renamed from `com.drawer.native` because `native` is reserved in
Kotlin (used by `expect`/`actual`).

## Testing

The `SafFileSystemGateway` depends on Android system services
(`DocumentFile` provider, `ContentResolver`) and cannot be exercised
from a JVM unit test. The contract test under `src/test/`
(`AndroidScannerContractTest`) proves the shared `:core-scanner`
`scanImagesFlow` works against the same `FileSystemGateway`
interface that `SafFileSystemGateway` implements.

Instrumented tests against real SAF behaviour must live in
`src/androidTest/` and require a connected device or emulator with
`android.permission.READ_MEDIA_IMAGES` granted and a tree URI picked
via `ACTION_OPEN_DOCUMENT_TREE`.

## Integration

This module is a standalone Android library. To wire it into the
React Native shell:

1. Add the module as a Gradle dependency in the app's `build.gradle.kts`:
   ```
   implementation(project(":android-native-module"))
   ```
2. In `MainApplication.kt`, instantiate `SafFileSystemGateway` from
   the app's `ContentResolver` and expose it through a TurboModule
   (codegen-based) or legacy `ReactContextBaseJavaModule`.
3. The frontend imports `@react-native-async-storage/async-storage`
   (or equivalent) to persist the picked tree URI between launches.

## Permissions

`AndroidManifest.xml` declares `READ_MEDIA_IMAGES` for API 33+ and
`READ_EXTERNAL_STORAGE` for API 28-32. Apps targeting API 34+ do not
need `WRITE_EXTERNAL_STORAGE` thanks to scoped storage; the SAF tree
URI carries the write grant.