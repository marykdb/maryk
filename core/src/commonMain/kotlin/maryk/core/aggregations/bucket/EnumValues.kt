package maryk.core.aggregations.bucket

import maryk.core.aggregations.AggregationRequestType.EnumValuesType
import maryk.core.aggregations.Aggregations
import maryk.core.aggregations.IsAggregationRequest
import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.embedObject
import maryk.core.properties.enum.IndexedEnumComparable
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.addReference
import maryk.core.values.SimpleObjectValues

/** Bucket all same enum values together for [reference] */
data class EnumValues<T: IndexedEnumComparable<T>>(
    override val reference: IsPropertyReference<out T, IsPropertyDefinition<T>, *>,
    val aggregations: Aggregations? = null
) : IsAggregationRequest<T, IsPropertyReference<out T, IsPropertyDefinition<T>, *>, EnumValuesResponse<T>> {
    override val aggregationType = EnumValuesType

    override fun createAggregator() =
        EnumValuesAggregator(this)

    @Suppress("unused")
    companion object : SimpleQueryDataModel<EnumValues<*>>(
        properties = object : ObjectPropertyDefinitions<EnumValues<*>>() {
            val of by addReference(EnumValues<*>::reference)
            val aggregations by embedObject(
                index = 2u,
                getter = EnumValues<*>::aggregations,
                dataModel = { Aggregations },
                alternativeNames = setOf("aggs")
            )
        }
    ) {
        override fun invoke(values: SimpleObjectValues<EnumValues<*>>) = EnumValues<IndexedEnumComparable<Any>>(
            reference = values(1u),
            aggregations = values(2u)
        )
    }
}
