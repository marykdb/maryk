package maryk.core.models.definitions

import maryk.core.definitions.MarykPrimitive
import maryk.core.definitions.PrimitiveType
import maryk.core.exceptions.DefNotFoundException
import maryk.core.exceptions.SerializationException
import maryk.core.models.addProperties
import maryk.core.models.serializers.ObjectDataModelSerializer
import maryk.core.models.serializers.readDataModelJson
import maryk.core.models.serializers.writeDataModelJson
import maryk.core.properties.DefinitionModel
import maryk.core.properties.IsDataModelPropertyDefinitions
import maryk.core.properties.IsValuesPropertyDefinitions
import maryk.core.properties.MutableModel
import maryk.core.properties.MutablePropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.PropertyDefinitionsCollectionDefinitionWrapper
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.string
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.types.numeric.UInt32
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.values.MutableValueItems
import maryk.core.values.ObjectValues
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter

/**
 * DataModel for non contextual models. Contains a [name] to identify the model and [properties] which define how the
 * properties should be validated. It models the DataObjects which can be validated. And it contains a
 * reference to the propertyDefinitions of type [DM] which can be used for the references to the properties.
 */
open class DataModelDefinition<DM : IsValuesPropertyDefinitions>(
    override val reservedIndices: List<UInt>? = null,
    override val reservedNames: List<String>? = null,
    properties: DM,
    override val name: String = properties::class.simpleName ?: throw DefNotFoundException("Class $properties has no name")
) : BaseDataModelDefinition<DM>(properties), MarykPrimitive, IsValuesDataModel<DM> {
    override val primitiveType = PrimitiveType.Model

    @Suppress("unused")
    internal object Model :
        DefinitionModel<DataModelDefinition<*>>(),
        IsDataModelPropertyDefinitions<DataModelDefinition<*>, PropertyDefinitionsCollectionDefinitionWrapper<DataModelDefinition<*>>> {
        override val name by string(1u, DataModelDefinition<*>::name)
        override val properties = addProperties(false, this)
        val reservedIndices by list(
            index = 3u,
            getter = DataModelDefinition<*>::reservedIndices,
            valueDefinition = NumberDefinition(
                type = UInt32,
                minValue = 1u
            )
        )
        val reservedNames by list(
            index = 4u,
            getter = DataModelDefinition<*>::reservedNames,
            valueDefinition = StringDefinition()
        )

        override fun invoke(values: ObjectValues<DataModelDefinition<*>, ObjectPropertyDefinitions<DataModelDefinition<*>>>) =
            DataModelDefinition(
                name = values(1u),
                properties = values(2u),
                reservedIndices = values(3u),
                reservedNames = values(4u)
            ).apply {
                @Suppress("UNCHECKED_CAST")
                (properties as MutablePropertyDefinitions<*, IsValuesPropertyDefinitions>)._model = this
            }

        override val Serializer = object: ObjectDataModelSerializer<DataModelDefinition<*>, ObjectPropertyDefinitions<DataModelDefinition<*>>, ContainsDefinitionsContext, ContainsDefinitionsContext>(this) {
            override fun writeJson(
                values: ObjectValues<DataModelDefinition<*>, ObjectPropertyDefinitions<DataModelDefinition<*>>>,
                writer: IsJsonLikeWriter,
                context: ContainsDefinitionsContext?
            ) {
                throw SerializationException("Cannot write definitions from Values")
            }

            override fun writeObjectAsJson(
                obj: DataModelDefinition<*>,
                writer: IsJsonLikeWriter,
                context: ContainsDefinitionsContext?,
                skip: List<IsDefinitionWrapper<*, *, *, DataModelDefinition<*>>>?
            ) {
                this.writeDataModelJson(writer, context, obj, Model)
            }

            override fun walkJsonToRead(
                reader: IsJsonLikeReader,
                values: MutableValueItems,
                context: ContainsDefinitionsContext?
            ) {
                readDataModelJson(context, reader, values, Model, ::MutableModel)
            }
        }
    }
}
