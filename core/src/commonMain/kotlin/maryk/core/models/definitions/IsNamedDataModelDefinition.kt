package maryk.core.models.definitions

import maryk.core.models.IsDataModel

interface IsNamedDataModelDefinition<DM : IsDataModel> : IsDataModelDefinition<DM> {
    val name: String
}
