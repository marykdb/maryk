package maryk.core.properties.definitions.contextual

import maryk.core.objects.DataModel

/** Reference to a DataModel */
data class DataModelReference<DM: DataModel<*, *>>(
    val name: String,
    val get: () -> DM
)