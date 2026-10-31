package maryk.core.query.filters

import maryk.core.models.IsRootDataModel
import maryk.core.properties.definitions.index.namedSearchIndex
import maryk.core.properties.definitions.index.toPropertyReference
import maryk.core.properties.references.AnyPropertyReference

/** Find a filter dependency, including the property sources of named search indexes. */
fun IsRootDataModel.singleReferenceInFilter(
    filter: IsFilter?,
    predicate: (AnyPropertyReference) -> Boolean,
): AnyPropertyReference? {
    val names = when (filter) {
        is Matches -> filter.nameValuePairs.map { it.name }
        is MatchesPrefix -> filter.nameValuePairs.map { it.name }
        is MatchesRegEx -> filter.nameRegexPairs.map { it.name }
        is IsFilterList -> return filter.filters.firstNotNullOfOrNull { singleReferenceInFilter(it, predicate) }
        else -> return filter?.singleReference(predicate)
    }
    for (name in names) {
        for (source in namedSearchIndex(name)?.references.orEmpty()) {
            val reference = source.toPropertyReference()
            if (reference.unwrap().asReversed().any(predicate)) return reference
        }
    }
    return null
}
