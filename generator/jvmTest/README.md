# Generator JVM tests

This project contains tests for validating the Protobuf generation for JVM platforms against the Google Protobuf library.
The tests ensure that the generated Protobuf code is compliant with the Protobuf standard and can be seamlessly integrated
with JVM applications.

Due to the limitations of the Protobuf Gradle plugin, which cannot be used in Kotlin multiplatform projects, this project was created as a separate entity to
specifically test the Protobuf generation on JVM platforms. These tests provide a critical aspect of quality assurance
and ensure that the generated code can be used in real-world applications.
