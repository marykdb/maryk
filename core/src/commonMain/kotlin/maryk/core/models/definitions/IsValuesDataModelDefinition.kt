package maryk.core.models.definitions

import maryk.core.models.IsValuesDataModel

interface IsValuesDataModelDefinition<DM : IsValuesDataModel> : IsNamedDataModelDefinition<DM> {
    val reservedIndices: List<UInt>?
    val reservedNames: List<String>?
}
