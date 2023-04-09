package maryk.core.models.definitions

import maryk.core.definitions.MarykPrimitiveDescriptor
import maryk.core.definitions.PrimitiveType
import maryk.core.exceptions.SerializationException
import maryk.core.models.DefinitionModel
import maryk.core.models.IsObjectDataModel
import maryk.core.models.serializers.ObjectDataModelSerializer
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.string
import maryk.core.properties.types.numeric.UInt32
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.values.ObjectValues
import maryk.json.IsJsonLikeWriter

/**
 * DataModel definition. Contains a [name] to identify the model.
 */
data class DataModelDefinition(
    override val name: String,
    override val reservedIndices: List<UInt>? = null,
    override val reservedNames: List<String>? = null,
) : BaseDataModelDefinition(), MarykPrimitiveDescriptor,
    IsValuesDataModelDefinition {
    override val primitiveType = PrimitiveType.Model

    object Model : DefinitionModel<DataModelDefinition>() {
        val name by string(1u, DataModelDefinition::name)
        val reservedIndices by list(
            index = 2u,
            getter = DataModelDefinition::reservedIndices,
            valueDefinition = NumberDefinition(
                type = UInt32,
                minValue = 1u
            )
        )
        val reservedNames by list(
            index = 3u,
            getter = DataModelDefinition::reservedNames,
            valueDefinition = StringDefinition()
        )

        override fun invoke(values: ObjectValues<DataModelDefinition, IsObjectDataModel<DataModelDefinition>>) =
            DataModelDefinition(
                name = values(1u),
                reservedIndices = values(2u),
                reservedNames = values(3u)
            )

        override val Serializer = object: ObjectDataModelSerializer<DataModelDefinition, IsObjectDataModel<DataModelDefinition>, ContainsDefinitionsContext, ContainsDefinitionsContext>(this) {
            override fun writeJson(
                values: ObjectValues<DataModelDefinition, IsObjectDataModel<DataModelDefinition>>,
                writer: IsJsonLikeWriter,
                context: ContainsDefinitionsContext?
            ) {
                throw SerializationException("Cannot write definitions from Values")
            }
        }
    }
}
