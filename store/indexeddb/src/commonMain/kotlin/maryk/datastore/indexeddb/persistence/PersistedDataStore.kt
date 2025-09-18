package maryk.datastore.indexeddb.persistence

internal data class PersistedDataStore(
    val records: List<PersistedRecord>
)

internal data class PersistedRecord(
    val key: String,
    val firstVersion: String,
    val lastVersion: String,
    val values: List<PersistedNode>
)

internal sealed interface PersistedNode {
    val reference: String
}

internal data class PersistedValueNode(
    override val reference: String,
    val version: String,
    val valueJson: String?,
    val isDeleted: Boolean
) : PersistedNode

internal data class PersistedHistoricNode(
    override val reference: String,
    val history: List<PersistedValueNode>
) : PersistedNode
