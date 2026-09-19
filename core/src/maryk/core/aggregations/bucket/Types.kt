package maryk.core.aggregations.bucket

import maryk.core.aggregations.AggregationRequestType.TypesType
import maryk.core.aggregations.Aggregations
import maryk.core.aggregations.IsAggregationRequest
import maryk.core.models.SimpleQueryModel
import maryk.core.properties.definitions.embedObject
import maryk.core.properties.enum.TypeEnum
import maryk.core.properties.references.TypeReference
import maryk.core.query.addReference
import maryk.core.values.SimpleObjectValues

/** Bucket all types together for [reference] */
data class Types<T: TypeEnum<*>>(
    override val reference: TypeReference<T, *, *>,
    val aggregations: Aggregations? = null
) : IsAggregationRequest<T, TypeReference<T, *, *>, TypesResponse<T>> {
    override val aggregationType = TypesType

    override fun createAggregator() =
        TypesAggregator(this)

    companion object : SimpleQueryModel<Types<*>>() {
        val of by addReference(Types<*>::reference)

        val aggregations by embedObject(
            index = 2u,
            getter = Types<*>::aggregations,
            dataModel = { Aggregations },
            alternativeNames = setOf("aggs")
        )

        override fun invoke(values: SimpleObjectValues<Types<*>>) = Types<TypeEnum<*>>(
            reference = values(of.index),
            aggregations = values(aggregations.index)
        )
    }
}
