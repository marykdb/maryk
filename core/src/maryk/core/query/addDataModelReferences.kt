package maryk.core.query

import maryk.core.models.IsRootDataModel
import maryk.core.properties.definitions.contextual.IsDataModelReference
import maryk.core.properties.definitions.contextual.LazyDataModelReference

fun DefinitionsConversionContext.addDataModelReferences(
    models: Iterable<IsRootDataModel>
) {
    models.forEach { dataModel ->
        val name = dataModel.Meta.name
        if (name !in dataModels) {
            dataModels[name] = LazyDataModelReference(name, null) {
                {
                    val reference = dataModels[name]
                    if (reference != null && reference !is LazyDataModelReference<*>) {
                        @Suppress("UNCHECKED_CAST")
                        (reference as IsDataModelReference<IsRootDataModel>).get()
                    } else {
                        dataModel
                    }
                }
            }
        }
    }
}
