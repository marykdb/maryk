@file:Suppress("unused")

package maryk.core.query.filters

import maryk.core.models.TypedObjectDataModel
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.RequestContext
import maryk.core.query.pairs.NamedRegexPair
import maryk.core.values.ObjectValues

/** Named search index in [nameRegexPairs] should match given search regex */
data class MatchesRegEx internal constructor(
    val nameRegexPairs: List<NamedRegexPair>
) : IsFilter {
    override val filterType = FilterType.MatchesRegEx

    constructor(vararg nameRegexPair: NamedRegexPair) : this(nameRegexPair.toList())

    override fun singleReference(predicate: (IsPropertyReference<*, *, *>) -> Boolean) = null

    companion object : TypedObjectDataModel<MatchesRegEx, Companion, RequestContext, RequestContext>() {
        val nameRegexPairs by list(
            index = 1u,
            getter = MatchesRegEx::nameRegexPairs,
            valueDefinition = EmbeddedObjectDefinition(
                dataModel = { NamedRegexPair }
            )
        )

        override fun invoke(values: ObjectValues<MatchesRegEx, Companion>) = MatchesRegEx(
            nameRegexPairs = values(1u)
        )
    }
}
