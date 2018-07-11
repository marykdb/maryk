package maryk

import maryk.core.models.RootObjectDataModel
import maryk.core.objects.ObjectValues
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

    companion object: RootObjectDataModel<SimpleMarykObject, Properties>(
        name = "SimpleMarykObject",
        properties = Properties
    ) {
        override fun invoke(map: ObjectValues<SimpleMarykObject, Properties>) = SimpleMarykObject(
            value = map(0)
        )
    }
}
