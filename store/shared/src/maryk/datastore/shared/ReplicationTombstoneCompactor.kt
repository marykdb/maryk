package maryk.datastore.shared

/**
 * Explicitly discards durable replication tombstones through a caller-provided
 * synchronization watermark. Callers must only advance this watermark after
 * every replication source is guaranteed not to replay an update at or below it.
 */
interface ReplicationTombstoneCompactor {
    suspend fun compactReplicationTombstones(upToVersionInclusive: ULong)
}
