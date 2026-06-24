package maryk.datastore.test

import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.string

object NullableUniqueModel : RootDataModel<NullableUniqueModel>() {
    val email by string(1u, required = false, unique = true)
}
