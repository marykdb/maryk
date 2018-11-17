package maryk.core.models

import maryk.core.definitions.MarykPrimitive
import maryk.core.definitions.PrimitiveType
import maryk.core.exceptions.ContextNotFoundException
import maryk.core.values.SimpleObjectValues
import maryk.core.properties.AbstractPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.PropertyDefinitionsCollectionDefinition
import maryk.core.properties.PropertyDefinitionsCollectionDefinitionWrapper

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

    internal object Model : DefinitionDataModel<DataModel<*, *>>(
        properties = object : ObjectPropertyDefinitions<DataModel<*, *>>() {
            init {
                IsNamedDataModel.addName(this) {
                    it.name
                }
                DataModel.addProperties(this)
            }
        }
    ) {
        override fun invoke(map: SimpleObjectValues<DataModel<*, *>>) =
            object : DataModel<IsValuesDataModel<PropertyDefinitions>, PropertyDefinitions>(
                name = map(1),
                properties = map(2)
            ) {}
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
