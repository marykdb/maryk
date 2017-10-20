package maryk.core.properties.definitions

import maryk.core.bytes.initString
import maryk.core.bytes.writeBytes
import maryk.core.properties.exceptions.PropertyInvalidSizeException
import maryk.core.properties.exceptions.PropertyInvalidValueException
import maryk.core.properties.exceptions.PropertyValidationException
import maryk.core.properties.references.PropertyReference
import maryk.core.protobuf.WireType

/**
 * Definition for String properties
 */
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
) : AbstractSimpleDefinition<String>(
        name, index, indexed, searchable, required, final, WireType.LENGTH_DELIMITED, unique, minValue, maxValue
), HasSizeDefinition {

    private val _regEx by lazy {
        when {
            this.regEx != null -> Regex(this.regEx)
            else -> null
        }
    }

    override fun readStorageBytes(length: Int, reader:() -> Byte) = initString(length, reader)

    override fun writeStorageBytes(value: String, reserver: (size: Int) -> Unit, writer: (byte: Byte) -> Unit) = value.writeBytes(reserver, writer)

    override fun asString(value: String) = value

    override fun fromString(string: String) = string

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