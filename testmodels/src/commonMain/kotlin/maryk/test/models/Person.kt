package maryk.test.models

import maryk.core.models.RootDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.definitions.string
import maryk.test.models.Person.Properties
import maryk.test.models.Person.Properties.firstName
import maryk.test.models.Person.Properties.surname

object Person: RootDataModel<Person, Properties>(
    indices = listOf(
        Multiple(firstName.ref(), surname.ref())
    ),
    properties = Properties
) {
    object Properties : PropertyDefinitions() {
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

    operator fun invoke(
        firstName: String,
        surname: String
    ) = this.values {
        mapNonNulls(
            this.firstName with firstName,
            this.surname with surname
        )
    }
}
