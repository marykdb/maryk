package maryk.core.models

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.properties.AbstractPropertyDefinitions
import maryk.core.properties.IsObjectPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitionsCollectionDefinition
import maryk.core.properties.ObjectPropertyDefinitionsCollectionDefinitionWrapper

/**
 * ObjectDataModel for non contextual models. Contains a [name] to identify the model and [properties] which define how the
 * properties should be validated. It models the DataObjects of type [DO] which can be validated. And it contains a
 * reference to the propertyDefinitions of type [P] which can be used for the references to the properties.
 */
abstract class ObjectDataModel<DO : Any, P : IsObjectPropertyDefinitions<DO>>(
    override val name: String,
    properties: P
) : SimpleObjectDataModel<DO, P>(
    properties
), IsNamedDataModel<P> {
    companion object {
        internal fun <DM : ObjectDataModel<*, *>> addProperties(definitions: AbstractPropertyDefinitions<DM>): ObjectPropertyDefinitionsCollectionDefinitionWrapper<DM> =
            ObjectPropertyDefinitionsCollectionDefinitionWrapper<DM>(
                2u,
                "properties",
                ObjectPropertyDefinitionsCollectionDefinition(
                    capturer = { context, propDefs ->
                        context?.apply {
                            this.propertyDefinitions = propDefs
                        } ?: throw ContextNotFoundException()
                    }
                ),
                getter = {
                    @Suppress("UNCHECKED_CAST")
                    it.properties as ObjectPropertyDefinitions<in Any>
                }
            ).also(definitions::addSingle)
    }
}
