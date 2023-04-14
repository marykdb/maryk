package maryk.core.models

import maryk.core.query.DefinitionsConversionContext

/**
 * Interface for internal representation of properties of a DataModel
 */
internal interface IsDataModelPropertiesCollectionDefinition<DM : IsDataModel> {
    val capturer: Unit.(DefinitionsConversionContext?, DM) -> Unit
}
