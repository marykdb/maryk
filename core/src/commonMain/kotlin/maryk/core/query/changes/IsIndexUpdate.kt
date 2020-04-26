@file:Suppress("unused")

package maryk.core.query.changes

import maryk.core.models.SimpleObjectDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.flexBytes
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.TypedValue
import maryk.core.query.changes.IndexUpdateType.Delete
import maryk.core.query.changes.IndexUpdateType.Update
import maryk.core.values.ObjectValues

/** For passing updates to an index */
sealed class IsIndexUpdate(
    val type: IndexUpdateType
)

/** Describes an update to an index value */
data class IndexUpdate(
    val index: ByteArray,
    val indexKey: ByteArray,
    val previousIndexKey: ByteArray?
): IsIndexUpdate(Update) {


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IndexUpdate) return false

        if (!index.contentEquals(other.index)) return false
        if (!indexKey.contentEquals(other.indexKey)) return false
        if (previousIndexKey != null) {
            if (other.previousIndexKey == null) return false
            if (!previousIndexKey.contentEquals(other.previousIndexKey)) return false
        } else if (other.previousIndexKey != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = index.contentHashCode()
        result = 31 * result + indexKey.contentHashCode()
        result = 31 * result + (previousIndexKey?.contentHashCode() ?: 0)
        return result
    }

    object Properties : ObjectPropertyDefinitions<IndexUpdate>() {
        val index by flexBytes(
            index = 1u,
            getter = IndexUpdate::index,
            toSerializable = { value, _: IsPropertyContext? ->
                value?.let { Bytes(it) }
            },
            fromSerializable = { it?.bytes }
        )

        val indexKey by flexBytes(
            index = 2u,
            getter = IndexUpdate::indexKey,
            toSerializable = { value, _: IsPropertyContext? ->
                value?.let { Bytes(it) }
            },
            fromSerializable = { it?.bytes }
        )

        val previousIndexKey by flexBytes(
            index = 3u,
            getter = IndexUpdate::previousIndexKey,
            required = false,
            toSerializable = { value, _: IsPropertyContext? ->
                value?.let { Bytes(it) }
            },
            fromSerializable = { it?.bytes }
        )
    }

    companion object : SimpleObjectDataModel<IndexUpdate, Properties>(
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<IndexUpdate, Properties>) = IndexUpdate(
            index = values(1u),
            indexKey = values(2u),
            previousIndexKey = values(3u)
        )
    }
}

/** Describes a delete to an index value */
data class IndexDelete(
    val index: ByteArray,
    val indexKey: ByteArray
): IsIndexUpdate(Delete) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IndexDelete) return false

        if (!index.contentEquals(other.index)) return false
        if (!indexKey.contentEquals(other.indexKey)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = index.contentHashCode()
        result = 31 * result + indexKey.contentHashCode()
        return result
    }

    object Properties : ObjectPropertyDefinitions<IndexDelete>() {
        val index by flexBytes(
            index = 1u,
            getter = IndexDelete::index,
            toSerializable = { value, _: IsPropertyContext? ->
                value?.let { Bytes(it) }
            },
            fromSerializable = { it?.bytes }
        )

        val indexKey by flexBytes(
            index = 2u,
            getter = IndexDelete::indexKey,
            toSerializable = { value, _: IsPropertyContext? ->
                value?.let { Bytes(it) }
            },
            fromSerializable = { it?.bytes }
        )
    }

    companion object : SimpleObjectDataModel<IndexDelete, Properties>(
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<IndexDelete, Properties>) = IndexDelete(
            index = values(1u),
            indexKey = values(2u)
        )
    }
}
