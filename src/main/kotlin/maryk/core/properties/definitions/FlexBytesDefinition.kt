package maryk.core.properties.definitions

import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.exceptions.PropertyInvalidSizeException
import maryk.core.properties.exceptions.PropertyValidationException
import maryk.core.properties.references.PropertyReference
import maryk.core.properties.types.Bytes

/** Definition for a bytes array with fixed length */
class FlexBytesDefinition(
        name: String? = null,
        index: Short = -1,
        indexed: Boolean = false,
        searchable: Boolean = true,
        required: Boolean = false,
        final: Boolean = false,
        unique: Boolean = false,
        minValue: Bytes? = null,
        maxValue: Bytes? = null,
        override val minSize: Int? = null,
        override val maxSize: Int? = null
): AbstractSimpleDefinition<Bytes>(
    name, index, indexed, searchable, required, final, unique, minValue, maxValue
), HasSizeDefinition {
    override fun convertFromBytes(length: Int, reader:() -> Byte) = Bytes.fromByteReader(length, reader)

    override fun convertToBytes(value: Bytes, reserver: (size: Int) -> Unit, writer: (byte: Byte) -> Unit) = value.writeBytes(reserver, writer)

    @Throws(ParseException::class)
    override fun convertFromString(string: String) = try {
        Bytes.ofBase64String(string)
    } catch (e: NumberFormatException) { throw ParseException(string, e) }

    @Throws(PropertyValidationException::class)
    override fun validate(previousValue: Bytes?, newValue: Bytes?, parentRefFactory: () -> PropertyReference<*, *>?) {
        super.validate(previousValue, newValue, parentRefFactory)

        if (newValue != null && (isSizeToSmall(newValue.size) || isSizeToBig(newValue.size))) {
            throw PropertyInvalidSizeException(
                    this.getRef(parentRefFactory), newValue.toHex(), this.minSize, this.maxSize
            )
        }
    }
}