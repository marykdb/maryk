package maryk.core.properties.types

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlinx.datetime.number
import maryk.core.exceptions.TypeException
import maryk.core.properties.definitions.TimeDefinition
import maryk.core.properties.enum.IndexedEnumComparable
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.IsCoreEnum
import maryk.core.properties.types.DateUnit.Centuries
import maryk.core.properties.types.DateUnit.Decades
import maryk.core.properties.types.DateUnit.Hours
import maryk.core.properties.types.DateUnit.Micros
import maryk.core.properties.types.DateUnit.Millennia
import maryk.core.properties.types.DateUnit.Millis
import maryk.core.properties.types.DateUnit.Minutes
import maryk.core.properties.types.DateUnit.Months
import maryk.core.properties.types.DateUnit.Nanos
import maryk.core.properties.types.DateUnit.Quarters
import maryk.core.properties.types.DateUnit.Seconds
import maryk.core.properties.types.DateUnit.Years
import maryk.json.MapType

enum class DateUnit(
    override val index: UInt,
    override val alternativeNames: Set<String>? = null
) :
    IndexedEnumComparable<DateUnit>,
    MapType,
    IsCoreEnum {
    Nanos(1u),
    Micros(2u),
    Millis(3u),
    Seconds(4u),
    Minutes(5u),
    Hours(6u),
    Days(7u),
//    Weeks(8u), Not yet possible without a calendar system
    Months(9u),
    Quarters(10u),
    Years(11u),
    Decades(12u),
    Centuries(13u),
    Millennia(14u);

    companion object : IndexedEnumDefinition<DateUnit>(
        DateUnit::class, { entries }
    )
}

/** Round Temporal to the [dateUnit] */
@Suppress("UNCHECKED_CAST")
fun <T:Comparable<*>> T.roundToDateUnit(dateUnit: DateUnit): T = when (this) {
    is LocalDate -> this.roundToDateUnit(dateUnit) as T
    is LocalTime -> this.roundToDateUnit(dateUnit) as T
    is LocalDateTime -> this.roundToDateUnit(dateUnit) as T
    else -> throw TypeException("Unknown type for IsTemporal")
}

/** Round Time to the [dateUnit] */
fun LocalTime.roundToDateUnit(dateUnit: DateUnit) = when (dateUnit) {
    Nanos -> this
    Micros -> LocalTime(hour, minute, second, nanosecond / 1000 * 1000)
    Millis -> LocalTime(hour, minute, second, nanosecond / 1000000 * 1000000)
    Seconds -> LocalTime(hour, minute, second)
    Minutes -> LocalTime(hour, minute)
    Hours -> LocalTime(hour, 0)
    else -> TimeDefinition.MIN
}

/** Round DateTime to the [dateUnit] */
fun LocalDate.roundToDateUnit(dateUnit: DateUnit) = when (dateUnit) {
    // Weeks -> Wait for a calendar system
    Months -> LocalDate(year, month, 1)
    Quarters -> {
        // calculate to the month start, 1/4/7/12, when calculating quarters
        val newMonth = ((month.number - 1) / 3) * 3 + 1
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
        Nanos -> it.atTime(hour, minute, second, nanosecond)
        Micros -> it.atTime(hour, minute, second, nanosecond / 1000 * 1000)
        Millis -> it.atTime(hour, minute, second, nanosecond / 1000000 * 1000000)
        Seconds -> it.atTime(hour, minute, second)
        Minutes -> it.atTime(hour, minute)
        Hours -> it.atTime(hour, 0)
        else -> it.atTime(0, 0)
    }
}
