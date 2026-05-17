# Versioning in Maryk

Maryk assigns every stored value change a monotonically ordered version. Versions make four workflows possible:

- read a record as it looked at an earlier version,
- fetch the changes for one record or a scan range,
- stream live additions, changes and removals,
- synchronize another store or client by replaying update responses.

Versioning is store-backed behavior. To retain historic values, open the store with `keepAllVersions = true`. To scan updates newest-first efficiently, also enable `keepUpdateHistoryIndex = true` where supported.

## Versions vs model versions

Maryk uses two different ideas that are easy to confuse:

- Data versions: generated per write by the store. Used by `toVersion`, changes, updates and history.
- Model versions: schema definition changes. Used by store migration logic and compatibility checks.

This page is about data versions.

## Reading a previous state

Most read requests can include `toVersion`.

```kotlin
val historic = store.execute(
    Person.get(
        key,
        toVersion = previousVersion
    )
)
```

If the store was opened without historic retention, only current values are available.

## Changes: history grouped per object

Use `GetChanges` when you know the keys. Use `ScanChanges` when you want changes for a range or query.

```kotlin
val changes = store.execute(
    Person.getChanges(
        key,
        fromVersion = lastSeenVersion,
        maxVersions = 100u
    )
)
```

`GetChanges` and `ScanChanges` return changes grouped by object and version. They are useful for audit views, object history screens and targeted sync.

See [`GetChanges`/`ScanChanges`](query.md#getscan-changes).

## Updates: chronological stream

Use `GetUpdates` or `ScanUpdates` when chronological order matters. Updates include additions, changes and removals, including hard deletes.

```kotlin
val updates = store.execute(
    Person.scanUpdates(
        fromVersion = lastSyncedVersion,
        maxVersions = 100u
    )
)
```

`ScanUpdates(order = null)` can use the update-history index when the engine was opened with `keepUpdateHistoryIndex = true`.

See [`GetUpdates`/`ScanUpdates`](query.md#getscan-updates).

## Live update flows

For live views, use `executeFlow` with a `Get`, `Scan`, `GetUpdates` or `ScanUpdates` style request supported by the store. The flow first emits the initial state and then emits additions, changes and removals.

```kotlin
val updates = store.executeFlow(Person.scan())

updates.collect { update ->
    // apply update to UI, cache or another store
}
```

Use this for desktop screens, local caches, and sync loops that should keep running after the first fetch.

## Sync pattern

A typical sync client stores the latest processed version locally.

1. Fetch current data with `Scan` or `Get`.
2. Store the highest returned version.
3. Later call `ScanUpdates(fromVersion = lastSeenVersion)`.
4. Apply returned updates.
5. Persist the highest processed version.
6. Repeat or switch to `executeFlow` for live updates.

If the client can miss long periods, keep enough history on the server to cover the expected offline window. If history was compacted or disabled, fall back to a fresh full scan.

## Concurrency checks

Change operations can include checks. Use them to guard against stale writes:

```kotlin
Person.change(
    key.change(
        Check(Person { lastName::ref } with "Doe"),
        Change(Person { lastName::ref } with "Roe")
    )
)
```

This is useful when a UI edits a value that may have changed since it was loaded.

## Version representation

A version is an unsigned 64-bit integer containing a Hybrid Logical Clock:

- upper 44 bits: physical UNIX millisecond timestamp,
- lower 20 bits: logical counter for multiple events in the same millisecond.

The logical part supports high write density within a single millisecond while preserving total ordering for generated store versions.

For background, see the [Hybrid Logical Clocks paper](https://cse.buffalo.edu/tech-reports/2014-04.pdf).

## Operational notes

- Enable `keepAllVersions` before you need history. It cannot reconstruct changes that were never retained.
- Keep model IDs stable in `dataModelsById`; IDs are part of store identity.
- Prefer `GetChanges` for one-object history and `ScanUpdates` for synchronization.
- Use reference graphs with history requests to limit returned fields.
- Test migration plans with representative stored data before changing keys, indexes or property definitions.
