package maryk.core.properties.definitions

import maryk.core.extensions.bytes.initShort
import maryk.core.extensions.bytes.toBytes
import maryk.core.extensions.bytes.writeBytes
import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.types.IndexedEnum

/** Definition for Enum properties */
class EnumDefinition<E: IndexedEnum<E>>(
        name: String? = null,
        index: Short = -1,
        indexed: Boolean = false,
        searchable: Boolean = true,
        required: Boolean = false,
        final: Boolean = false,
        unique: Boolean = false,
        minValue: E? = null,
        maxValue: E? = null,
        val values: Array<E>
) : AbstractSimpleDefinition<E>(
        name, index, indexed, searchable, required, final, unique, minValue, maxValue
), IsFixedBytesEncodable<E> {
    override val byteSize = 2

    private val valueByString: Map<String, E> by lazy {
        values.associate { Pair(it.name, it) }
    }
    private val valueByIndex: Map<Short, E> by lazy {
        values.associate { Pair(it.indexAsShort, it) }
    }

    override fun convertToBytes(value: E, bytes: ByteArray?, offset: Int) = value.indexAsShort.toBytes(bytes, offset)

    override fun convertFromBytes(bytes: ByteArray, offset: Int, length: Int) =
            valueByIndex[initShort(bytes, offset)] ?: throw ParseException("Enum index does not exist ${initShort(bytes, offset)}")

    override fun convertFromBytes(length: Int, reader:() -> Byte) =
            valueByIndex[initShort(reader)] ?: throw ParseException("Enum index does not exist ${initShort(reader)}")

    override fun convertToBytes(value: E, reserver: (size: Int) -> Unit, writer: (byte: Byte) -> Unit) {
        reserver(2)
        value.indexAsShort.writeBytes(writer)
    }

    override fun convertToString(value: E, optimized: Boolean) = when {
        optimized -> value.indexAsShort.toString()
        else -> value.name
    }

    override fun convertFromString(string: String, optimized: Boolean) = when {
        optimized -> {
            val short = try { string.toShort() } catch (e: Throwable) { throw ParseException(string, e)}
            valueByIndex[short] ?: throw ParseException(string)
        }
        else -> valueByString[string] ?: throw ParseException(string)
    }
}