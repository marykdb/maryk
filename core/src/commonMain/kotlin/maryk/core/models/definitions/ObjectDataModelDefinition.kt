package maryk.core.models.definitions

import maryk.core.models.IsObjectDataModel

/**
 * ObjectDataModel for non contextual models. Contains a [name] to identify the model and [properties] which define how the
 * properties should be validated. It models the DataObjects of type [DO] which can be validated. And it contains a
 * reference to the propertyDefinitions of type [DM] which can be used for the references to the properties.
 */
interface IsObjectDataModelDefinition<DO : Any, DM : IsObjectDataModel<DO>> : IsNamedDataModelDefinition<DM>

abstract class ObjectDataModelDefinition<DO : Any, DM : IsObjectDataModel<DO>>(
    override val name: String,
    properties: DM
) : BaseDataModelDefinition<DM>(
    properties
), IsObjectDataModelDefinition<DO, DM>
