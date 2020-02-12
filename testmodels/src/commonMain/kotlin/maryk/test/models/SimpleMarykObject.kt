package maryk.test.models

import maryk.core.models.ObjectDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.string
import maryk.core.values.ObjectValues

data class SimpleMarykObject(
    val value: String = "haha"
) {
    object Properties : ObjectPropertyDefinitions<SimpleMarykObject>() {
        val value by string(
            index = 1u,
            getter = SimpleMarykObject::value,
            default = "haha",
            regEx = "ha.*"
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
