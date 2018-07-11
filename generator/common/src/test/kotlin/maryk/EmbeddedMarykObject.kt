package maryk

import maryk.core.models.ObjectDataModel
import maryk.core.objects.Values
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.StringDefinition

data class EmbeddedMarykObject(
    val value: String = "haha"
) {
    object Properties: ObjectPropertyDefinitions<EmbeddedMarykObject>() {
        val value = add(
            index = 0, name = "value",
            definition = StringDefinition(
                regEx = "ha.*",
                default = "haha"
            ),
            getter = EmbeddedMarykObject::value
        )
    }

    companion object: ObjectDataModel<EmbeddedMarykObject, Properties>(
        name = "EmbeddedMarykObject",
        properties = Properties
    ) {
        override fun invoke(map: Values<EmbeddedMarykObject, Properties>) = EmbeddedMarykObject(
            value = map(0)
        )
    }
}
