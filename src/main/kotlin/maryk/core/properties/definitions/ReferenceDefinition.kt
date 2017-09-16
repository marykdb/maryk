package maryk.core.properties.definitions

import maryk.core.objects.RootDataModel
import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.types.Key

/** Definition for a reference to another DataObject*/
class ReferenceDefinition<DO: Any>(
        name: String? = null,
        index: Short = -1,
        indexed: Boolean = false,
        searchable: Boolean = true,
        required: Boolean = false,
        final: Boolean = false,
        unique: Boolean = false,
        minValue: Key<DO>? = null,
        maxValue: Key<DO>? = null,
        val dataModel: RootDataModel<DO>
): AbstractSimpleDefinition<Key<DO>>(
        name, index, indexed, searchable, required, final, unique, minValue, maxValue
), IsFixedBytesEncodable<Key<DO>> {
    override val byteSize = dataModel.key.size

    override fun convertToBytes(value: Key<DO>, reserver: (size: Int) -> Unit, writer: (byte: Byte) -> Unit) {
        value.writeBytes(reserver, writer)
    }

    override fun convertFromBytes(length: Int, reader: () -> Byte) = dataModel.key.get(reader)

    override fun convertToBytes(value: Key<DO>, bytes: ByteArray?, offset: Int) = when(bytes) {
        null -> value.bytes
        else -> value.toBytes(bytes, offset)
    }

    override fun convertFromBytes(bytes: ByteArray, offset: Int, length: Int) =
            dataModel.key.get(bytes.copyOfRange(offset, this.byteSize + offset))

    @Throws(ParseException::class)
    override fun convertFromString(string: String, optimized: Boolean) = try {
        dataModel.key.get(string)
    } catch (e: Throwable) { throw ParseException(string, e) }
}