package maryk.test.models

import maryk.core.models.ObjectDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.StringDefinition
import maryk.core.values.ObjectValues

data class SimpleMarykObject(
    val value: String = "haha"
) {
    object Properties : ObjectPropertyDefinitions<SimpleMarykObject>() {
        val value = add(
            index = 1u, name = "value",
            definition = StringDefinition(
                default = "haha",
                regEx = "ha.*"
            ),
            getter = SimpleMarykObject::value
        )
    }

    companion object : ObjectDataModel<SimpleMarykObject, Properties>(
        name = "SimpleMarykObject",
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<SimpleMarykObject, Properties>) = SimpleMarykObject(
            value = values(1u)
        )
    }
}
