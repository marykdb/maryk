package maryk
import maryk.core.models.RootObjectDataModel
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
                default = "haha",
                regEx = "ha.*"
            ),
            getter = SimpleMarykObject::value
        )
    }

    companion object: RootObjectDataModel<SimpleMarykObject, Properties>(
        name = "SimpleMarykObject",
        properties = Properties
    ) {
        override fun invoke(map: Values<SimpleMarykObject, Properties>) = SimpleMarykObject(
            value = map(0)
        )
    }
}
