package maryk.core.properties.definitions

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.json.JsonReader
import maryk.core.json.JsonWriter
import maryk.core.objects.DataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.exceptions.PropertyValidationException
import maryk.core.properties.references.CanHaveComplexChildReference
import maryk.core.properties.references.CanHaveSimpleChildReference
import maryk.core.properties.references.PropertyReference
import maryk.core.protobuf.ByteLengthContainer
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType

/**
 * Definition for submodel properties
 * @param dataModel definition of the DataObject
 * @param <D>  Type of model for this definition
 * @param <DO> DataModel which is contained within SubModel
 */
class SubModelDefinition<DO : Any, out D : DataModel<DO, IsPropertyContext>>(
        name: String? = null,
        index: Int = -1,
        indexed: Boolean = false,
        searchable: Boolean = true,
        required: Boolean = false,
        final: Boolean = false,
        val dataModel: D
) : AbstractSubDefinition<DO, IsPropertyContext>(
        name, index, indexed, searchable, required, final
) {
    override fun getRef(parentRefFactory: () -> PropertyReference<*, *>?) =
            CanHaveSimpleChildReference(
                this,
                parentRefFactory()?.let {
                    it as CanHaveComplexChildReference<*, *>
                },
                dataModel = this.dataModel
            )

    @Throws(PropertyValidationException::class)
    override fun validate(previousValue: DO?, newValue: DO?, parentRefFactory: () -> PropertyReference<*, *>?) {
        super.validate(previousValue, newValue, parentRefFactory)
        if (newValue != null) {
            this.dataModel.validate(
                    parentRefFactory = { this.getRef(parentRefFactory) },
                    dataObject = newValue
            )
        }
    }

    override fun writeJsonValue(writer: JsonWriter, value: DO) = this.dataModel.writeJson(writer, value)

    override fun readJson(context: IsPropertyContext?, reader: JsonReader) = this.dataModel.readJsonToObject(reader, context)

    override fun calculateTransportByteLengthWithKey(index: Int, value: DO, lengthCacher: (length: ByteLengthContainer) -> Unit): Int {
        // Set up container to store byte length
        val container = ByteLengthContainer()
        lengthCacher(container)

        var totalByteLength = 0
        totalByteLength += this.dataModel.calculateProtoBufLength(value, lengthCacher)
        container.length = totalByteLength // first store byte length of object

        totalByteLength += ProtoBuf.calculateKeyLength(index)
        totalByteLength += container.length.calculateVarByteLength()
        return totalByteLength
    }

    override fun writeTransportBytesWithKey(index: Int, value: DO, lengthCacheGetter: () -> Int, writer: (byte: Byte) -> Unit) {
        ProtoBuf.writeKey(index, WireType.LENGTH_DELIMITED, writer)
        lengthCacheGetter().writeVarBytes(writer)
        this.dataModel.writeProtoBuf(value, lengthCacheGetter, writer)
    }

    override fun readTransportBytes(context: IsPropertyContext?, length: Int, reader: () -> Byte)
            = this.dataModel.readProtoBufToObject(context, length, reader)
}