# Maryk File

Small expect/actual file IO layer used by tooling and stores to read/write local files in a cross‑platform way.

## Supported operations
- `File.size(path: String): Long?` – returns the file size when available.
- `File.readText(path: String): String?` – reads file content or `null` if it does not exist.
- `File.readText(path, maxBytes)` and `File.readBytes(path, maxBytes)` – bounded reads; return `null` when content exceeds the byte limit.
- `File.readBytes(path: String): ByteArray?` – reads binary content.
- `File.readChunks(path, chunkSize, onChunk): Boolean` – reads a regular file in bounded chunks, defaulting to 64 KiB; returns `false` when unavailable.
- `File.writeText(path: String, contents: String)` – writes text, creating parent directories when possible.
- `File.writeBytes(path: String, contents: ByteArray)` – writes binary content.
- `File.appendText(path: String, contents: String)` – appends text, creating the file when absent.
- `File.moveReplace(sourcePath, destinationPath)` – moves a file into place, replacing its destination.
- `File.syncFile(path)` and `File.syncParentDirectory(path)` – flush file contents/metadata and the containing directory entry; return `false` when the platform cannot do so.
- `File.delete(path: String): Boolean` – deletes the file, returning `true` on success.

## Platforms
Actual implementations exist for:
- JVM (Android callers can use the JVM artifact; no dedicated Android target)
- POSIX native (Linux, macOS, iOS, tvOS, watchOS and Android Native)
- mingwX64

These are file primitives, not a datastore transaction. Callers own publication
and recovery for multi-file output and must check synchronization results when
durability is required.

## Usage
```kotlin
val path = "example.txt"
File.writeText(path, "hello")
val content = File.readText(path) // "hello"
File.appendText(path, " world")
File.delete(path)
```
