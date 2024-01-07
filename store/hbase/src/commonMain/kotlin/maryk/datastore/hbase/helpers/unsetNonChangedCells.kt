package maryk.datastore.hbase.helpers

import maryk.datastore.hbase.dataColumnFamily
import maryk.datastore.shared.TypeIndicator
import maryk.lib.extensions.compare.compareTo
import maryk.lib.extensions.compare.compareToWithOffsetLength
import org.apache.hadoop.hbase.Cell
import org.apache.hadoop.hbase.client.Put

fun unsetNonChangedCells(currentValues: List<Cell>, qualifiersToKeep: MutableList<ByteArray>, put: Put) {
    qualifiersToKeep.sortWith { o1, o2 -> o1.compareTo(o2) }
    var minIndex = 0
    for (cell in currentValues) {
        val index = qualifiersToKeep.binarySearch(fromIndex = minIndex) {
            it.compareToWithOffsetLength(cell.qualifierArray, cell.qualifierOffset, cell.qualifierLength)
        }
        if (index < 0) {
            put.addColumn(
                dataColumnFamily,
                cell.qualifierArray.copyOfRange(cell.qualifierOffset, cell.qualifierOffset + cell.qualifierLength),
                TypeIndicator.DeletedIndicator.byteArray
            )
        } else {
            // Start next time comparing with next value in qualifiersToKeep as they are ordered
            minIndex = index + 1
        }
    }
}
