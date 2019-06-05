package maryk.core.aggregations.bucket

import maryk.core.aggregations.IsAggregationRequest
import maryk.core.properties.enum.IndexedEnumComparable
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.IsCoreEnum
import maryk.core.properties.enum.TypeEnum
import maryk.json.MapType

enum class DateHistogramUnit(
    override val index: UInt,
    override val alternativeNames: Set<String>? = null
) :
    IndexedEnumComparable<DateHistogramUnit>,
    MapType,
    IsCoreEnum,
    TypeEnum<IsAggregationRequest> {
    Millis(1u),
    Seconds(2u),
    Minutes(3u),
    Hours(4u),
    Days(5u),
    Weeks(6u),
    Months(7u),
    Quarters(8u),
    Years(9u),
    Decades(10u),
    Centuries(11u),
    Millennia(12u);

    companion object : IndexedEnumDefinition<DateHistogramUnit>(
        DateHistogramUnit::class, DateHistogramUnit::values
    )
}
