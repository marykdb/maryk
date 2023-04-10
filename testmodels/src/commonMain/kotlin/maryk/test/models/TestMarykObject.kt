package maryk.test.models

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import maryk.core.models.ObjectDataModel
import maryk.core.properties.definitions.DateDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.TimeDefinition
import maryk.core.properties.definitions.boolean
import maryk.core.properties.definitions.dateTime
import maryk.core.properties.definitions.embedObject
import maryk.core.properties.definitions.enum
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.multiType
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.reference
import maryk.core.properties.definitions.set
import maryk.core.properties.definitions.string
import maryk.core.properties.definitions.valueObject
import maryk.core.properties.types.Key
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.Float64
import maryk.core.properties.types.numeric.SInt32
import maryk.core.properties.types.numeric.UInt32
import maryk.core.values.ObjectValues

data class TestMarykObject(
    val string: String = "haha",
    val int: Int,
    val uint: UInt,
    val double: Double,
    val dateTime: LocalDateTime,
    val bool: Boolean? = null,
    val enum: Option = Option.V1,
    val list: List<Int>? = null,
    val set: Set<LocalDate>? = null,
    val map: Map<LocalTime, String>? = null,
    val valueObject: TestValueObject? = null,
    val embeddedObject: EmbeddedMarykObject? = null,
    val multi: TypedValue<SimpleMarykTypeEnumWithObject<*>, *>? = null,
    val reference: Key<TestMarykModel>? = null,
    val listOfString: List<String>? = null
) {
    companion object : ObjectDataModel<TestMarykObject, Companion>(TestMarykObject::class) {
        val string by string(
            index = 1u,
            getter = TestMarykObject::string,
            default = "haha",
            regEx = "ha.*"
        )

        val int by number(
            index = 2u,
            getter = TestMarykObject::int,
            type = SInt32,
            maxValue = 6
        )

        val uint by number(
            index = 3u,
            getter = TestMarykObject::uint,
            type = UInt32,
            final = true
        )

        val double by number(
            index = 4u,
            getter = TestMarykObject::double,
            type = Float64
        )

        val dateTime by dateTime(
            index = 5u,
            getter = TestMarykObject::dateTime
        )

        val bool by boolean(
            index = 6u,
            getter = TestMarykObject::bool,
            final = true
        )

        val enum by enum(
            index = 7u,
            getter = TestMarykObject::enum,
            enum = Option,
            default = Option.V1,
            final = true
        )

        val list by list(
            index = 8u,
            getter = TestMarykObject::list,
            valueDefinition = NumberDefinition(
                type = SInt32
            ),
            required = false
        )

        val set by set(
            index = 9u,
            getter = TestMarykObject::set,
            valueDefinition = DateDefinition(),
            required = false
        )

        val map by map(
            index = 10u,
            getter = TestMarykObject::map,
            keyDefinition = TimeDefinition(),
            valueDefinition = StringDefinition(),
            required = false
        )

        val valueObject by valueObject(
            index = 11u,
            getter = TestMarykObject::valueObject,
            dataModel = TestValueObject,
            required = false
        )

        val embeddedObject by embedObject(
            index = 12u,
            getter = TestMarykObject::embeddedObject,
            dataModel = { EmbeddedMarykObject },
            required = false
        )

        val multi by multiType(
            index = 13u,
            required = false,
            typeEnum = SimpleMarykTypeEnumWithObject,
            getter = TestMarykObject::multi
        )

        val reference by reference(
            index = 14u,
            getter = TestMarykObject::reference,
            required = false,
            dataModel = { TestMarykModel }
        )

        val listOfString by list(
            index = 15u,
            getter = TestMarykObject::listOfString,
            valueDefinition = StringDefinition(),
            required = false
        )

        override fun invoke(values: ObjectValues<TestMarykObject, Companion>) = TestMarykObject(
            string = values(string.index),
            int = values(int.index),
            uint = values(uint.index),
            double = values(double.index),
            dateTime = values(dateTime.index),
            bool = values(bool.index),
            enum = values(enum.index),
            list = values(list.index),
            set = values(set.index),
            map = values(map.index),
            valueObject = values(valueObject.index),
            embeddedObject = values(embeddedObject.index),
            multi = values(multi.index),
            reference = values(reference.index),
            listOfString = values(listOfString.index)
        )
    }
}
