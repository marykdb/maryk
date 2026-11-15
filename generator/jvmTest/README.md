# Generator JVM tests

Checks generated ProtoBuf schemas against Google's Protobuf library. This is a
separate JVM project so it can use Protobuf's JVM build tooling without adding
those dependencies to the multiplatform generator.

## When to run

Run this module after changing:

- ProtoBuf generation,
- generated schema naming,
- model serialization metadata,
- Gradle wiring for generator output.

Use:

```bash
./gradlew :generator:jvmTest:test
```
