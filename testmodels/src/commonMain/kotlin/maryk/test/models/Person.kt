package maryk.test.models

import maryk.core.properties.RootModel
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.definitions.string
import maryk.test.models.Person.firstName
import maryk.test.models.Person.surname

object Person : RootModel<Person>(
    indices = { listOf(
        Multiple(firstName.ref(), surname.ref())
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
