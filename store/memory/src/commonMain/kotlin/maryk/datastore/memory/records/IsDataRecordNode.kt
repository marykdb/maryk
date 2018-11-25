@file:Suppress("EXPERIMENTAL_API_USAGE")

package maryk.datastore.memory.records

sealed class IsDataRecordNode {
    abstract val reference: ByteArray
}

@Suppress("unused")
interface IsDataRecordValue<T> {
    val reference: ByteArray
}

class DataRecordValue<T: Any>(
    override val reference: ByteArray,
    val value: T,
    val version: ULong
): IsDataRecordNode(), IsDataRecordValue<T>

class DeletedValue<T: Any>(
    override val reference: ByteArray,
    val version: ULong
): IsDataRecordNode(), IsDataRecordValue<T>

class DataRecordHistoricValues<T: Any>(
    override val reference: ByteArray,
    val history: List<IsDataRecordValue<T>>
): IsDataRecordNode()
