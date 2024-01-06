package maryk.datastore.hbase.helpers

import maryk.core.extensions.bytes.toVarBytes
import maryk.core.properties.references.TypedPropertyReference
import maryk.datastore.hbase.dataColumnFamily
import maryk.datastore.shared.TypeIndicator
import org.apache.hadoop.hbase.client.Put
import org.apache.hadoop.hbase.client.Result

/**
 * Set count for [reference] by applying [countChange] to current count
 */
internal fun <T : Any> createCountUpdater(
    currentRowResult: Result,
    reference: TypedPropertyReference<out T>,
    put: Put,
    countChange: Int,
    sizeValidator: (UInt) -> Unit
) {
    val referenceToCompareTo = reference.toStorageByteArray()

    val previousCount = currentRowResult.getColumnLatestCell(dataColumnFamily, referenceToCompareTo)?.readCountValue() ?: 0
    val newCount = maxOf(0, previousCount + countChange)

    sizeValidator(newCount.toUInt())

    put.addColumn(dataColumnFamily, referenceToCompareTo, byteArrayOf(TypeIndicator.NoTypeIndicator.byte, *newCount.toVarBytes()))
}
