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
-  [Extensions to base types](src/commonMain/kotlin/maryk/lib/extensions)
-  Date/Time functionality
   * [Date](src/commonMain/kotlin/maryk/lib/time/Date.kt) - 
     Object to help with some basic date operations based on KotlinX LocalDate
   * [DateTime](src/commonMain/kotlin/maryk/lib/time/DateTime.kt) -
     Object to help with some basic date time operations based on KotlinX LocalDateTime
   * [Time](src/commonMain/kotlin/maryk/lib/time/Time.kt) - 
     Object to represent `Time`
- [UUID.generateUUID()](src/commonMain/kotlin/maryk/lib/uuid/UUID.kt) - 
  Creates a `Pair` of `long` together representing a type 4 (Random) UUID 
