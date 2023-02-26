package maryk.test.models

import maryk.core.properties.RootModel
import maryk.core.properties.definitions.ReferenceDefinition
import maryk.core.properties.definitions.list

object ReferencesModel : RootModel<ReferencesModel>() {
    val references by list(
        index = 1u,
        valueDefinition = ReferenceDefinition(
            dataModel = { SimpleMarykModel }
        )
    )
}
