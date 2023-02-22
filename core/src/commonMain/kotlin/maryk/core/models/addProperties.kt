package maryk.core.models

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.properties.AbstractPropertyDefinitions
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.PropertyDefinitionsCollectionDefinition
import maryk.core.properties.PropertyDefinitionsCollectionDefinitionWrapper

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
