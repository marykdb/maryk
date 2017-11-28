package maryk.core.properties.definitions

import maryk.core.extensions.bytes.initBoolean
import maryk.core.extensions.bytes.writeBytes
import maryk.core.json.JsonWriter
import maryk.core.properties.IsPropertyContext
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
): AbstractSimpleDefinition<Boolean, IsPropertyContext>(
    name, index, indexed, searchable, required, final, WireType.VAR_INT, unique, minValue = false, maxValue = true
), IsFixedBytesEncodable<Boolean> {
    override val byteSize = 1

    override fun readStorageBytes(length: Int, reader: () -> Byte) = initBoolean(reader)

    override fun calculateStorageByteLength(value: Boolean) = this.byteSize

    override fun writeStorageBytes(value: Boolean, writer: (byte: Byte) -> Unit) = value.writeBytes(writer)

    override fun calculateTransportByteLength(value: Boolean) = this.byteSize

    override fun fromString(string: String) = when(string) {
        "true" -> true
        "false" -> false
        else -> throw ParseException(string)
    }

    override fun writeJsonValue(value: Boolean, writer: JsonWriter, context: IsPropertyContext?) {
        writer.writeValue(
                this.asString(value)
        )
    }
}