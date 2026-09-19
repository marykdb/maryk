package maryk.datastore.shared.updates

/** Point-in-time fence for a flow's initial read. */
data class FlowSnapshotBoundary(
    val localEmissionSequence: Long,
    val foundationDbReadVersion: Long? = null,
)
