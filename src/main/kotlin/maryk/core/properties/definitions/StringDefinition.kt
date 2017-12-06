package maryk.core.properties.definitions

import maryk.core.bytes.calculateUTF8ByteLength
import maryk.core.bytes.initString
import maryk.core.bytes.writeUTF8Bytes
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.exceptions.InvalidSizeException
import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.properties.references.IsPropertyReference
import maryk.core.protobuf.WireType

/** Definition for String properties */
class StringDefinition(
        indexed: Boolean = false,
        searchable: Boolean = true,
        required: Boolean = false,
        final: Boolean = false,
        unique: Boolean = false,
        minValue: String? = null,
        maxValue: String? = null,
        override val minSize: Int? = null,
        override val maxSize: Int? = null,
        val regEx: String? = null
) : AbstractSimpleDefinition<String, IsPropertyContext>(
        indexed, searchable, required, final, WireType.LENGTH_DELIMITED, unique, minValue, maxValue
), HasSizeDefinition, IsSerializableFlexBytesEncodable<String, IsPropertyContext> {
    private val _regEx by lazy {
        when {
            this.regEx != null -> Regex(this.regEx)
            else -> null
        }
    }

    override fun readStorageBytes(length: Int, reader: () -> Byte) = initString(length, reader)

    override fun calculateStorageByteLength(value: String) = value.calculateUTF8ByteLength()

    override fun writeStorageBytes(value: String, writer: (byte: Byte) -> Unit) = value.writeUTF8Bytes(writer)

    override fun calculateTransportByteLength(value: String) = value.calculateUTF8ByteLength()

    override fun asString(value: String) = value

    override fun fromString(string: String) = string

    override fun validateWithRef(previousValue: String?, newValue: String?, refGetter: () -> IsPropertyReference<String, IsPropertyDefinition<String>>?) {
        super.validateWithRef(previousValue, newValue, refGetter)

        when {
            newValue != null -> {
                when {
                    isSizeToSmall(newValue.length) || isSizeToBig(newValue.length)
                    -> throw InvalidSizeException(
                            refGetter(), newValue, this.minSize, this.maxSize
                    )
                    this._regEx != null && !(this._regEx!! matches newValue)
                    -> throw InvalidValueException(
                            refGetter(), newValue
                    )
                }
            }
        }
    }
}