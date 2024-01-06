package maryk.datastore.hbase.helpers

import maryk.core.extensions.bytes.writeBytes
import maryk.core.properties.references.ListReference
import maryk.datastore.hbase.dataColumnFamily
import org.apache.hadoop.hbase.client.Put
import org.apache.hadoop.hbase.client.Result

/**
 * Get list from [currentRowResult] at [reference] by reading and collecting all values
 */
internal fun <T : Any> getList(
    currentRowResult: Result,
    put: Put,
    reference: ListReference<T, *>
): MutableList<T> {
    val referenceAsBytes = reference.toStorageByteArray()

    val putCells = put.get(dataColumnFamily, referenceAsBytes)
    val putCount = if (putCells.isNotEmpty()) {
        val putCell = putCells.last()
        putCell.readCountValue()
    } else null

    // First handle the count
    val count = currentRowResult.getColumnLatestCell(dataColumnFamily, referenceAsBytes)?.readCountValue() ?: return mutableListOf()

    val list = ArrayList<T>(count)

    for (i in 0..maxOf(count, putCount ?: 0)) {
        var byteIndex = referenceAsBytes.size
        val refToRead = referenceAsBytes.copyOf(byteIndex + 4)
        i.toUInt().writeBytes({
            refToRead[byteIndex++] = it
        })
        val putCell = put.get(dataColumnFamily, refToRead)
        if (putCell.isNotEmpty()) {
            putCell.last().readValue(reference.comparablePropertyDefinition.valueDefinition)?.also {
                @Suppress("UNCHECKED_CAST")
                list.add(it as T)
            }
        } else {
            val cell = currentRowResult.getColumnLatestCell(dataColumnFamily, refToRead)
            cell?.readValue(reference.comparablePropertyDefinition.valueDefinition)?.also {
                @Suppress("UNCHECKED_CAST")
                list.add(it as T)
            }
        }
    }

    return list
}
