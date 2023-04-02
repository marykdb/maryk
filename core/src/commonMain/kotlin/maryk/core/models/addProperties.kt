package maryk.core.models

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.definitions.IsDataModelDefinition
import maryk.core.models.definitions.IsObjectDataModelDefinition

internal fun <DM : IsDataModelDefinition<*>> addProperties(
    isRootModel: Boolean,
    definitions: AbstractDataModel<DM>
): DataModelCollectionDefinitionWrapper<DM> {
    val wrapper = DataModelCollectionDefinitionWrapper<DM>(
        2u,
        "properties",
        DataModelCollectionDefinition(
            isRootModel,
            capturer = { context, propDefs ->
                context?.apply {
                    this.propertyDefinitions = propDefs
                } ?: throw ContextNotFoundException()
            }
        ),
        getter = { it.properties as ValuesDataModel }
    )

    definitions.addSingle(wrapper)
    return wrapper
}

internal fun <DM : IsObjectDataModelDefinition<*, *>> addProperties(definitions: AbstractDataModel<DM>): ObjectDataModelCollectionDefinitionWrapper<DM> =
    ObjectDataModelCollectionDefinitionWrapper<DM>(
        2u,
        "properties",
        ObjectDataModelCollectionDefinition(
            capturer = { context, propDefs ->
                context?.apply {
                    this.propertyDefinitions = propDefs
                } ?: throw ContextNotFoundException()
            }
        ),
        getter = {
            @Suppress("UNCHECKED_CAST")
            it.properties as IsObjectDataModel<in Any>
        }
    ).also(definitions::addSingle)
