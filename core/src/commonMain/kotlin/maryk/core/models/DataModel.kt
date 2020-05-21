package maryk.core.models

import maryk.core.definitions.MarykPrimitive
import maryk.core.definitions.PrimitiveType
import maryk.core.exceptions.ContextNotFoundException
import maryk.core.exceptions.DefNotFoundException
import maryk.core.exceptions.SerializationException
import maryk.core.properties.AbstractPropertyDefinitions
import maryk.core.properties.IsDataModelPropertyDefinitions
import maryk.core.properties.MutablePropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.PropertyDefinitionsCollectionDefinition
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
abstract class DataModel<DM : IsValuesDataModel<P>, P : PropertyDefinitions>(
    reservedIndices: List<UInt>? = null,
    reservedNames: List<String>? = null,
    properties: P
) : SimpleDataModel<DM, P>(
    reservedIndices,
    reservedNames,
    properties
), MarykPrimitive {
    override val primitiveType = PrimitiveType.Model

    override val name: String get() = this::class.simpleName ?: throw DefNotFoundException("Class $this has no name")

    @Suppress("unused")
    private object Properties :
        ObjectPropertyDefinitions<DataModel<*, *>>(),
        IsDataModelPropertyDefinitions<DataModel<*, *>, PropertyDefinitionsCollectionDefinitionWrapper<DataModel<*, *>>> {
        override val name by string(1u, DataModel<*, *>::name)
        override val properties = addProperties(this)
        val reservedIndices by list(
            index = 3u,
            getter = DataModel<*, *>::reservedIndices,
            valueDefinition = NumberDefinition(
                type = UInt32,
                minValue = 1u
            )
        )
        val reservedNames by list(
            index = 4u,
            getter = DataModel<*, *>::reservedNames,
            valueDefinition = StringDefinition()
        )
    }

    internal object Model : DefinitionDataModel<DataModel<*, *>>(
        properties = Properties
    ) {
        override fun invoke(values: SimpleObjectValues<DataModel<*, *>>) =
            object : DataModel<IsValuesDataModel<PropertyDefinitions>, PropertyDefinitions>(
                properties = values(2u),
                reservedIndices = values(3u),
                reservedNames = values(4u)
            ) {
                override val name: String = values(1u)
            }

        override fun writeJson(
            values: ObjectValues<DataModel<*, *>, ObjectPropertyDefinitions<DataModel<*, *>>>,
            writer: IsJsonLikeWriter,
            context: ContainsDefinitionsContext?
        ) {
            throw SerializationException("Cannot write definitions from Values")
        }

        override fun writeJson(obj: DataModel<*, *>, writer: IsJsonLikeWriter, context: ContainsDefinitionsContext?) {
            this.writeDataModelJson(writer, context, obj, Properties)
        }

        override fun walkJsonToRead(
            reader: IsJsonLikeReader,
            values: MutableValueItems,
            context: ContainsDefinitionsContext?
        ) {
            readDataModelJson(context, reader, values, Properties, ::MutablePropertyDefinitions)
        }
    }

    companion object {
        internal fun <DM : IsDataModel<*>> addProperties(definitions: AbstractPropertyDefinitions<DM>): PropertyDefinitionsCollectionDefinitionWrapper<DM> {
            val wrapper = PropertyDefinitionsCollectionDefinitionWrapper<DM>(
                2u,
                "properties",
                PropertyDefinitionsCollectionDefinition(
                    capturer = { context, propDefs ->
                        context?.apply {
                            this.propertyDefinitions = propDefs
                        } ?: throw ContextNotFoundException()
                    }
                ),
                getter = { it.properties as PropertyDefinitions }
            )

            definitions.addSingle(wrapper)
            return wrapper
        }
    }
}
