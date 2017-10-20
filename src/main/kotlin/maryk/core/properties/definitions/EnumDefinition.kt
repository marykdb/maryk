package maryk.core.properties.definitions

import maryk.core.extensions.bytes.computeVarByteSize
import maryk.core.extensions.bytes.initShort
import maryk.core.extensions.bytes.initShortByVar
import maryk.core.extensions.bytes.writeBytes
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.types.IndexedEnum
import maryk.core.protobuf.ProtoBuf
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
) : AbstractSimpleDefinition<E>(
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

    override fun readStorageBytes(length: Int, reader:() -> Byte) =
            getEnumByIndex(initShort(reader).toInt() - Short.MIN_VALUE)

    override fun writeStorageBytes(value: E, reserver: (size: Int) -> Unit, writer: (byte: Byte) -> Unit) {
        reserver(2)
        value.indexAsShortToStore.writeBytes(writer)
    }

    override fun readTransportBytes(length: Int, reader: () -> Byte) =
            getEnumByIndex(initShortByVar(reader).toInt())

    override fun writeTransportBytesWithKey(value: E, reserver: (size: Int) -> Unit, writer: (byte: Byte) -> Unit) {
        ProtoBuf.writeKey(this.index, WireType.VAR_INT, reserver, writer)
        this.writeTransportBytes(value, reserver, writer)
    }

    override fun writeTransportBytes(value: E, reserver: (size: Int) -> Unit, writer: (byte: Byte) -> Unit) {
        reserver(value.index.computeVarByteSize())
        value.index.writeVarBytes(writer)
    }

    override fun asString(value: E) = value.name

    override fun fromString(string: String) =
        valueByString[string] ?: throw ParseException(string)
}