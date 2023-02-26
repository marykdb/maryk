package maryk.core.models

import maryk.core.definitions.MarykPrimitive
import maryk.core.definitions.PrimitiveType
import maryk.core.exceptions.DefNotFoundException
import maryk.core.exceptions.SerializationException
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
import maryk.core.properties.types.numeric.UInt32
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.values.MutableValueItems
import maryk.core.values.ObjectValues
import maryk.core.values.SimpleObjectValues
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter

/**
 * DataModel for non contextual models. Contains a [name] to identify the model and [properties] which define how the
 * properties should be validated. It models the DataObjects which can be validated. And it contains a
 * reference to the propertyDefinitions of type [P] which can be used for the references to the properties.
 */
open class DataModel<P : IsValuesPropertyDefinitions>(
    reservedIndices: List<UInt>? = null,
    reservedNames: List<String>? = null,
    properties: P,
    override val name: String = properties::class.simpleName ?: throw DefNotFoundException("Class $properties has no name")
) : SimpleDataModel<DataModel<P>, P>(
    reservedIndices,
    reservedNames,
    properties
), MarykPrimitive {
    override val primitiveType = PrimitiveType.Model

    @Suppress("unused")
    private object Properties :
        ObjectPropertyDefinitions<DataModel<*>>(),
        IsDataModelPropertyDefinitions<DataModel<*>, PropertyDefinitionsCollectionDefinitionWrapper<DataModel<*>>> {
        override val name by string(1u, DataModel<*>::name)
        override val properties = addProperties(false, this)
        val reservedIndices by list(
            index = 3u,
            getter = DataModel<*>::reservedIndices,
            valueDefinition = NumberDefinition(
                type = UInt32,
                minValue = 1u
            )
        )
        val reservedNames by list(
            index = 4u,
            getter = DataModel<*>::reservedNames,
            valueDefinition = StringDefinition()
        )
    }

    internal object Model : DefinitionDataModel<DataModel<*>>(
        properties = Properties
    ) {
        override fun invoke(values: SimpleObjectValues<DataModel<*>>) =
            DataModel(
                name = values(1u),
                properties = values(2u),
                reservedIndices = values(3u),
                reservedNames = values(4u)
            ).apply {
                (properties as MutablePropertyDefinitions)._model = this
            }

        override fun writeJson(
            values: ObjectValues<DataModel<*>, ObjectPropertyDefinitions<DataModel<*>>>,
            writer: IsJsonLikeWriter,
            context: ContainsDefinitionsContext?
        ) {
            throw SerializationException("Cannot write definitions from Values")
        }

        override fun writeJson(obj: DataModel<*>, writer: IsJsonLikeWriter, context: ContainsDefinitionsContext?) {
            this.writeDataModelJson(writer, context, obj, Properties)
        }

        override fun walkJsonToRead(
            reader: IsJsonLikeReader,
            values: MutableValueItems,
            context: ContainsDefinitionsContext?
        ) {
            readDataModelJson(context, reader, values, Properties, ::MutableModel)
        }
    }
}
