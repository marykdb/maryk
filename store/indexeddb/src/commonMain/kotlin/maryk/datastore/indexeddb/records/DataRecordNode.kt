package maryk.datastore.indexeddb.records

import maryk.core.clock.HLC
import maryk.lib.extensions.toHex

/** A sealed class for defines a node in a DataRecord at [reference] */
internal sealed class DataRecordNode {
    abstract val reference: ByteArray

    /** Commit any changes */
    open fun commit() {}
}

/** Defines a [reference] */
internal interface IsDataRecordValue<T> {
    val reference: ByteArray
    val version: HLC
}

/** Defines a [value] at [version] for [reference] */
internal class DataRecordValue<T : Any>(
    override val reference: ByteArray,
    val value: T,
    override val version: HLC
) : DataRecordNode(), IsDataRecordValue<T> {
    override fun toString() = "${reference.toHex()}=$value"
}

/** Defines a deletion at [version] for [reference] */
internal class DeletedValue<T : Any>(
    override val reference: ByteArray,
    override val version: HLC
) : DataRecordNode(), IsDataRecordValue<T> {
    override fun toString() = "${reference.toHex()}->§DELETED§"
}

/** Defines a [history] for [reference] */
internal class DataRecordHistoricValues<T : Any>(
    override val reference: ByteArray,
    val history: List<IsDataRecordValue<T>>,
    internal var toAdd: IsDataRecordValue<T>? = null
) : DataRecordNode() {
    fun add(node: IsDataRecordValue<T>) {
        toAdd = node
    }

    override fun toString() = history.lastOrNull().toString()

    override fun commit() {
        toAdd?.let {
            (history as MutableList<IsDataRecordValue<T>>).add(it)
            toAdd = null
        }
    }
}
