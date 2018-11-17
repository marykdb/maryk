package maryk.test.models

import maryk.core.models.ValueDataModel
import maryk.core.values.ObjectValues
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.DateDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.types.ValueDataObject
import maryk.core.properties.types.numeric.SInt32
import maryk.lib.time.Date

data class ValueMarykObject(
    val int: Int = 5,
    val date: Date = Date(2000, 5, 12)
): ValueDataObject(toBytes(int, date)) {
    object Properties: ObjectPropertyDefinitions<ValueMarykObject>() {
        val int = add(
            index = 1, name = "int",
            definition = NumberDefinition(
                type = SInt32,
                default = 5
            ),
            getter = ValueMarykObject::int
        )
        val date = add(
            index = 2, name = "date",
            definition = DateDefinition(
                default = Date(2000, 5, 12)
            ),
            getter = ValueMarykObject::date
        )
    }

    companion object: ValueDataModel<ValueMarykObject, Properties>(
        name = "ValueMarykObject",
        properties = Properties
    ) {
        override fun invoke(map: ObjectValues<ValueMarykObject, Properties>) = ValueMarykObject(
            int = map(1),
            date = map(2)
        )
    }
}
