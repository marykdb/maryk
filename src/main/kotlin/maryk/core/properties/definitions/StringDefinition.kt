package maryk.core.properties.definitions

import maryk.core.bytes.calculateUTF8ByteLength
import maryk.core.bytes.initString
import maryk.core.bytes.writeUTF8Bytes
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.exceptions.PropertyInvalidSizeException
import maryk.core.properties.exceptions.PropertyInvalidValueException
import maryk.core.properties.exceptions.PropertyValidationException
import maryk.core.properties.references.PropertyReference
import maryk.core.protobuf.WireType

/** Definition for String properties */
class StringDefinition(
        name: String? = null,
        index: Int = -1,
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
        name, index, indexed, searchable, required, final, WireType.LENGTH_DELIMITED, unique, minValue, maxValue
), HasSizeDefinition {
    private val _regEx by lazy {
        when {
            this.regEx != null -> Regex(this.regEx)
            else -> null
        }
    }

    override fun readStorageBytes(context: IsPropertyContext?, length: Int, reader:() -> Byte) = initString(length, reader)

    override fun calculateStorageByteLength(value: String) = value.calculateUTF8ByteLength()

    override fun writeStorageBytes(value: String, writer: (byte: Byte) -> Unit) = value.writeUTF8Bytes(writer)

    override fun calculateTransportByteLength(value: String) = value.calculateUTF8ByteLength()

    override fun asString(value: String) = value

    override fun fromString(string: String, context: IsPropertyContext?) = string

    @Throws(PropertyValidationException::class)
    override fun validate(previousValue: String?, newValue: String?, parentRefFactory: () -> PropertyReference<*, *>?) {
        super.validate(previousValue, newValue, parentRefFactory)

        when {
            newValue != null -> {
                when {
                    isSizeToSmall(newValue.length) || isSizeToBig(newValue.length)
                    -> throw PropertyInvalidSizeException(
                            this.getRef(parentRefFactory), newValue, this.minSize, this.maxSize
                    )
                    this._regEx != null && !(this._regEx!! matches newValue)
                    -> throw PropertyInvalidValueException(
                            this.getRef(parentRefFactory), newValue
                    )
                }
            }
        }
    }
}