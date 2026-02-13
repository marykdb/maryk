package maryk.datastore.foundationdb.clusterlog

import maryk.core.query.changes.IsChange
import maryk.core.properties.types.Bytes
import maryk.core.values.Values

internal sealed interface ClusterLogUpdate {
    val keyBytes: Bytes
    val version: ULong

    val type: Byte

    companion object {
        const val TYPE_ADDITION: Byte = 1
        const val TYPE_CHANGE: Byte = 2
        const val TYPE_DELETION: Byte = 3
    }
}

internal data class ClusterLogAddition(
    override val keyBytes: Bytes,
    override val version: ULong,
    val values: Values<*>,
) : ClusterLogUpdate {
    override val type: Byte = ClusterLogUpdate.TYPE_ADDITION
}

internal data class ClusterLogChange(
    override val keyBytes: Bytes,
    override val version: ULong,
    val changes: List<IsChange>,
) : ClusterLogUpdate {
    override val type: Byte = ClusterLogUpdate.TYPE_CHANGE
}

internal data class ClusterLogDeletion(
    override val keyBytes: Bytes,
    override val version: ULong,
    val hardDelete: Boolean,
) : ClusterLogUpdate {
    override val type: Byte = ClusterLogUpdate.TYPE_DELETION
}

internal data class ClusterLogHeader(
    val origin: String,
    val modelId: UInt,
)
