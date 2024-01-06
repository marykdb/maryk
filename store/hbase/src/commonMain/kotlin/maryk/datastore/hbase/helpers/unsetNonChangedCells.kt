package maryk.datastore.hbase.helpers

import maryk.datastore.hbase.dataColumnFamily
import maryk.datastore.shared.TypeIndicator
import maryk.lib.extensions.compare.compareToWithOffsetLength
import org.apache.hadoop.hbase.Cell
import org.apache.hadoop.hbase.client.Put

fun unsetNonChangedCells(qualifiersToKeep: List<ByteArray>, put: Put): (cell: Cell) -> Unit = { cell ->
    if (
        qualifiersToKeep.binarySearch {
            it.compareToWithOffsetLength(cell.qualifierArray, cell.qualifierOffset, cell.qualifierLength)
        } < 0
    ) {
        put.addColumn(dataColumnFamily, cell.qualifierArray.copyOfRange(cell.qualifierOffset, cell.qualifierOffset + cell.qualifierLength), TypeIndicator.DeletedIndicator.byteArray)
    }
}
