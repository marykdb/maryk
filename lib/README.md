# Maryk Library

Maryk Library is a project that provides multi-platform implementations for essential functions
used across Maryk projects. The purpose of this library is to abstract platform-specific
functionality, ensuring that all core Maryk projects have consistent support for the various platforms it supports.

## Dependency

The foundation of Maryk projects is the Kotlin Standard Library, which means that as
soon as Kotlin implements any of the functionalities exposed in Maryk Library, those functionalities will be deprecated in the library.

## Key Features

Here's a quick rundown of what Maryk Library has to offer:

-  [Streaming String readers and writers](src/commonMain/kotlin/maryk/lib/bytes/String.kt) - Provides efficient and convenient reading and writing of strings from and to byte streams.
-  [ParseException](src/commonMain/kotlin/maryk/lib/exceptions/ParseException.kt) - An exception that is thrown when a parsing error occurs.
-  [Extensions to base types](src/commonMain/kotlin/maryk/lib/extensions) - A set of extensions for core data types, adding useful functionality that enhances their usability.
