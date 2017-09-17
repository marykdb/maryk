package maryk.core.properties.definitions

import maryk.core.json.JsonGenerator
import maryk.core.json.JsonParser
import maryk.core.objects.DataModel
import maryk.core.properties.exceptions.PropertyValidationException
import maryk.core.properties.references.CanHaveComplexChildReference
import maryk.core.properties.references.CanHaveSimpleChildReference
import maryk.core.properties.references.PropertyReference

/**
 * Definition for submodel properties
 * @param dataModel definition of the DataObject
 * @param <D>  Type of model for this definition
 * @param <DO> DataModel which is contained within SubModel
 */
class SubModelDefinition<DO : Any, out D : DataModel<DO>>(
        name: String? = null,
        index: Short = -1,
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

    override fun writeJsonValue(generator: JsonGenerator, value: DO) = dataModel.toJson(generator, value)

    override fun parseFromJson(parser: JsonParser) = dataModel.fromJson(parser)
}