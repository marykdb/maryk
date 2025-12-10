# Maryk CLI

The Maryk CLI is an interactive terminal client that communicates with Maryk stores. It is built with the [Kotter](https://github.com/varabyte/kotter) terminal UI library to provide a block-oriented command experience. It can connect to both RocksDB and FoundationDB stores.

## Status

This initial setup contains the basic infrastructure for commands and exposes a single `help` command. Every executed command renders in its own Kotter block, keeping the entire history visible in the terminal.

## Getting Started

Run the CLI from the project root:

```bash
./gradlew :cli:runJvm
```

For release builds on macOS, you can run:

```bash
./gradlew :cli:runReleaseExecutableMacosArm64
```

When executed in a non-interactive environment (like Gradle), the release binary prints the bundled help text and exits cleanly.

Once running interactively, type `help` to see the available commands. Use `Ctrl+C` to exit the session.

### Connecting to a store

- RocksDB: `connect rocksdb --dir /path/to/rocksdb`
- FoundationDB: `connect foundationdb --dir maryk/app/store [--cluster /path/to/fdb.cluster] [--tenant myTenant]`

## Development Notes

- Commands are registered through `CommandRegistry` and return structured output so the UI can render consistently.
- The CLI uses a lightweight command line parser that supports quoted arguments, preparing the client for future commands like store connections and queries.
- Tests live in `cli/src/commonTest/kotlin` and can be executed via `./gradlew :cli:jvmTest`.
