package maryk.datastore.shared

/** Supplies a datastore- or cluster-authoritative upper version boundary for point-in-time reads. */
interface SnapshotVersionProvider {
    suspend fun captureSnapshotVersion(): ULong
}

/** A proxy whose watermark support depends on the capabilities of its backend. */
interface ConditionalSnapshotVersionProvider : SnapshotVersionProvider {
    val supportsSnapshotVersion: Boolean
}

/** Whether the store can supply an authoritative watermark; retained history is a separate requirement. */
val IsDataStore.supportsSnapshotVersion: Boolean
    get() = this is SnapshotVersionProvider &&
        (this !is ConditionalSnapshotVersionProvider || this.supportsSnapshotVersion)
