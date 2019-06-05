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
sealed class AggregationType(
    index: UInt,
    override val name: String,
    dataModel: IsObjectDataModel<out IsAggregationRequest, *>,
    alternativeNames: Set<String>? = null
) : IndexedEnumImpl<AggregationType>(index, alternativeNames),
    MapType,
    IsCoreEnum,
    MultiTypeEnum<IsAggregationRequest> {

    @Suppress("UNCHECKED_CAST")
    override val definition = EmbeddedObjectDefinition(
        dataModel = { dataModel as AbstractObjectDataModel<IsAggregationRequest, ObjectPropertyDefinitions<IsAggregationRequest>, RequestContext, RequestContext> }
    )

    object ValueCountType : AggregationType(1u, "ValueCount", ValueCount)
    object SumType : AggregationType(2u, "Sum", Sum)
    object AverageType : AggregationType(3u, "Average", Average)
    object MinType : AggregationType(4u, "Min", Min)
    object MaxType : AggregationType(5u, "Max", Max)
    object StatsType : AggregationType(6u, "Stats", Stats)

    object EnumValuesType : AggregationType(50u, "EnumValues", EnumValues)
    object TypesType : AggregationType(51u, "Types", Types)
    object DateHistogramType : AggregationType(52u, "DateHistogram", DateHistogram)

    companion object : MultiTypeEnumDefinition<AggregationType>(
        "AggregationType",
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
