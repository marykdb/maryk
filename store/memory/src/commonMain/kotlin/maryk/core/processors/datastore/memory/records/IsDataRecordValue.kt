@file:Suppress("EXPERIMENTAL_API_USAGE")

package maryk.core.processors.datastore.memory.records

sealed class IsDataRecordValue {
    abstract val reference: ByteArray
}

class DataRecordValue<T: Any>(
    override val reference: ByteArray,
    val value: T,
    val version: ULong
): IsDataRecordValue()

class DataRecordHistoricValues<T: Any>(
    override val reference: ByteArray,
    val history: List<DataRecordValue<T>>
): IsDataRecordValue()
