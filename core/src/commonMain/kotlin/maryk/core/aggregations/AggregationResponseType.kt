package maryk.core.aggregations

import maryk.core.aggregations.bucket.DateHistogramResponse
import maryk.core.aggregations.bucket.EnumValuesResponse
import maryk.core.aggregations.bucket.TypesResponse
import maryk.core.aggregations.metric.AverageResponse
import maryk.core.aggregations.metric.MaxResponse
import maryk.core.aggregations.metric.MinResponse
import maryk.core.aggregations.metric.StatsResponse
import maryk.core.aggregations.metric.SumResponse
import maryk.core.aggregations.metric.ValueCountResponse
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
sealed class AggregationResponseType(
    index: UInt,
    override val name: String,
    dataModel: IsObjectDataModel<out IsAggregationResponse, *>,
    alternativeNames: Set<String>? = null
) : IndexedEnumImpl<AggregationResponseType>(index, alternativeNames),
    MapType,
    IsCoreEnum,
    MultiTypeEnum<IsAggregationResponse> {

    @Suppress("UNCHECKED_CAST")
    override val definition = EmbeddedObjectDefinition(
        dataModel = { dataModel as AbstractObjectDataModel<IsAggregationResponse, ObjectPropertyDefinitions<IsAggregationResponse>, RequestContext, RequestContext> }
    )

    object ValueCountType : AggregationResponseType(1u, "ValueCount", ValueCountResponse.Model)
    object SumType : AggregationResponseType(2u, "Sum", SumResponse.Model)
    object AverageType : AggregationResponseType(3u, "Average", AverageResponse.Model)
    object MinType : AggregationResponseType(4u, "Min", MinResponse.Model)
    object MaxType : AggregationResponseType(5u, "Max", MaxResponse.Model)
    object StatsType : AggregationResponseType(6u, "Stats", StatsResponse.Model)

    object EnumValuesType : AggregationResponseType(50u, "EnumValues", EnumValuesResponse.Model)
    object TypesType : AggregationResponseType(51u, "Types", TypesResponse.Model)
    object DateHistogramType : AggregationResponseType(52u, "DateHistogram", DateHistogramResponse.Model)

    companion object : MultiTypeEnumDefinition<AggregationResponseType>(
        AggregationResponseType::class,
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
