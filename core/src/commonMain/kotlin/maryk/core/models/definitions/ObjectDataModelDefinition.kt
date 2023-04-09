package maryk.core.models.definitions

/**
 * ObjectDataModel for non-contextual models. Contains a [name] to identify the model.
 */
interface IsObjectDataModelDefinition : IsNamedDataModelDefinition

abstract class ObjectDataModelDefinition(
    override val name: String,
) : BaseDataModelDefinition(), IsObjectDataModelDefinition
