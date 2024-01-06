package maryk.datastore.hbase.helpers

import maryk.datastore.hbase.dataColumnFamily
import maryk.lib.extensions.compare.compareToWithOffsetLength
import org.apache.hadoop.hbase.Cell

fun createCellFilterWithPrefix(referenceAsBytes: ByteArray): (cell: Cell) -> Boolean = { cell ->
    if (
        dataColumnFamily.compareToWithOffsetLength(
            cell.familyArray,
            cell.familyOffset,
            cell.familyLength.toInt()
        ) == 0
    ) {
        // prefix check to find all matching cells
        if (referenceAsBytes.compareToWithOffsetLength(
                cell.qualifierArray,
                cell.qualifierOffset,
                referenceAsBytes.size
            ) == 0
        ) {
            true
        } else {
            false
        }
    } else false
}
