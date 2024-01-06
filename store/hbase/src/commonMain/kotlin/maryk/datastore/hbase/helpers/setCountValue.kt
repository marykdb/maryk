package maryk.datastore.hbase.helpers

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.writeVarBytes
import maryk.datastore.shared.TypeIndicator

fun countValueAsBytes(value: Int) = ByteArray(value.calculateVarByteLength() + 1).also { bytes ->
    bytes[0] = TypeIndicator.NoTypeIndicator.byte
    var index = 1
    value.writeVarBytes {
        bytes[index++] = it
    }
}
