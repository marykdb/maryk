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
import maryk.core.models.AbstractObjectDataModel
import maryk.core.models.IsObjectDataModel
import maryk.core.properties.ObjectPropertyDefinitions
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
    dataModel: IsObjectDataModel<out IsAggregationRequest<*, *, *>, *>,
    alternativeNames: Set<String>? = null
) : IndexedEnumImpl<AggregationRequestType>(index, alternativeNames),
    MapType,
    IsCoreEnum,
    MultiTypeEnum<IsAggregationRequest<*, *, *>> {

    @Suppress("UNCHECKED_CAST")
    override val definition = EmbeddedObjectDefinition(
        dataModel = { dataModel as AbstractObjectDataModel<IsAggregationRequest<*, *, *>, ObjectPropertyDefinitions<IsAggregationRequest<*, *, *>>, RequestContext, RequestContext> }
    )

    object ValueCountType : AggregationRequestType(1u, "ValueCount", ValueCount.Model)
    object SumType : AggregationRequestType(2u, "Sum", Sum.Model)
    object AverageType : AggregationRequestType(3u, "Average", Average.Model)
    object MinType : AggregationRequestType(4u, "Min", Min.Model)
    object MaxType : AggregationRequestType(5u, "Max", Max.Model)
    object StatsType : AggregationRequestType(6u, "Stats", Stats.Model)

    object EnumValuesType : AggregationRequestType(50u, "EnumValues", EnumValues.Model)
    object TypesType : AggregationRequestType(51u, "Types", Types.Model)
    object DateHistogramType : AggregationRequestType(52u, "DateHistogram", DateHistogram.Model)

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
