# Maryk File

Small expect/actual file IO layer used by tooling and stores to read/write local files in a cross‑platform way.

## Supported operations
- `File.readText(path: String): String?` – reads file content or `null` if it does not exist.
- `File.writeText(path: String, contents: String)` – writes text, creating parent directories when possible.
- `File.appendText(path: String, contents: String)` – appends text, creating the file when absent.
- `File.delete(path: String): Boolean` – deletes the file, returning `true` on success.

## Platforms
Actual implementations exist for:
- JVM/Android
- POSIX native (Linux/macOS)
- mingwX64

## Usage
```kotlin
val path = "example.txt"
File.writeText(path, "hello")
val content = File.readText(path) // "hello"
File.appendText(path, " world")
File.delete(path)
```
