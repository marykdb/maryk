package maryk.test.models

import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.ReferenceDefinition
import maryk.core.properties.definitions.list

object ReferencesModel : RootDataModel<ReferencesModel>() {
    val references by list(
        index = 1u,
        valueDefinition = ReferenceDefinition(
            dataModel = { SimpleMarykModel }
        )
    )
}
