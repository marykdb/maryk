package maryk
import maryk.core.models.RootDataModel
import maryk.core.objects.ValueMap
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.StringDefinition

data class SimpleMarykObject(
    val value: String = "haha"
) {
    object Properties: PropertyDefinitions<SimpleMarykObject>() {
        val value = add(
            index = 0, name = "value",
            definition = StringDefinition(
                default = "haha",
                regEx = "ha.*"
            ),
            getter = SimpleMarykObject::value
        )
    }

    companion object: RootDataModel<SimpleMarykObject, Properties>(
        name = "SimpleMarykObject",
        properties = Properties
    ) {
        override fun invoke(map: ValueMap<SimpleMarykObject>) = SimpleMarykObject(
            value = map(0)
        )
    }
}
