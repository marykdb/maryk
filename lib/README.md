# Maryk Library

This project contains multi-platform implementations for common functions used within Maryk projects.

Anything platform specific needed by the core projects should be abstracted into this library. If a 
platform is supported by the library, it will be supported by all core maryk projects.

Currently all functionality is implemented for JS and JVM. Kotlin/Native is high on the wish list and any 
contributions are welcome. If implemented it allows all

Kotlin stdlib is the base dependency for everything in Maryk projects so as soon as Kotlin implements
any of the functionalities exposed in this library, those functionalities will be deprecated.

## What is in the library?

-  [Base64 encoding/decoding](src/commonMain/kotlin/maryk/lib/bytes/Base64.kt)
-  [Streaming String readers and writers](src/commonMain/kotlin/maryk/lib/bytes/String.kt)
-  [ParseException](src/commonMain/kotlin/maryk/lib/exceptions/ParseException.kt)
-  [Extensions to base types](src/commonMain/kotlin/maryk/lib/exceptions/ParseException.kt)
-  Date/Time functionality
   * [Date](src/commonMain/kotlin/maryk/lib/time/Date.kt) - 
     Data class to represents a `Date` with year / month / date
   * [DateTime](src/commonMain/kotlin/maryk/lib/time/DateTime.kt) -
     Data class to represents a `DateTime` with year / month / date 
   * [Instant.getCurrentEpochTimeInMillis()](src/commonMain/kotlin/maryk/lib/time/Instant.kt) -
     Method to get the current time in milliseconds since epoch start 
   * [ISO8601.toDate()](src/commonMain/kotlin/maryk/lib/time/ISO8601.kt) -
     Method to parse ISO 8601 string to `Date`
   * [Time](src/commonMain/kotlin/maryk/lib/time/Time.kt) - 
     Object to represent `Time`
- [UUID.generateUUID()](src/commonMain/kotlin/maryk/lib/uuid/UUID.kt) - 
  Creates a `Pair` of `long` together representing a type 4 (Random) UUID 
