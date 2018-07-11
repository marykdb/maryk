package maryk

import maryk.core.models.RootDataModel
import maryk.core.objects.Values
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.StringDefinition

data class SimpleMarykObject(
    val value: String = "haha"
) {
    object Properties: ObjectPropertyDefinitions<SimpleMarykObject>() {
        val value = add(
            index = 0, name = "value",
            definition = StringDefinition(
                regEx = "ha.*",
                default = "haha"
            ),
            getter = SimpleMarykObject::value
        )
    }

    companion object: RootDataModel<SimpleMarykObject, Properties>(
        name = "SimpleMarykObject",
        properties = Properties
    ) {
        override fun invoke(map: Values<SimpleMarykObject, Properties>) = SimpleMarykObject(
            value = map(0)
        )
    }
}
