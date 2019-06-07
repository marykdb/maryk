package maryk.core.aggregations.bucket

import maryk.core.aggregations.AggregationRequestType.TypesType
import maryk.core.aggregations.Aggregations
import maryk.core.aggregations.IsAggregationRequest
import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.references.TypeReference
import maryk.core.query.DefinedByReference
import maryk.core.values.SimpleObjectValues

/** Bucket all types together for [reference] */
data class Types(
    val reference: TypeReference<*, *, *>,
    val aggregations: Aggregations? = null
) : IsAggregationRequest {
    override val aggregationType = TypesType

    companion object : SimpleQueryDataModel<Types>(
        properties = object : ObjectPropertyDefinitions<Types>() {
            init {
                DefinedByReference.addReference(this, Types::reference, name = "of")
                IsAggregationRequest.addAggregationsDefinition(this, Types::aggregations)
            }
        }
    ) {
        override fun invoke(values: SimpleObjectValues<Types>) = Types(
            reference = values(1u),
            aggregations = values(2u)
        )
    }
}
