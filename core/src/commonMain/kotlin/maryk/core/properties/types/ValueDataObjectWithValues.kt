package maryk.core.properties.types

import maryk.core.models.IsObjectDataModel
import maryk.core.models.IsValueDataModel
import maryk.core.models.serializers.readValueObjectItems
import maryk.core.values.ObjectValues

/** Value Data Objects which can be used to represent as fixed length bytes */
class ValueDataObjectWithValues(
    _bytes: ByteArray,
    values: ObjectValues<*, *>
) : ValueDataObject(_bytes) {
    private val ownedValues: ObjectValues<*, *>

    init {
        @Suppress("UNCHECKED_CAST")
        val model = values.dataModel as? IsValueDataModel<ValueDataObject, IsObjectDataModel<ValueDataObject>>
            ?: throw IllegalArgumentException("A value object cache requires a value data model")
        require(this._bytes.size == model.Serializer.byteSize) { "Invalid value object byte size" }
        @Suppress("UNCHECKED_CAST")
        require(model.toBytes(values as ObjectValues<ValueDataObject, IsObjectDataModel<ValueDataObject>>).contentEquals(this._bytes)) {
            "Value object bytes do not match the supplied values"
        }
        var offset = 0
        ownedValues = values.copy(values = readValueObjectItems(model) { this._bytes[offset++] })
    }

    /** Returned slots belong to the caller; the byte-derived cache remains immutable. */
    val values: ObjectValues<*, *>
        get() {
            var offset = 0
            val model = ownedValues.dataModel as IsValueDataModel<*, *>
            return ownedValues.copy(values = readValueObjectItems(model) { _bytes[offset++] })
        }

    internal fun getValue(index: UInt): Any = ownedValues(index)
}
