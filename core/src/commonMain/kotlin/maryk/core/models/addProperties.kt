package maryk.core.models

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.properties.AbstractPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitionsCollectionDefinition
import maryk.core.properties.ObjectPropertyDefinitionsCollectionDefinitionWrapper
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.PropertyDefinitionsCollectionDefinition
import maryk.core.properties.PropertyDefinitionsCollectionDefinitionWrapper

internal fun <DM : IsDataModel<*>> addProperties(
    isRootModel: Boolean,
    definitions: AbstractPropertyDefinitions<DM>
): PropertyDefinitionsCollectionDefinitionWrapper<DM> {
    val wrapper = PropertyDefinitionsCollectionDefinitionWrapper<DM>(
        2u,
        "properties",
        PropertyDefinitionsCollectionDefinition(
            isRootModel,
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

internal fun <DM : IsObjectDataModel<*, *>> addProperties(definitions: AbstractPropertyDefinitions<DM>): ObjectPropertyDefinitionsCollectionDefinitionWrapper<DM> =
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
