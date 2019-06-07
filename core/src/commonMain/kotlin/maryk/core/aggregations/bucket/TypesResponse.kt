package maryk.core.aggregations.bucket

import maryk.core.aggregations.AggregationResponseType.TypesType
import maryk.core.aggregations.IsAggregationResponse
import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.enum.MultiTypeEnum
import maryk.core.properties.enum.TypeEnum
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DefinedByReference
import maryk.core.values.SimpleObjectValues

/** The [buckets] found for all types at [reference] */
data class TypesResponse<T: TypeEnum<*>>(
    val reference: IsPropertyReference<out T, *, *>,
    val buckets: List<Bucket<T>> = emptyList()
) : IsAggregationResponse {
    override val aggregationType = TypesType

    companion object : SimpleQueryDataModel<TypesResponse<*>>(
        properties = object : ObjectPropertyDefinitions<TypesResponse<*>>() {
            init {
                DefinedByReference.addReference(this, TypesResponse<*>::reference, name = "of")
                add(2u, "buckets",
                    ListDefinition(
                        default = emptyList(),
                        valueDefinition = EmbeddedObjectDefinition(
                            dataModel = { Bucket }
                        )
                    ),
                    TypesResponse<*>::buckets
                )
            }
        }
    ) {
        override fun invoke(values: SimpleObjectValues<TypesResponse<*>>) =
            TypesResponse<MultiTypeEnum<Any>>(
                reference = values(1u),
                buckets = values(2u)
            )
    }
}
