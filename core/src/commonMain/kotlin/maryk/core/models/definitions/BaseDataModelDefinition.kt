package maryk.core.models.definitions

import maryk.core.models.IsTypedDataModel

/**
 * A Data Model for converting and validating DataObjects. The [properties] contain all the property definitions for
 * this Model.
 */
abstract class BaseDataModelDefinition<DM : IsTypedDataModel<*>> internal constructor(
    final override val properties: DM
) : IsDataModelDefinition<DM>
