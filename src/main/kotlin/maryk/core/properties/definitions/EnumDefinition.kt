package maryk.core.properties.definitions

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.initShort
import maryk.core.extensions.bytes.initShortByVar
import maryk.core.extensions.bytes.writeBytes
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.types.IndexedEnum
import maryk.core.protobuf.WireType

/** Definition for Enum properties */
class EnumDefinition<E: IndexedEnum<E>>(
        name: String? = null,
        index: Int = -1,
        indexed: Boolean = false,
        searchable: Boolean = true,
        required: Boolean = false,
        final: Boolean = false,
        unique: Boolean = false,
        minValue: E? = null,
        maxValue: E? = null,
        val values: Array<E>
) : AbstractSimpleDefinition<E, IsPropertyContext>(
        name, index, indexed, searchable, required, final, WireType.VAR_INT, unique, minValue, maxValue
), IsFixedBytesEncodable<E> {
    override val byteSize = 2

    private val valueByString: Map<String, E> by lazy {
        values.associate { Pair(it.name, it) }
    }

    private val valueByIndex: Map<Int, E> by lazy {
        values.associate { Pair(it.index, it) }
    }
    private fun getEnumByIndex(index: Int) = valueByIndex[index] ?: throw ParseException("Enum index does not exist $index")

    override fun readStorageBytes(context: IsPropertyContext?, length: Int, reader:() -> Byte) =
            getEnumByIndex(initShort(reader).toInt() - Short.MIN_VALUE)

    override fun calculateStorageByteLength(value: E) = this.byteSize

    override fun writeStorageBytes(value: E, writer: (byte: Byte) -> Unit) {
        value.indexAsShortToStore.writeBytes(writer)
    }

    override fun readTransportBytes(context: IsPropertyContext?, length: Int, reader: () -> Byte)
            = getEnumByIndex(initShortByVar(reader).toInt())

    override fun calculateTransportByteLength(value: E) = value.index.calculateVarByteLength()

    override fun writeTransportBytes(value: E, writer: (byte: Byte) -> Unit)
            = value.index.writeVarBytes(writer)

    override fun asString(value: E) = value.name

    override fun fromString(string: String, context: IsPropertyContext?) =
        valueByString[string] ?: throw ParseException(string)
}