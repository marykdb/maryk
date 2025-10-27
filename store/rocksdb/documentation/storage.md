# RocksDB Record Storage Structure

## Store metadata column family

Besides the per‑model column families, Maryk keeps a single metadata column family shared by the entire RocksDB store. Its byte is `0`. It contains the mapping from *model id → model name*, persisted as:

- **Key:** one byte prefix `0x01` (defined as `MODEL_NAME_METADATA_PREFIX`) followed by the model id encoded as a fixed 4‑byte big‑endian `UInt`.
- **Value:** UTF‑8 encoded model name.

This metadata is written whenever a model definition is stored and read during startup to verify that the configured `dataModelsById` still matches the persisted schema. Because the keys are namespaced with the prefix byte, the same column family can later host new metadata types without risking collisions.

## Column Family Layout

Each DataModel is represented by multiple column families that hold the actual data. At minimum a model has `Model`, `Keys`, `Table`, `Index` and `Unique`. When `keepAllVersions = true` the historic variants are also created to store previous versions.

The column family name is a compact byte array made of two parts: a single type byte followed by the varint‑encoded model id. The type byte values are:

- Model (1)
- Keys (2)
- Table (3)
- Index (4)
- Unique (5)
- Historic Table (6) – only when `keepAllVersions = true`
- Historic Index (7) – only when `keepAllVersions = true`
- Historic Unique (8) – only when `keepAllVersions = true`

The model id is the `UInt` key from `dataModelsById` passed to `RocksDBDataStore.open`.

For efficient scans, the `Table` and `HistoricTable` families use a fixed‑length prefix extractor sized to the model key. The historic families use a custom comparator so qualifiers sort before versions (see `VersionedComparator`).

## Model
Stores the model used for the data. Useful to get current structure and to check if current data can be
read or updated with reference model from a client.

## Keys
Contains all object keys and their creation versions. Scan operations iterate this family to enumerate keys in order.

## Record Structure
Each record is represented by multiple key/value pairs across the families. The exact encoding differs per family and whether history is enabled.

## Table (latest values)
A record is stored with the following structure within the `Table` family.

- `KEY` → `VERSION` – Creation version. Written once, never changed.
- `KEY || SOFT_DELETE_INDICATOR(0x00)` → `VERSION || Boolean` – Object‑level soft delete flag.
- `KEY || LAST_VERSION_INDICATOR(0b1000)` → `VERSION` – Last write version; updated on any add/change/delete.
- `KEY || QUALIFIER` → `VERSION || VALUE` – Property values. The qualifier encodes the property reference (and collection item, where applicable).
  
## Index (latest)
`INDEX_REF || VALUE_BYTES || KEY` → `VERSION`

Indexes allow range scans over value prefixes and disambiguate duplicates by appending the primary key.
  
## Unique (latest)
`UNIQUE_REF || VALUE_BYTES` → `VERSION || KEY`

Unique entries map a unique value (or composite) to exactly one record key.
  
## Historic Table (all versions)
Stores all versions of values by appending the inverted version to the qualifier. Newest versions sort first.

- `KEY` → `VERSION` – Creation version (also stored in historic).
- `KEY || SOFT_DELETE_INDICATOR || inverted(VERSION)` → empty value (flag marker).
- `KEY || QUALIFIER || inverted(VERSION)` → `VALUE`.

## Historic Index
Supports “as of” queries on indexes.

- `INDEX_REF || VALUE_BYTES || KEY || inverted(VERSION)` → empty (set) or `[0x00]` (unset) marker.
  When an index value changes, the previous value is recorded as an `unset` at the same version.

## Historic Unique
Stores unique value history.

- `UNIQUE_REF || VALUE_BYTES || inverted(VERSION)` → `KEY` (set) or empty (unset tombstone).

This enables resolving which key owned a unique value at or before a given version.
