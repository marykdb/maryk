package maryk.core.properties.definitions

import maryk.core.json.JsonReader
import maryk.core.json.JsonWriter
import maryk.core.objects.DataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.wrapper.IsDataObjectProperty
import maryk.core.properties.references.IsPropertyReference
import maryk.core.protobuf.ByteLengthContainer
import maryk.core.protobuf.WireType

/**
 * Definition for submodel properties
 * @param dataModel definition of the DataObject
 * @param <D>  Type of model for this definition
 * @param <DO> DataModel which is contained within SubModel
 */
class SubModelDefinition<DO : Any, out D : DataModel<DO, CX>, in CX: IsPropertyContext>(
        indexed: Boolean = false,
        searchable: Boolean = true,
        required: Boolean = false,
        final: Boolean = false,
        val dataModel: D
) : AbstractValueDefinition<DO, CX>(
        indexed, searchable, required, final, wireType = WireType.LENGTH_DELIMITED
), IsSerializablePropertyDefinition<DO, CX>, IsSubModelDefinition<DO, CX> {
    override fun asString(value: DO, context: CX?): String {
        var string = ""
        this.writeJsonValue(value, maryk.core.json.JsonWriter {
            string += it
        }, context)
        return string
    }

    override fun fromString(string: String, context: CX?): DO {
        val stringIterator = string.iterator()
        return this.readJson(JsonReader { stringIterator.nextChar() }, context)
    }

    override fun getEmbeddedByName(name: String): IsDataObjectProperty<*, *, *>? = dataModel.getDefinition(name)

    override fun getEmbeddedByIndex(index: Int): IsDataObjectProperty<*, *, *>? = dataModel.getDefinition(index)

    override fun validateWithRef(previousValue: DO?, newValue: DO?, refGetter: () -> IsPropertyReference<DO, IsPropertyDefinition<DO>>?) {
        super.validateWithRef(previousValue, newValue, refGetter)
        if (newValue != null) {
            this.dataModel.validate(
                    refGetter = refGetter,
                    dataObject = newValue
            )
        }
    }

    override fun writeJsonValue(value: DO, writer: JsonWriter, context: CX?) = this.dataModel.writeJson(value, writer, context)

    override fun readJson(reader: JsonReader, context: CX?) = this.dataModel.readJsonToObject(reader, context)

    override fun calculateTransportByteLength(value: DO, lengthCacher: (length: ByteLengthContainer) -> Unit, context: CX?): Int {
        var totalByteLength = 0
        totalByteLength += this.dataModel.calculateProtoBufLength(value, lengthCacher, context)
        return totalByteLength
    }

    override fun writeTransportBytes(value: DO, lengthCacheGetter: () -> Int, writer: (byte: Byte) -> Unit, context: CX?) {
        this.dataModel.writeProtoBuf(value, lengthCacheGetter, writer, context)
    }

    override fun readTransportBytes(length: Int, reader: () -> Byte, context: CX?)
            = this.dataModel.readProtoBufToObject(length, reader, context)
}