package maryk.datastore.shared

/** Supplies an upper version boundary for point-in-time reads. */
interface SnapshotVersionProvider {
    suspend fun captureSnapshotVersion(): ULong
}
