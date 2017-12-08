package maryk.core.properties.definitions

import maryk.core.objects.RootDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.types.Key
import maryk.core.protobuf.WireType

/** Definition for a reference to another DataObject*/
class ReferenceDefinition<DO: Any>(
        override val indexed: Boolean = false,
        override val searchable: Boolean = true,
        override val required: Boolean = true,
        override val final: Boolean = false,
        override val unique: Boolean = false,
        override val minValue: Key<DO>? = null,
        override val maxValue: Key<DO>? = null,
        val dataModel: RootDataModel<DO, *>
): IsSimpleDefinition<Key<DO>, IsPropertyContext>, IsSerializableFixedBytesEncodable<Key<DO>, IsPropertyContext> {
    override val wireType = WireType.LENGTH_DELIMITED
    override val byteSize = dataModel.key.size

    override fun calculateStorageByteLength(value: Key<DO>) = this.byteSize

    override fun writeStorageBytes(value: Key<DO>, writer: (byte: Byte) -> Unit)  = value.writeBytes(writer)

    override fun readStorageBytes(length: Int, reader: () -> Byte) = dataModel.key.get(reader)

    override fun calculateTransportByteLength(value: Key<DO>) = this.byteSize

    override fun fromString(string: String) = try {
        dataModel.key.get(string)
    } catch (e: Throwable) { throw ParseException(string, e) }
}