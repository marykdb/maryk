# Maryk Library

Common byte, string and parsing utilities used across Maryk modules, with
platform-specific implementations where needed.

## Key Features

-  [Streaming String readers and writers](src/commonMain/kotlin/maryk/lib/bytes/String.kt) - Read and write UTF-8 with strict validation and safe nested decoding. Small reads reuse bounded scratch storage.
-  [ParseException](src/commonMain/kotlin/maryk/lib/exceptions/ParseException.kt) - Structured parsing failures.
-  [Extensions to base types](src/commonMain/kotlin/maryk/lib/extensions) - Shared operations on bytes, collections and other Kotlin types.
