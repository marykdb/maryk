package maryk.core.aggregations.bucket

import maryk.core.aggregations.AggregationResponseType.TypesType
import maryk.core.aggregations.IsAggregationResponse
import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.enum.MultiTypeEnum
import maryk.core.properties.enum.TypeEnum
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.addReference
import maryk.core.values.SimpleObjectValues

/** The [buckets] found for all types at [reference] */
data class TypesResponse<T: TypeEnum<*>>(
    val reference: IsPropertyReference<out T, IsPropertyDefinition<T>, *>,
    val buckets: List<Bucket<T>> = emptyList()
) : IsAggregationResponse {
    override val aggregationType = TypesType

    @Suppress("unused")
    companion object : SimpleQueryDataModel<TypesResponse<*>>(
        properties = object : ObjectPropertyDefinitions<TypesResponse<*>>() {
            val of by addReference(TypesResponse<*>::reference)

            val buckets by list(
                index = 2u,
                getter = TypesResponse<*>::buckets,
                valueDefinition = EmbeddedObjectDefinition(
                    dataModel = { Bucket }
                ),
                default = emptyList()
            )
        }
    ) {
        override fun invoke(values: SimpleObjectValues<TypesResponse<*>>) =
            TypesResponse<MultiTypeEnum<Any>>(
                reference = values(1u),
                buckets = values(2u)
            )
    }
}
