package maryk.datastore.shared

/** Supplies a datastore- or cluster-authoritative upper version boundary for point-in-time reads. */
interface SnapshotVersionProvider {
    suspend fun captureSnapshotVersion(): ULong
}
