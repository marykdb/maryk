package maryk.core.models

import maryk.core.properties.IsObjectPropertyDefinitions

/**
 * ObjectDataModel for non contextual models. Contains a [name] to identify the model and [properties] which define how the
 * properties should be validated. It models the DataObjects of type [DO] which can be validated. And it contains a
 * reference to the propertyDefinitions of type [DM] which can be used for the references to the properties.
 */
interface IsObjectDataModel<DO : Any, DM : IsObjectPropertyDefinitions<DO>> : IsNamedDataModel<DM>

abstract class ObjectDataModel<DO : Any, DM : IsObjectPropertyDefinitions<DO>>(
    override val name: String,
    properties: DM
) : BaseDataModel<DM>(
    properties
), IsObjectDataModel<DO, DM>
