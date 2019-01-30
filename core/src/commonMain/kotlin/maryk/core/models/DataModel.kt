package maryk.core.models

import maryk.core.definitions.MarykPrimitive
import maryk.core.definitions.PrimitiveType
import maryk.core.exceptions.ContextNotFoundException
import maryk.core.properties.AbstractPropertyDefinitions
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
abstract class DataModel<DM: IsValuesDataModel<P>, P: PropertyDefinitions>(
    override val name: String,
    properties: P
) : SimpleDataModel<DM, P>(
    properties
), MarykPrimitive {
    override val primitiveType = PrimitiveType.Model

    private object Properties : ObjectPropertyDefinitions<DataModel<*, *>>() {
        val name = IsNamedDataModel.addName(this, DataModel<*, *>::name)

        init {
            DataModel.addProperties(this)
        }
    }

    internal object Model : DefinitionDataModel<DataModel<*, *>>(
        properties = Properties
    ) {
        override fun invoke(values: SimpleObjectValues<DataModel<*, *>>) =
            object : DataModel<IsValuesDataModel<PropertyDefinitions>, PropertyDefinitions>(
                name = values(1),
                properties = values(2)
            ) {}

        override fun writeJson(
            values: ObjectValues<DataModel<*, *>, ObjectPropertyDefinitions<DataModel<*, *>>>,
            writer: IsJsonLikeWriter,
            context: ContainsDefinitionsContext?
        ) {
            throw Exception("Cannot write definitions from Values")
        }

        override fun writeJson(obj: DataModel<*, *>, writer: IsJsonLikeWriter, context: ContainsDefinitionsContext?) {
            // Only skip when DefinitionsContext was set
            val toSkip = when {
                context != null && context.currentDefinitionName == obj.name -> listOf(Properties.name)
                else -> null
            }
            super.writeJson(obj, writer, context, toSkip)
        }

        override fun readJsonToMap(reader: IsJsonLikeReader, context: ContainsDefinitionsContext?): MutableValueItems {
            val map = super.readJsonToMap(reader, context)

            context?.currentDefinitionName?.let { name ->
                if (name.isNotBlank() && map.contains(Properties.name.index)) {
                    throw Exception("Name $name was already defined by map")
                }

                map[Properties.name.index] = name
            }

            return map
        }
    }

    companion object {
        internal fun <DM: IsDataModel<*>> addProperties(definitions: AbstractPropertyDefinitions<DM>): PropertyDefinitionsCollectionDefinitionWrapper<DM> {
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
