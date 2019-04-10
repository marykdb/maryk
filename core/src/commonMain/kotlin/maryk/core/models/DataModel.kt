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
    properties: P
) : SimpleDataModel<DM, P>(
    properties
), MarykPrimitive {
    override val primitiveType = PrimitiveType.Model

    override val name: String get() = this::class.simpleName ?: throw DefNotFoundException("Class $this has no name")

    private object Properties :
        ObjectPropertyDefinitions<DataModel<*, *>>(),
        IsDataModelPropertyDefinitions<DataModel<*, *>, PropertyDefinitionsCollectionDefinitionWrapper<DataModel<*, *>>> {
        override val name = IsNamedDataModel.addName(this, DataModel<*, *>::name)
        override val properties = DataModel.addProperties(this)
    }

    internal object Model : DefinitionDataModel<DataModel<*, *>>(
        properties = Properties
    ) {
        override fun invoke(values: SimpleObjectValues<DataModel<*, *>>) =
            object : DataModel<IsValuesDataModel<PropertyDefinitions>, PropertyDefinitions>(
                properties = values(2)
            ) {
                override val name: String = values(1)
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
                2,
                "properties",
                PropertyDefinitionsCollectionDefinition(
                    capturer = { context, propDefs ->
                        context?.apply {
                            this.propertyDefinitions = propDefs
                        } ?: ContextNotFoundException()
                    }
                )
            ) {
                @Suppress("UNCHECKED_CAST")
                it.properties as PropertyDefinitions
            }

            definitions.addSingle(wrapper)
            return wrapper
        }
    }
}
