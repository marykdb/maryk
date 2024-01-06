package maryk.datastore.hbase.helpers

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.references.IncMapReference
import maryk.lib.extensions.compare.compareDefinedTo
import maryk.lib.extensions.compare.compareToWithOffsetLength
import org.apache.hadoop.hbase.Cell
import org.apache.hadoop.hbase.client.Result

/** Get the current incrementing map key for [reference] */
internal fun getCurrentIncMapKey(
    currentRowResult: Result,
    reference: IncMapReference<Comparable<Any>, Any, IsPropertyContext>
): ByteArray {
    val referenceAsBytes = reference.toStorageByteArray()

    val cellIterator = currentRowResult.rawCells().iterator()
    var currentCell: Cell?
    do {
        currentCell = cellIterator.next()
    } while (cellIterator.hasNext() && currentCell != null && referenceAsBytes.compareToWithOffsetLength(currentCell.qualifierArray, currentCell.qualifierOffset, currentCell.qualifierLength) < 0)

    if (currentCell != null) {
        // goto first content item
        currentCell = cellIterator.next()

        if (referenceAsBytes.compareDefinedTo(currentCell.qualifierArray, currentCell.qualifierOffset, currentCell.qualifierLength) == 0) {
            return currentCell.qualifierArray.copyOfRange(currentCell.qualifierOffset, currentCell.qualifierOffset + currentCell.qualifierLength)
        }
    }

    // If nothing was found create a new reference
    val mapKeySize  = reference.propertyDefinition.definition.keyDefinition.byteSize
    return ByteArray(mapKeySize) { if (it == 0) mapKeySize.toByte() else 0xFF.toByte() }
}
