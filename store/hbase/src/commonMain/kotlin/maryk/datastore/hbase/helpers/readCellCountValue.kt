package maryk.datastore.hbase.helpers

import maryk.core.extensions.bytes.initIntByVar
import maryk.datastore.shared.TypeIndicator
import org.apache.hadoop.hbase.Cell

fun Cell.readCountValue(): Int? {
    if (valueLength == 1 && valueArray[valueOffset] == TypeIndicator.DeletedIndicator.byte) return null
    var readIndex = valueOffset + 1
    return initIntByVar { valueArray[readIndex++] }
}
