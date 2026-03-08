@file:Suppress("unused")

package maryk.core.query.filters

import maryk.core.models.TypedObjectDataModel
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.RequestContext
import maryk.core.query.pairs.NamedValuePair
import maryk.core.values.ObjectValues

/** Named search index in [nameValuePairs] should match given search value */
data class Matches internal constructor(
    val nameValuePairs: List<NamedValuePair>
) : IsFilter {
    override val filterType = FilterType.Matches

    constructor(vararg nameValuePair: NamedValuePair) : this(nameValuePair.toList())

    override fun singleReference(predicate: (IsPropertyReference<*, *, *>) -> Boolean) = null

    companion object : TypedObjectDataModel<Matches, Companion, RequestContext, RequestContext>() {
        val nameValuePairs by list(
            index = 1u,
            getter = Matches::nameValuePairs,
            valueDefinition = EmbeddedObjectDefinition(
                dataModel = { NamedValuePair }
            )
        )

        override fun invoke(values: ObjectValues<Matches, Companion>) = Matches(
            nameValuePairs = values(1u)
        )
    }
}
