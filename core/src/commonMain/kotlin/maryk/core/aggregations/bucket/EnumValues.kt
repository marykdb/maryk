package maryk.core.aggregations.bucket

import maryk.core.aggregations.AggregationType.EnumValuesType
import maryk.core.aggregations.Aggregations
import maryk.core.aggregations.IsAggregationRequest
import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DefinedByReference
import maryk.core.values.SimpleObjectValues

/** Bucket all same enum values together for [reference] */
data class EnumValues(
    val reference: IsPropertyReference<out IndexedEnum, *, *>,
    val aggregations: Aggregations? = null
) : IsAggregationRequest {
    override val aggregationType = EnumValuesType

    companion object : SimpleQueryDataModel<EnumValues>(
        properties = object : ObjectPropertyDefinitions<EnumValues>() {
            init {
                DefinedByReference.addReference(this, EnumValues::reference, name = "of")
                IsAggregationRequest.addAggregationsDefinition(this, EnumValues::aggregations)
            }
        }
    ) {
        override fun invoke(values: SimpleObjectValues<EnumValues>) = EnumValues(
            reference = values(1u),
            aggregations = values(2u)
        )
    }
}
