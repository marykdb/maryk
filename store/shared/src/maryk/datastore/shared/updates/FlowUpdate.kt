package maryk.datastore.shared.updates

import maryk.core.models.IsRootDataModel

/** Internal update envelope carrying the source positions needed by flow activation. */
class FlowUpdate(
    update: Update<out IsRootDataModel>,
    val localEmissionSequence: Long,
    val foundationDbCommitVersion: Long? = null,
) : IsUpdateAction {
    @Suppress("UNCHECKED_CAST")
    val update = update as Update<IsRootDataModel>

    fun isIncludedIn(boundary: FlowSnapshotBoundary): Boolean =
        if (foundationDbCommitVersion != null && boundary.foundationDbReadVersion != null) {
            foundationDbCommitVersion <= boundary.foundationDbReadVersion
        } else {
            localEmissionSequence <= boundary.localEmissionSequence
        }
}
