package maryk

import maryk.core.models.ObjectDataModel
import maryk.core.objects.ObjectValues
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.StringDefinition

data class EmbeddedObject(
    val value: String = "haha"
) {
    object Properties: ObjectPropertyDefinitions<EmbeddedObject>() {
        val value = add(
            index = 0, name = "value",
            definition = StringDefinition(
                regEx = "ha.*",
                default = "haha"
            ),
            getter = EmbeddedObject::value
        )
    }

    companion object: ObjectDataModel<EmbeddedObject, Properties>(
        name = "EmbeddedObject",
        properties = Properties
    ) {
        override fun invoke(map: ObjectValues<EmbeddedObject, Properties>) = EmbeddedObject(
            value = map(0)
        )
    }
}
