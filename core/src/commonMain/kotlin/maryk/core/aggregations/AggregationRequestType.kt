package maryk.core.aggregations

import maryk.core.aggregations.bucket.DateHistogram
import maryk.core.aggregations.bucket.EnumValues
import maryk.core.aggregations.bucket.Types
import maryk.core.aggregations.metric.Average
import maryk.core.aggregations.metric.Max
import maryk.core.aggregations.metric.Min
import maryk.core.aggregations.metric.Stats
import maryk.core.aggregations.metric.Sum
import maryk.core.aggregations.metric.ValueCount
import maryk.core.models.IsObjectDataModel
import maryk.core.properties.IsBaseModel
import maryk.core.properties.IsObjectPropertyDefinitions
import maryk.core.properties.IsSimpleBaseModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.SimpleQueryModel
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.enum.IndexedEnumImpl
import maryk.core.properties.enum.IsCoreEnum
import maryk.core.properties.enum.MultiTypeEnum
import maryk.core.properties.enum.MultiTypeEnumDefinition
import maryk.core.query.RequestContext
import maryk.json.MapType

/** Indexed type of Aggregation */
sealed class AggregationRequestType(
    index: UInt,
    override val name: String,
    dataModel: IsObjectPropertyDefinitions<out IsAggregationRequest<*, *, *>>,
    alternativeNames: Set<String>? = null
) : IndexedEnumImpl<AggregationRequestType>(index, alternativeNames),
    MapType,
    IsCoreEnum,
    MultiTypeEnum<IsAggregationRequest<*, *, *>> {

    @Suppress("UNCHECKED_CAST")
    override val definition = EmbeddedObjectDefinition(
        dataModel = { dataModel as SimpleQueryModel<IsAggregationRequest<*, *, *>> }
    )

    object ValueCountType : AggregationRequestType(1u, "ValueCount", ValueCount)
    object SumType : AggregationRequestType(2u, "Sum", Sum)
    object AverageType : AggregationRequestType(3u, "Average", Average)
    object MinType : AggregationRequestType(4u, "Min", Min)
    object MaxType : AggregationRequestType(5u, "Max", Max)
    object StatsType : AggregationRequestType(6u, "Stats", Stats)

    object EnumValuesType : AggregationRequestType(50u, "EnumValues", EnumValues)
    object TypesType : AggregationRequestType(51u, "Types", Types)
    object DateHistogramType : AggregationRequestType(52u, "DateHistogram", DateHistogram)

    companion object : MultiTypeEnumDefinition<AggregationRequestType>(
        AggregationRequestType::class,
        {
            arrayOf(
                ValueCountType,
                SumType,
                AverageType,
                MinType,
                MaxType,
                StatsType,
                EnumValuesType,
                TypesType,
                DateHistogramType
            )
        }
    )
}
