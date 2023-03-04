@file:Suppress("unused")

package maryk.core.query.changes

import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.SimpleObjectModel
import maryk.core.properties.definitions.flexBytes
import maryk.core.properties.types.Bytes
import maryk.core.query.changes.IndexUpdateType.Delete
import maryk.core.query.changes.IndexUpdateType.Update
import maryk.core.values.ObjectValues

/** For passing updates to an index */
interface IsIndexUpdate {
    val index: Bytes
    val type: IndexUpdateType
}

/** Describes an update to an index value */
data class IndexUpdate(
    override val index: Bytes,
    val indexKey: Bytes,
    val previousIndexKey: Bytes?
): IsIndexUpdate {
    override val type = Update

    object Properties : ObjectPropertyDefinitions<IndexUpdate>() {

    }

    companion object : SimpleObjectModel<IndexUpdate, Properties>() {
        val index by flexBytes(
            index = 1u,
            getter = IndexUpdate::index
        )

        val indexKey by flexBytes(
            index = 2u,
            getter = IndexUpdate::indexKey
        )

        val previousIndexKey by flexBytes(
            index = 3u,
            getter = IndexUpdate::previousIndexKey,
            required = false
        )

        override fun invoke(values: ObjectValues<IndexUpdate, Properties>) = IndexUpdate(
            index = values(1u),
            indexKey = values(2u),
            previousIndexKey = values(3u)
        )
    }
}

/** Describes a delete to an index value */
data class IndexDelete(
    override val index: Bytes,
    val indexKey: Bytes
): IsIndexUpdate {
    override val type = Delete

    companion object : SimpleObjectModel<IndexDelete, Companion>() {
        val index by flexBytes(
            index = 1u,
            getter = IndexDelete::index
        )

        val indexKey by flexBytes(
            index = 2u,
            getter = IndexDelete::indexKey
        )

        override fun invoke(values: ObjectValues<IndexDelete, Companion>) = IndexDelete(
            index = values(1u),
            indexKey = values(2u)
        )
    }
}
