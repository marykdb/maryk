package maryk.test.models

import maryk.core.models.RootDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.ReferenceDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.types.Key

object ReferencesModel : RootDataModel<ReferencesModel, ReferencesModel.Properties>(
    properties = Properties
) {
    object Properties : PropertyDefinitions() {
        val references by list(
            index = 1u,
            valueDefinition = ReferenceDefinition(
                dataModel = { SimpleMarykModel }
            )
        )
    }

    operator fun invoke(
        references: List<Key<SimpleMarykModel>> = listOf()
    ) = values {
        mapNonNulls(
            this.references with references
        )
    }
}
