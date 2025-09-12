# Versioning in Maryk

Every value stored within an object is given a unique version number. This makes it possible to request only changed data and, if configured, to retain historical versions for tracking changes over time.

## Querying for Specific Versions

Two queries work with versions: [`GetChanges`/`ScanChanges`](query.md#getscan-changes) and [`GetUpdates`/`ScanUpdates`](query.md#getscan-updates).

- **GetChanges/ScanChanges** – returns all changes ordered by object and then by version. Useful for tracking modifications per object.
- **GetUpdates/ScanUpdates** – returns changes ordered by time and includes hard deletes, providing a chronological view of updates.

## Representation of a Version

A version in Maryk is represented as an unsigned 64‑bit integer that encodes a Hybrid Logical Clock (HLC) combining:

1. **Physical Clock** – a UNIX millisecond timestamp.
2. **Logical Clock** – a counter ensuring uniqueness when multiple updates occur in the same millisecond.

The structure of the version is as follows:
- The upper 44 bits represent the physical clock.
- The lower 20 bits represent the logical clock.

The logical clock has millisecond precision and supports up to 1,048,575 events per millisecond, giving a maximum timestamp of June 23rd, 2527 at 08:20:44.

For a deeper understanding of Hybrid Logical Clocks, you can refer to the original paper introducing the concept: 
[Hybrid Logical Clocks](https://cse.buffalo.edu/tech-reports/2014-04.pdf).
