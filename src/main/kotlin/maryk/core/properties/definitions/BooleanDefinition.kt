package maryk.core.properties.definitions

import maryk.core.extensions.bytes.initBoolean
import maryk.core.extensions.bytes.toBytes
import maryk.core.properties.exceptions.ParseException

/** Definition for Boolean properties */
class BooleanDefinition(
        name: String? = null,
        index: Short = -1,
        indexed: Boolean = false,
        searchable: Boolean = true,
        required: Boolean = false,
        final: Boolean = false,
        unique: Boolean = false
): AbstractSimpleDefinition<Boolean>(
    name, index, indexed, searchable, required, final, unique, minValue = false, maxValue = true
), IsFixedBytesEncodable<Boolean> {
    override val byteSize = 1

    override fun convertToBytes(value: Boolean, bytes: ByteArray?, offset: Int) = value.toBytes(bytes, offset)

    override fun convertFromBytes(bytes: ByteArray, offset: Int, length: Int) = initBoolean(bytes, offset)

    @Throws(ParseException::class)
    override fun convertFromString(string: String, optimized: Boolean) = when(string) {
        "true" -> true
        "false" -> false
        else -> throw ParseException(string)
    }
}