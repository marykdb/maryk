package maryk.test.models

import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.definitions.index.Normalize
import maryk.core.properties.definitions.string
import maryk.test.models.CaseInsensitivePerson.firstName
import maryk.test.models.CaseInsensitivePerson.surname

object CaseInsensitivePerson : RootDataModel<CaseInsensitivePerson>(
    indexes = { listOf(
        Multiple(Normalize(surname.ref()), firstName.ref()),
    ) },
) {
    val firstName by string(
        index = 1u,
        minSize = 1u,
        maxSize = 100u
    )
    val surname by string(
        index = 2u,
        minSize = 1u,
        maxSize = 100u
    )
}
