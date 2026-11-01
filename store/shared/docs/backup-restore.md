# Point-in-time backup and restore

`backup` creates a portable logical backup: the manifest identifies the format,
snapshot version, and models; each chunk contains complete versioned changes for
one model. It is not a filesystem or engine-level backup.

The current portable format is version `3` (`DATA_STORE_BACKUP_FORMAT_VERSION`).
Restore accepts only that exact manifest version; it never guesses how to read an
older or newer format. A format change must increment the version, update both
writer and reader, and document whether a compatibility reader is supplied.

Requirements and operation:

- Enable `keepAllVersions` before data is written. History cannot be recreated
  later, and `backup` rejects stores without it.
- Let `backup` capture the snapshot version, or provide one obtained from the
  same cluster. Use that version for every page; do not mix snapshots.
- A remote store needs a compatible server that exposes an authoritative
  snapshot version. A newer client refuses an older server without that
  capability rather than producing a mixed-time export.
- A `DataStoreBackupWriter` must keep output private until `complete()` returns.
  Use durable storage, integrity checks, encryption where needed, and an atomic
  publish/rename step in the writer implementation.
- Choose `batchSize` for memory and transport limits. It bounds records per
  chunk, not the size of one record's complete history.
- Backups include retained historic data and the soft-delete transition/state
  visible at the snapshot. Hard deletion can permanently erase earlier history
  in persistent engines: capturing a version does not preserve erased rows.
  Coordinate hard deletion with backup creation, or use soft deletion until the
  backup completes. Protect backups as sensitive production data and retain
  them according to the same policy.
- Backup history paging preserves frequently changed fields even when another
  field has much older sparse revisions. A scanned record without its creation
  history, or history outside the exclusive snapshot boundary, stops publication.
  Creation reads must match that record and creation version. Duplicate or
  unordered page revisions, or conflicting copies of a complete revision across
  pages, also stop publication rather than silently selecting one copy.
- Restore requires matching registered model names and major model versions.
  Review minor/patch schema compatibility before restoring. By default it
  refuses non-empty target models. The target must retain all versions so the
  restored history is not silently collapsed to current state.
- Restore validates creation history, ordering, and snapshot bounds before replay,
  and rejects unexpected or incomplete backend responses.
- Restore uses serialized staging capped at 64 MiB and 100,000
  records by default. Exceeding either bound fails before changing the target.
  `restore(reader, requireEmpty, DataStoreRestoreOptions(...))` adjusts these bounds.
- Restore stages every reader once before replay. This avoids repeated scans for every
  record version while preserving global source-version order.
- Replay groups all records with the same source version, including across models
  and backup chunks. The group commits atomically and preserves original creation,
  change and deletion versions. Its old unique claims are released inside the
  transaction; final ownership is validated and installed before publication.
  Transfers and cyclic swaps do not depend on backup record/key order.
- Every group's serialized size and record count are checked before any replay.
  `maxReplayBytes` remains 8 MiB by default for API compatibility; the effective
  ceiling also includes the backend's advertised limits and the common replay
  limits of 1 MiB and 1,000 record versions per group. A group cannot be split.
  Native transaction/work limits can still reject a group atomically at execution.
- No extra application-record metadata or live-store opening migration is needed.
- Built-in local stores advertise `VersionedRestoreDataStore`. Custom stores and
  decorators must explicitly implement/forward this capability and its atomicity
  contract. Unsupported targets fail before replay; there is no sequential fallback.
  Remote clients negotiate the capability with their server.
- Replay is not globally transactional: backend or later replay failures can leave
  earlier requests applied. Restore into a disposable store, validate it, then
  publish or switch to it only after success.
