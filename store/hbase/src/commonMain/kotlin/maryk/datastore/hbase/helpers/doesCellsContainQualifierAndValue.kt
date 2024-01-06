package maryk.datastore.hbase.helpers

import maryk.lib.extensions.compare.compareToWithOffsetLength
import org.apache.hadoop.hbase.Cell

fun doesCellsContainQualifierAndValue(currentValues: List<Cell>): (qualifier: ByteArray, value: ByteArray) -> Boolean = { qualifier, value ->
    var toWrite = true
    for (currentCell in currentValues) {
        val compare = qualifier.compareToWithOffsetLength(currentCell.qualifierArray, currentCell.qualifierOffset, currentCell.qualifierLength)
        when {
            compare == 0 -> {
                if (value.compareToWithOffsetLength(currentCell.valueArray, currentCell.valueOffset, currentCell.valueLength) == 0) {
                    toWrite = false
                }
                break
            }
            compare < 0 -> break
            else -> continue
        }
    }
    toWrite
}
