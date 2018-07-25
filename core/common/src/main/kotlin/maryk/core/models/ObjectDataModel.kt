package maryk.core.models

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.properties.AbstractPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitionsCollectionDefinition
import maryk.core.properties.ObjectPropertyDefinitionsCollectionDefinitionWrapper

/**
 * ObjectDataModel for non contextual models. Contains a [name] to identify the model and [properties] which define how the
 * properties should be validated. It models the DataObjects of type [DO] which can be validated. And it contains a
 * reference to the propertyDefinitions of type [P] which can be used for the references to the properties.
 */
abstract class ObjectDataModel<DO: Any, P: ObjectPropertyDefinitions<DO>>(
    override val name: String,
    properties: P
) : SimpleObjectDataModel<DO, P>(
    properties
), IsNamedDataModel<P> {
    companion object {
        internal fun <DM: IsDataModel<*>> addProperties(definitions: AbstractPropertyDefinitions<DM>): ObjectPropertyDefinitionsCollectionDefinitionWrapper<DM> {
            val wrapper = ObjectPropertyDefinitionsCollectionDefinitionWrapper<DM>(
                2,
                "properties",
                ObjectPropertyDefinitionsCollectionDefinition(
                    capturer = { context, propDefs ->
                        context?.apply {
                            this.propertyDefinitions = propDefs
                        } ?: ContextNotFoundException()
                    }
                )
            ) {
                @Suppress("UNCHECKED_CAST")
                it.properties as ObjectPropertyDefinitions<Any>
            }

            definitions.addSingle(wrapper)
            return wrapper
        }
    }
}
