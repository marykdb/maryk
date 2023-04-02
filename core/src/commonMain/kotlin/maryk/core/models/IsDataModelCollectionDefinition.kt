package maryk.core.models

import maryk.core.query.DefinitionsConversionContext

internal interface IsDataModelCollectionDefinition<DM : IsDataModel> {
    val capturer: Unit.(DefinitionsConversionContext?, DM) -> Unit
}
