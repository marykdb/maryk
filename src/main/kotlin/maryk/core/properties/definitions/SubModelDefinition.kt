package maryk.core.properties.definitions

import maryk.core.json.JsonReader
import maryk.core.json.JsonWriter
import maryk.core.objects.DataModel
import maryk.core.properties.exceptions.PropertyValidationException
import maryk.core.properties.references.CanHaveComplexChildReference
import maryk.core.properties.references.CanHaveSimpleChildReference
import maryk.core.properties.references.PropertyReference
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType

/**
 * Definition for submodel properties
 * @param dataModel definition of the DataObject
 * @param <D>  Type of model for this definition
 * @param <DO> DataModel which is contained within SubModel
 */
class SubModelDefinition<DO : Any, out D : DataModel<DO>>(
        name: String? = null,
        index: Int = -1,
        indexed: Boolean = false,
        searchable: Boolean = true,
        required: Boolean = false,
        final: Boolean = false,
        val dataModel: D
) : AbstractSubDefinition<DO>(
        name, index, indexed, searchable, required, final
) {
    override fun getRef(parentRefFactory: () -> PropertyReference<*, *>?) =
            CanHaveSimpleChildReference<DO, SubModelDefinition<DO, D>>(
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

    override fun readJson(reader: JsonReader) = this.dataModel.readJsonToObject(reader)

    override fun writeTransportBytesWithKey(index: Int, value: DO, reserver: (size: Int) -> Unit, writer: (byte: Byte) -> Unit) {
        ProtoBuf.writeKey(this.index, WireType.START_GROUP, reserver, writer)
        this.dataModel.writeProtoBuf(value, reserver, writer)
        ProtoBuf.writeKey(this.index, WireType.END_GROUP, reserver, writer)
    }

    // With a length of -1 it should read until key with wire type END_GROUP
    override fun readTransportBytes(length: Int, reader: () -> Byte) = if(length == -1) {
        this.dataModel.readProtoBufToObject(reader)
    } else {
        this.dataModel.readProtoBufToObject(length, reader)
    }
}