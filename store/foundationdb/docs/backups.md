# FoundationDB backup and recovery

Use `fdbbackup`, `backup_agent` and `fdbrestore` for operational recovery of a
FoundationDB deployment. These are operator-managed tools; Maryk does not wrap
cluster backup jobs in a datastore API. For application data transfer, see
[portable backups](../../shared/docs/backup-restore.md).

## Back up the complete user keyspace

Back up the **entire user keyspace without a `-k` filter**, and recover into a
separate, empty cluster. This includes other applications using that keyspace;
protect the backup accordingly.

Maryk's `directoryPath` is a logical DirectoryLayer path. Child directories can
have independent physical prefixes: backing up `rootDirectory.pack()` alone
misses data or the mappings needed to reach it. Full coverage preserves Maryk
schemas, records, indexes, history, receipts, replication/update state and directory
allocations. Listing only today's directories is unsafe when new ones can appear.

User-keyspace recovery does not include cluster configuration, system keys,
credentials or host files. Keep deployment configuration and encryption keys
separately. For DirectoryLayer details, see the
[upstream explanation](https://apple.github.io/foundationdb/developer-guide.html#directories).

## Start and monitor a backup

Use matching server and backup-tool releases, and follow the
[upstream backup guide](https://apple.github.io/foundationdb/backups.html) and the
installed tools' `--help`. The examples use operator-supplied absolute paths.

Run backup agents connected to the explicit source cluster. The command schedules
the work; the agents perform it and must all be able to access the destination.
A `file://` URL refers to the agents' filesystem. Use canonical paths, such as
`/private/var` rather than its macOS `/var` alias.

```sh
source_cluster=/etc/foundationdb/source.cluster
backup_base=file:///srv/fdb-backups

# Omit -k. The -z option keeps recording mutations.
fdbbackup start -C "$source_cluster" -d "$backup_base" -z
fdbbackup status -C "$source_cluster"
fdbbackup list -b "$backup_base"
```

Record the actual container URL reported by the tools, not just the base directory.
Inspect it with `fdbbackup describe -d "$backup_container"`. Monitor restorable
versions, errors, agent health and capacity: a submitted job is not yet a
recoverable backup. Set retention against acceptable data loss, and expire old
backups with supported commands only after a newer restore drill succeeds.

## Restore into an isolated cluster

Provision a compatible **new empty destination cluster**, with backup agents that
can read the container. Keep application traffic away throughout recovery.

```sh
target_cluster=/etc/foundationdb/recovery.cluster
backup_container=file:///srv/fdb-backups/ACTUAL_BACKUP_CONTAINER

fdbrestore start --dest-cluster-file "$target_cluster" \
  -r "$backup_container" -w
fdbrestore status --dest-cluster-file "$target_cluster"
```

This selects the latest restorable FDB version by default. To recover before an
incident, choose a known-good version within the container's restorable range
and pass `-v VERSION` to `start`. **An FDB version is not a Maryk HLC snapshot
version**; never interchange them. Replication alone does not undo an erroneous
write or deletion.

Reopen with the original application version, model registrations and IDs,
`directoryPath`, history/index settings, cluster-update settings and encryption
provider. Keep migration code, runtime versions, keys, credentials, TLS material
and infrastructure configuration separately. Native recovery preserves encrypted
bytes but does not supply the keys or provider. Preserve an untouched recovery
point before reopening, because opening can resume migrations or maintenance.

## Validate, then switch traffic

Check the recovered store against application invariants:

- Known keys and counts across models; current values and version metadata.
- Historical reads when enabled, soft deletes and replication tombstones.
- Indexed queries, unique constraints and sensitive fields with the real keys.
- New writes with increasing versions, persisted transaction retries and migration status.

Reconcile external consumers and reconnect clients explicitly. Restoring database
cursors does not rewind queues, side effects or external checkpoints.

Only then switch application configuration to the recovered cluster. Fence old
writers and account for writes after the selected recovery point. Keep the original
deployment isolated for investigation. Measure recovery time against operational
requirements; use a deployment-specific rehearsal for production assurance.

## Local recovery drill

On macOS/Linux, install matching `fdbserver`, `fdbcli`, `fdbbackup`, `fdbrestore`
and `backup_agent` on `PATH`, or together in `FDB_RECOVERY_BIN_DIR`:

```sh
./gradlew :store:foundationdb:installFoundationDB --quiet
./gradlew :store:foundationdb:runFoundationDBRecoveryDrill --quiet
```

The installer supplies server/client support for module tests; backup tools must
be installed separately. Local tooling pins server 7.3.79 and API 730, which does
not establish production upgrade/downgrade compatibility.

The drill creates two disposable loopback clusters and its own agents. It does
not use the application cluster, `FDB_CLUSTER_FILE` or managed test port 4500.
It requires matching tool versions, compares the restored user keyspace before
reopening Maryk, and checks reads, history, indexes, soft deletes, uniqueness and
new writes with history enabled and disabled. It stops its subprocesses and
retains a temporary artifact directory for inspection.

Ordinary `jvmTest` does not run this drill. It is a small local recovery check,
not evidence for production-scale timing, continuous recovery points, remote
storage durability, encryption/migration recovery or consumer reconciliation.
