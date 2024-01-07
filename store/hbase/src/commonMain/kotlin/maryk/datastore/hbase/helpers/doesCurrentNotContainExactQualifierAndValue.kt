package maryk.datastore.hbase.helpers

import maryk.lib.extensions.compare.compareToWithOffsetLength
import org.apache.hadoop.hbase.Cell

/**
 * Returns a function which returns true if the currentValue does not have qualifier or current value is not matching the new value
 */
fun doesCurrentNotContainExactQualifierAndValue(currentValues: List<Cell>): (qualifier: ByteArray, newValue: ByteArray) -> Boolean = { qualifier, newValue ->
    val index = currentValues.binarySearch { cell -> qualifier.compareToWithOffsetLength(cell.qualifierArray, cell.qualifierOffset, cell.qualifierLength) }

    if (index < 0) {
        true
    } else {
        val cell = currentValues[index]
        newValue.compareToWithOffsetLength(cell.valueArray, cell.valueOffset, cell.valueLength) != 0
    }
}
