package maryk.core.aggregations.metric

import maryk.core.aggregations.AggregationResponseType.ValueCountType
import maryk.core.aggregations.IsAggregationResponse
import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.numeric.UInt64
import maryk.core.query.DefinedByReference
import maryk.core.values.SimpleObjectValues

/** The response of the value count */
data class ValueCountResponse<T: Any>(
    val reference: IsPropertyReference<out T, *, *>,
    val value: ULong
) : IsAggregationResponse {
    override val aggregationType = ValueCountType

    companion object : SimpleQueryDataModel<ValueCountResponse<*>>(
        properties = object : ObjectPropertyDefinitions<ValueCountResponse<*>>() {
            init {
                DefinedByReference.addReference(this, ValueCountResponse<*>::reference, name = "of")
                add(2u, "value",
                    NumberDefinition(type = UInt64), ValueCountResponse<*>::value)
            }
        }
    ) {
        override fun invoke(values: SimpleObjectValues<ValueCountResponse<*>>) =
            ValueCountResponse<Any>(
                reference = values(1u),
                value = values(2u)
            )
    }
}
