package maryk.core.properties.definitions

import maryk.core.extensions.bytes.writeBytes
import maryk.core.json.JsonGenerator
import maryk.core.json.JsonParser
import maryk.core.objects.ValueDataModel
import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.exceptions.PropertyValidationException
import maryk.core.properties.references.CanHaveComplexChildReference
import maryk.core.properties.references.PropertyReference
import maryk.core.properties.types.ValueDataObject

/** Definition for value model properties
 * @param dataModel definition of the DataObject
 * @param <D>  Type of model for this definition
 * @param <DO> DataModel which is contained within SubModel
 */
class ValueModelDefinition<DO: ValueDataObject, out D : ValueDataModel<DO>>(
        name: String? = null,
        index: Short = -1,
        indexed: Boolean = false,
        searchable: Boolean = true,
        required: Boolean = false,
        final: Boolean = false,
        unique: Boolean = false,
        minValue: DO? = null,
        maxValue: DO? = null,
        val dataModel: D
) : AbstractSimpleDefinition<DO>(
        name, index, indexed, searchable, required, final, unique, minValue, maxValue
) {
    override fun convertToBytes(value: DO, reserver: (size: Int) -> Unit, writer: (byte: Byte) -> Unit) {
        reserver(value._bytes.size)
        value._bytes.writeBytes(writer)
    }

    override fun convertFromBytes(length: Int, reader: () -> Byte) = this.dataModel.createFromBytes(reader)

    override fun convertToString(value: DO) = value.toBase64()

    @Throws(ParseException::class)
    override fun convertFromString(string: String) = try {
        this.dataModel.createFromString(string)
    } catch (e: NumberFormatException) { throw ParseException(string, e) }

    override fun getRef(parentRefFactory: () -> PropertyReference<*, *>?) =
            CanHaveComplexChildReference<DO, ValueModelDefinition<DO, D>>(
                    this,
                    parentRefFactory()?.let {
                        it as CanHaveComplexChildReference<*, *>
                    },
                    dataModel = dataModel
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

    /** Writes a value to Json
     * @param value: value to write
     * @param generator: to write json to
     */
    override fun writeJsonValue(generator: JsonGenerator, value: DO) = dataModel.toJson(generator, value)

    @Throws(ParseException::class)
    override fun parseFromJson(parser: JsonParser): DO = dataModel.fromJsonToObject(parser)
}