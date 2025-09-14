---
title: What is Maryk?
description: A friendly tour of Maryk — one model, many platforms, and version‑aware storage.
---

Maryk is a Kotlin Multiplatform toolkit for data. You describe your data once — with types and validation — and use that same model across clients and servers. **No drift, no duplicate schemas.**

### What you get at a glance

- One strongly‑typed model for all platforms (JVM, Android, iOS, macOS, Linux, Windows, JS).
- Built‑in validation and rich property types (primitives, collections, embedded, multi‑type, value objects).
- Version‑aware storage and queries: ask for “what changed” or “as of time T”.
- Portable serialization: fast YAML/JSON streaming and compact ProtoBuf transport.
- Pluggable stores with the same API (In‑Memory, RocksDB, FoundationDB).

### A tiny taste

```kotlin
object Person : RootDataModel<Person>() {
  val firstName by string(index = 1u)
  val lastName by string(index = 2u)
  val dateOfBirth by date(index = 3u)
}

val john = Person.create { firstName with "John"; lastName with "Smith" }
Person.validate(john) // throws if invalid
```

### Why this matters

- **Fewer bugs** — one canonical model means fewer mismatches between app and backend.
- **Less bandwidth** — request only the fields you need (reference graphs) or only the changes since last time.
- **Easier evolution** — serialize schemas and run compatibility checks; migrate stores safely when models change.

### When Maryk fits

- You’re building multi‑client apps and want a single source of truth for data.
- You need time‑travel/debuggability or live change streams.
- You like Kotlin and want to share as much logic as possible across platforms.

### Next steps

- Quick setup: [Getting Started](/getting-started/)
- Deep dive: [Data Design](/data-modeling/data-design/)
