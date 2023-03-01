package maryk.test.models

import maryk.core.properties.ObjectModel
import maryk.core.properties.definitions.string
import maryk.core.values.ObjectValues

data class SimpleMarykObject(
    val value: String = "haha"
) {
    companion object : ObjectModel<SimpleMarykObject, Companion>(SimpleMarykObject::class) {
        val value by string(
            index = 1u,
            getter = SimpleMarykObject::value,
            default = "haha",
            regEx = "ha.*"
        )

        override fun invoke(values: ObjectValues<SimpleMarykObject, Companion>) = SimpleMarykObject(
            value = values(1u)
        )
    }
}
