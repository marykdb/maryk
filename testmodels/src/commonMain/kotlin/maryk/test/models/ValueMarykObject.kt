package maryk.test.models

import maryk.core.models.ValueDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.date
import maryk.core.properties.definitions.number
import maryk.core.properties.types.ValueDataObject
import maryk.core.properties.types.numeric.SInt32
import maryk.core.values.ObjectValues
import maryk.lib.time.Date

data class ValueMarykObject(
    val int: Int = 5,
    val date: Date = Date(2000, 5, 12)
) : ValueDataObject(toBytes(int, date)) {
    object Properties : ObjectPropertyDefinitions<ValueMarykObject>() {
        val int by number(
            index = 1u,
            getter = ValueMarykObject::int,
            type = SInt32,
            default = 5
        )
        val date by date(
            index = 2u,
            getter = ValueMarykObject::date,
            default = Date(2000, 5, 12)
        )
    }

    companion object : ValueDataModel<ValueMarykObject, Properties>(
        name = "ValueMarykObject",
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<ValueMarykObject, Properties>) = ValueMarykObject(
            int = values(1u),
            date = values(2u)
        )
    }
}
