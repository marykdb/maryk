# IndexedDB Operations And Query Support

The store executes Maryk requests over IndexedDB calls and cursor scans.

## Add

`AddRequest` writes:

- current key metadata/snapshot;
- current table rows;
- current index rows;
- current unique rows;
- history snapshot/change rows when history is enabled;
- update-history row when the update-history index is enabled.

Uniqueness is checked with direct IndexedDB reads before writes are committed.

## Change

`ChangeRequest` reads the current object, materializes the changed values, validates the changed object, rewrites affected current rows, and writes history/update-history rows when enabled.

Index and unique rows are updated from the computed before/after storage plan.

## Delete

Soft delete marks current metadata as deleted and writes history rows.

Hard delete removes current key/table/index/unique rows and purges key-scoped historic table/change rows. Historic secondary rows are purged through `hik:<modelId>` and `huk:<modelId>` cleanup sidecars.

Hard deletes keep update-history visibility by writing a hard-delete update marker.

## Get

Current get uses direct key/table reads.

Historic get uses `ht:<modelId>` and reads the latest snapshot at or before `toVersion`.

`where` filters are evaluated on full values before selected values are returned.

## Scan

Table scans cursor over `k:<modelId>` ranges.

Index scans cursor over `i:<modelId>` ranges and then fetch selected/full values as needed for filters and response values.

Unique fast-path scans use `u:<modelId>` for current reads and `hu:<modelId>` for historic reads.

Current scans use batched IndexedDB cursor pages so request limits count rows after Maryk filters and soft-delete checks.

## GetChanges And ScanChanges

`getChanges` reads `c:<modelId>` by key/version scope.

Table-ordered `scanChanges` scans current or historic visible keys, then reads matching change-log rows.

Index-ordered `scanChanges` scans current `i:<modelId>` rows or historic `hi:<modelId>` rows, then reads matching changes. Descending index scans use bounded upper ranges plus Maryk-side filtering for exact sorting-key semantics.

## GetUpdates And ScanUpdates

Current `getUpdates` and table/index `scanUpdates` read current or historic visible values and return selected updates.

`orderedKeys` reconciliation is supported for scan-update refill semantics.

When `keepUpdateHistoryIndex` is enabled and the request shape allows it, `scanUpdates` can use the update-history index to return changed rows first.

## ScanUpdateHistory

`scanUpdateHistory` requires:

- `keepAllVersions = true`;
- `keepUpdateHistoryIndex = true`.

It cursor-scans `uh:<modelId>` by inverted version order, reads historic values at each update version for filtering, and returns addition/change/removal updates.

## Filters

Key and index ranges are pushed into IndexedDB cursor bounds when possible. Other filters are evaluated after fetching the needed values.

Supported filter paths include:

- normal value filters;
- fuzzy qualifier filters;
- sub-reference filters;
- normalized/named-search index filters;
- mutable value and any-value index scans.

Referenced-object filters resolve referenced rows through IndexedDB reads/scans.

## Aggregations

Aggregations are evaluated while scanning matched rows, using the same request path as normal scans.
