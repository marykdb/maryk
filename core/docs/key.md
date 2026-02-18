# Keys in DataObjects

Every DataObject is stored under a unique key that acts as its identifier. If new data is written with an existing key the previous object is replaced.

## Default UUID Keys

If no key definition is provided the model generates 128‑bit UUIDv4 keys. They are random and spread writes better in distributed stores.

## UUIDv4 vs UUIDv7

- **UUIDv4:** Random distribution. Better write spread. Lower hotspot risk in distributed mode. No natural time order for scans.
- **UUIDv7:** Time-ordered prefix. Better temporal scan locality. Can hotspot on write-heavy distributed shards because many new keys land near each other.

Choose UUIDv4 for high write concurrency on distributed backends. Choose UUIDv7 when time-order scans are primary and hotspotting is acceptable.

## Choosing the Right Key from the Start

Design the key carefully: once data is stored its structure cannot change without heavy migrations. Optimise the key for the primary access pattern. Secondary use cases can often be handled later with an index.

## Properties for Key Structure

Key structures must have fixed byte lengths so they can be indexed and scanned efficiently.

Key parts must have predictable sizes. Numbers, dates, fixed bytes, references, enums, booleans, multi‑type type IDs and small value objects all qualify. Strings, flexible bytes, collections and embedded models cannot be used because their length varies.

## The Order of Keys

Keys are stored in order. If the key begins with a reference, scans can jump directly to that reference.

If data is often requested newest‑first, store a reversed timestamp in the key so scans return recent results first.

## Tips on Designing a Key

- **Consider performance:** The key structure should be optimized for the most common use cases, as this will directly
  impact the performance of data retrieval and scans.
- **Entity association:** If data "belongs" to a particular entity or person, start the key with a reference to that
  entity or person. This associativity can streamline searches.
- **Time ordering:** If data needs to be ordered by time, include the time in the key. If newer data is frequently
  requested first, reverse the time to prioritize its retrieval.
- **Type identification:** If the data has a primary multi-type property, include the type ID in the key to enable quick
  retrieval of data objects of a specific type. Ensure that any time information follows the type ID to maintain
  chronological order.
- **Date precision:** If date precision in nanoseconds is insufficient for your application's needs, consider appending
  a random number to the key for additional uniqueness.
- **Use indexing:** If you find that your key structure is not optimized for your primary use case, utilize indexing to
  improve performance. This is especially beneficial when dealing with large datasets.
