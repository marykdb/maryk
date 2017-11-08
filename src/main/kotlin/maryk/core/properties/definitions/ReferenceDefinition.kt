package maryk.core.properties.definitions

import maryk.core.objects.RootDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.types.Key
import maryk.core.protobuf.WireType

/** Definition for a reference to another DataObject*/
class ReferenceDefinition<DO: Any>(
        name: String? = null,
        index: Int = -1,
        indexed: Boolean = false,
        searchable: Boolean = true,
        required: Boolean = false,
        final: Boolean = false,
        unique: Boolean = false,
        minValue: Key<DO>? = null,
        maxValue: Key<DO>? = null,
        val dataModel: RootDataModel<DO>
): AbstractSimpleDefinition<Key<DO>, IsPropertyContext>(
        name, index, indexed, searchable, required, final, WireType.LENGTH_DELIMITED, unique, minValue, maxValue
), IsFixedBytesEncodable<Key<DO>> {
    override val byteSize = dataModel.key.size

    override fun calculateStorageByteLength(value: Key<DO>) = this.byteSize

    override fun writeStorageBytes(value: Key<DO>, writer: (byte: Byte) -> Unit)  = value.writeBytes(writer)

    override fun readStorageBytes(length: Int, reader: () -> Byte) = dataModel.key.get(reader)

    override fun calculateTransportByteLength(value: Key<DO>) = this.byteSize

    @Throws(ParseException::class)
    override fun fromString(string: String) = try {
        dataModel.key.get(string)
    } catch (e: Throwable) { throw ParseException(string, e) }
}