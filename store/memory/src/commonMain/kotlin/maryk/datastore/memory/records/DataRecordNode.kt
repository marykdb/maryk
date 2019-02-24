package maryk.datastore.memory.records

/** A sealed class for defines a node in a DataRecord at [reference] */
sealed class DataRecordNode {
    abstract val reference: ByteArray
}

/** Defines a [reference] */
@Suppress("unused")
interface IsDataRecordValue<T> {
    val reference: ByteArray
    val version: ULong
}

/** Defines a [value] at [version] for [reference] */
class DataRecordValue<T : Any>(
    override val reference: ByteArray,
    val value: T,
    override val version: ULong
) : DataRecordNode(), IsDataRecordValue<T>

/** Defines a deletion at [version] for [reference] */
class DeletedValue<T : Any>(
    override val reference: ByteArray,
    override val version: ULong
) : DataRecordNode(), IsDataRecordValue<T>

/** Defines a [history] for [reference] */
class DataRecordHistoricValues<T : Any>(
    override val reference: ByteArray,
    val history: List<IsDataRecordValue<T>>
) : DataRecordNode()
