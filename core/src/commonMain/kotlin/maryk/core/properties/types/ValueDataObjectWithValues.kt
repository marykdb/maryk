package maryk.core.properties.types

import maryk.core.values.ObjectValues

/** Value Data Objects which can be used to represent as fixed length bytes */
class ValueDataObjectWithValues(
    _bytes: ByteArray,
    val values: ObjectValues<*, *>
) : ValueDataObject(_bytes)
