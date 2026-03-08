package maryk.test.models

import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.index.AnyOf
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.definitions.index.Normalize
import maryk.core.properties.definitions.index.SplitOn.Whitespace
import maryk.core.properties.definitions.index.normalize
import maryk.core.properties.definitions.index.split
import maryk.core.properties.definitions.string
import maryk.test.models.CaseInsensitivePerson.firstName
import maryk.test.models.CaseInsensitivePerson.surname

object CaseInsensitivePerson : RootDataModel<CaseInsensitivePerson>(
    indexes = { listOf(
        Multiple(Normalize(surname.ref()), firstName.ref()),
        AnyOf(
            "name",
            surname.ref(),
            firstName.ref(),
        ).normalize().split(Whitespace),
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
