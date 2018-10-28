package maryk
import maryk.core.models.ObjectDataModel
import maryk.core.objects.ObjectValues
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.StringDefinition

data class SimpleMarykObject(
    val value: String = "haha"
) {
    object Properties: ObjectPropertyDefinitions<SimpleMarykObject>() {
        val value = add(
            index = 1, name = "value",
            definition = StringDefinition(
                default = "haha",
                regEx = "ha.*"
            ),
            getter = SimpleMarykObject::value
        )
    }

    companion object: ObjectDataModel<SimpleMarykObject, Properties>(
        name = "SimpleMarykObject",
        properties = Properties
    ) {
        override fun invoke(map: ObjectValues<SimpleMarykObject, Properties>) = SimpleMarykObject(
            value = map(1)
        )
    }
}
