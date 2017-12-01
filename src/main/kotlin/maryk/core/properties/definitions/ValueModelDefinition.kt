package maryk.core.properties.definitions

import maryk.core.extensions.bytes.writeBytes
import maryk.core.json.JsonReader
import maryk.core.json.JsonWriter
import maryk.core.objects.ValueDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.references.CanHaveComplexChildReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.ValueDataObject
import maryk.core.protobuf.WireType

/** Definition for value model properties
 * @param dataModel definition of the DataObject
 * @param <D>  Type of model for this definition
 * @param <DO> DataModel which is contained within SubModel
 */
class ValueModelDefinition<DO: ValueDataObject, out D : ValueDataModel<DO>>(
        name: String? = null,
        index: Int = -1,
        indexed: Boolean = false,
        searchable: Boolean = true,
        required: Boolean = false,
        final: Boolean = false,
        unique: Boolean = false,
        minValue: DO? = null,
        maxValue: DO? = null,
        val dataModel: D
) : AbstractSimpleDefinition<DO, IsPropertyContext>(
        name, index, indexed, searchable, required, final, WireType.LENGTH_DELIMITED, unique, minValue, maxValue
), IsSerializableFixedBytesEncodable<DO, IsPropertyContext> {
    override val byteSize = dataModel.byteSize

    override fun calculateStorageByteLength(value: DO) = this.byteSize

    override fun writeStorageBytes(value: DO, writer: (byte: Byte) -> Unit) = value._bytes.writeBytes(writer)

    override fun readStorageBytes(length: Int, reader: () -> Byte)
            = this.dataModel.readFromBytes(reader)

    override fun calculateTransportByteLength(value: DO) = this.dataModel.byteSize

    override fun asString(value: DO) = value.toBase64()

    override fun fromString(string: String) = this.dataModel.fromString(string)

    override fun getRef(parentRefFactory: () -> IsPropertyReference<*, *>?) =
            CanHaveComplexChildReference(
                    this,
                    parentRefFactory()?.let {
                        it as CanHaveComplexChildReference<*, *, *>
                    }
            )

    override fun getEmbeddedByName(name: String): IsPropertyDefinition<*>? = dataModel.getDefinition(name)

    override fun getEmbeddedByIndex(index: Int): IsPropertyDefinition<out Any>? = dataModel.getDefinition(index)

    override fun validate(previousValue: DO?, newValue: DO?, parentRefFactory: () -> IsPropertyReference<*, *>?) {
        super.validate(previousValue, newValue, parentRefFactory)
        if (newValue != null) {
            this.dataModel.validate(
                    parentRefFactory = { this.getRef(parentRefFactory) },
                    dataObject = newValue
            )
        }
    }

    /** Writes a value to Json
     * @param value: value to write
     * @param writer: to write json to
     */
    override fun writeJsonValue(value: DO, writer: JsonWriter, context: IsPropertyContext?) = dataModel.writeJson(value, writer, context)

    override fun readJson(reader: JsonReader, context: IsPropertyContext?): DO = dataModel.readJsonToObject(reader, context)
}