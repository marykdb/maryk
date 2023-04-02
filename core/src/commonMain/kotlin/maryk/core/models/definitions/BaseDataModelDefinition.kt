package maryk.core.models.definitions

import maryk.core.properties.IsTypedPropertyDefinitions

/**
 * A Data Model for converting and validating DataObjects. The [properties] contain all the property definitions for
 * this Model.
 */
abstract class BaseDataModelDefinition<DM : IsTypedPropertyDefinitions<*>> internal constructor(
    final override val properties: DM
) : IsDataModelDefinition<DM>
