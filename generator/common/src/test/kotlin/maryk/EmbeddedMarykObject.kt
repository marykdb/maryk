package maryk
import maryk.core.models.DataModel
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.StringDefinition

data class EmbeddedMarykObject(
    val value: String = "haha"
) {
    object Properties: PropertyDefinitions<EmbeddedMarykObject>() {
        val value = add(
            index = 0, name = "value",
            definition = StringDefinition(
                regEx = "ha.*",
                default = "haha"
            ),
            getter = EmbeddedMarykObject::value
        )
    }

    companion object: DataModel<EmbeddedMarykObject, Properties>(
        name = "EmbeddedMarykObject",
        properties = Properties
    ) {
        override fun invoke(map: Map<Int, *>) = EmbeddedMarykObject(
            value = map(0)
        )
    }
}
