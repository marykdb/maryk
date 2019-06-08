package maryk.core.aggregations.bucket

import maryk.core.aggregations.bucket.DateUnit.Centuries
import maryk.core.aggregations.bucket.DateUnit.Decades
import maryk.core.aggregations.bucket.DateUnit.Hours
import maryk.core.aggregations.bucket.DateUnit.Millennia
import maryk.core.aggregations.bucket.DateUnit.Millis
import maryk.core.aggregations.bucket.DateUnit.Minutes
import maryk.core.aggregations.bucket.DateUnit.Months
import maryk.core.aggregations.bucket.DateUnit.Quarters
import maryk.core.aggregations.bucket.DateUnit.Seconds
import maryk.core.aggregations.bucket.DateUnit.Years
import maryk.core.exceptions.TypeException
import maryk.core.properties.enum.IndexedEnumComparable
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.IsCoreEnum
import maryk.json.MapType
import maryk.lib.exceptions.ParseException
import maryk.lib.time.Date
import maryk.lib.time.DateTime
import maryk.lib.time.IsTemporal
import maryk.lib.time.Time

enum class DateUnit(
    override val index: UInt,
    override val alternativeNames: Set<String>? = null
) :
    IndexedEnumComparable<DateUnit>,
    MapType,
    IsCoreEnum {
    Millis(1u),
    Seconds(2u),
    Minutes(3u),
    Hours(4u),
    Days(5u),
//    Weeks(6u), Not yet possible without a calendar system
    Months(7u),
    Quarters(8u),
    Years(9u),
    Decades(10u),
    Centuries(11u),
    Millennia(12u);

    companion object : IndexedEnumDefinition<DateUnit>(
        DateUnit::class, DateUnit::values
    )
}

/** Round Temporal to the [dateUnit] */
@Suppress("UNCHECKED_CAST")
fun <T:IsTemporal<*>> T.roundToDateUnit(dateUnit: DateUnit): T = when (this) {
    is Date -> this.roundToDateUnit(dateUnit) as T
    is Time -> this.roundToDateUnit(dateUnit) as T
    is DateTime -> this.roundToDateUnit(dateUnit) as T
    else -> throw TypeException("Unknown type for IsTemporal")
}

/** Round Time to the [dateUnit] */
fun Time.roundToDateUnit(dateUnit: DateUnit) = when (dateUnit) {
    Millis -> this
    Seconds -> Time(hour, minute, second)
    Minutes -> Time(hour, minute)
    Hours -> Time(hour, 0)
    else -> Time.MIDNIGHT
}

/** Round DateTime to the [dateUnit] */
fun Date.roundToDateUnit(dateUnit: DateUnit) = when (dateUnit) {
    // Weeks -> Wait for a calendar system
    Months -> Date(year, month, 1)
    Quarters -> {
        val newMonth: Byte = when (month) {
            1.toByte(), 2.toByte(), 3.toByte() -> 1.toByte()
            4.toByte(), 5.toByte(), 6.toByte() -> 4.toByte()
            7.toByte(), 8.toByte(), 9.toByte() -> 7.toByte()
            10.toByte(), 11.toByte(), 12.toByte() -> 10.toByte()
            else -> throw ParseException("Unknown month")
        }
        Date(year, newMonth, 1)
    }
    Years -> Date(year, 1, 1)
    Decades -> Date(year - (year % 10), 1, 1)
    Centuries -> Date(year - (year % 100), 1, 1)
    Millennia -> Date(year - (year % 1000), 1, 1)
    else -> this // Else unit is lower and does not need rounding
}

/** Round DateTime to the [dateUnit] */
fun DateTime.roundToDateUnit(dateUnit: DateUnit) = when (dateUnit) {
        Millis -> this
        else -> DateTime(
            date.roundToDateUnit(dateUnit),
            time.roundToDateUnit(dateUnit)
        )
    }
