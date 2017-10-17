package maryk.core.properties.definitions

import maryk.core.extensions.bytes.initBoolean
import maryk.core.extensions.bytes.writeBytes
import maryk.core.json.JsonGenerator
import maryk.core.properties.exceptions.ParseException
import maryk.core.protobuf.WireType

/** Definition for Boolean properties */
class BooleanDefinition(
        name: String? = null,
        index: Int = -1,
        indexed: Boolean = false,
        searchable: Boolean = true,
        required: Boolean = false,
        final: Boolean = false,
        unique: Boolean = false
): AbstractSimpleDefinition<Boolean>(
    name, index, indexed, searchable, required, final, WireType.VAR_INT, unique, minValue = false, maxValue = true
), IsFixedBytesEncodable<Boolean> {
    override val byteSize = 1

    override fun convertFromStorageBytes(length: Int, reader:() -> Byte) = initBoolean(reader)

    override fun convertToStorageBytes(value: Boolean, reserver: (size: Int) -> Unit, writer: (byte: Byte) -> Unit) {
        reserver(1)
        value.writeBytes(writer)
    }

    override fun writeTransportBytes(value: Boolean, reserver: (size: Int) -> Unit, writer: (byte: Byte) -> Unit)
            = convertToStorageBytes(value, reserver, writer)

    @Throws(ParseException::class)
    override fun convertFromString(string: String) = when(string) {
        "true" -> true
        "false" -> false
        else -> throw ParseException(string)
    }

    override fun writeJsonValue(generator: JsonGenerator, value: Boolean) {
        generator.writeValue(
                this.convertToString(value)
        )
    }
}