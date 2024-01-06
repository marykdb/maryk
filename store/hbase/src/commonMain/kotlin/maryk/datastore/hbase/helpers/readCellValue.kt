package maryk.datastore.hbase.helpers

import maryk.core.properties.definitions.IsPropertyDefinition
import org.apache.hadoop.hbase.Cell

fun Cell.readValue(definition: IsPropertyDefinition<out Any>): Any? {
    var readIndex = valueOffset
    val reader = { valueArray[readIndex++] }
    return maryk.datastore.shared.readValue(definition, reader) {
        valueLength + valueOffset - readIndex
    }
}
