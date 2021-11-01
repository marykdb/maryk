package maryk.core.aggregations.bucket

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.datetime.atTime
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
fun <T:Comparable<*>> T.roundToDateUnit(dateUnit: DateUnit): T = when (this) {
    is LocalDate -> this.roundToDateUnit(dateUnit) as T
    is Time -> this.roundToDateUnit(dateUnit) as T
    is LocalDateTime -> this.roundToDateUnit(dateUnit) as T
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
fun LocalDate.roundToDateUnit(dateUnit: DateUnit) = when (dateUnit) {
    // Weeks -> Wait for a calendar system
    Months -> LocalDate(year, month, 1)
    Quarters -> {
        val newMonth: Int = when (month) {
            Month.JANUARY, Month.FEBRUARY, Month.MARCH -> 1
            Month.APRIL, Month.MAY, Month.JUNE -> 4
            Month.JULY, Month.AUGUST, Month.SEPTEMBER -> 7
            Month.OCTOBER, Month.NOVEMBER, Month.DECEMBER -> 10
            else -> throw ParseException("Unknown month")
        }
        LocalDate(year, newMonth, 1)
    }
    Years -> LocalDate(year, 1, 1)
    Decades -> LocalDate(year - (year % 10), 1, 1)
    Centuries -> LocalDate(year - (year % 100), 1, 1)
    Millennia -> LocalDate(year - (year % 1000), 1, 1)
    else -> this // Else unit is lower and does not need rounding
}

/** Round DateTime to the [dateUnit] */
fun LocalDateTime.roundToDateUnit(dateUnit: DateUnit) = date.roundToDateUnit(dateUnit).let {
        when (dateUnit) {
            Millis -> it.atTime(hour, minute, second, nanosecond / 1000000 * 1000000)
            Seconds -> it.atTime(hour, minute, second)
            Minutes -> it.atTime(hour, minute)
            Hours -> it.atTime(hour, 0)
            else -> it.atTime(0, 0)
        }
    }
