package maryk.test.models

import maryk.core.models.RootDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.ReferenceDefinition
import maryk.core.properties.types.Key

object ReferencesModel : RootDataModel<ReferencesModel, ReferencesModel.Properties>(
    properties = Properties
) {
    object Properties : PropertyDefinitions() {
        val references = add(
            index = 1u, name = "references",
            definition = ListDefinition(
                valueDefinition = ReferenceDefinition(
                    dataModel = { SimpleMarykModel }
                )
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
