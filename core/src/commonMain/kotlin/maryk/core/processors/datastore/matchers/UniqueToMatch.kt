package maryk.core.processors.datastore.matchers

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsComparableDefinition

/** Unique [value] to match against [reference] for [definition] in a scan */
class UniqueToMatch(
    val reference: ByteArray,
    val definition: IsComparableDefinition<Comparable<Any>, IsPropertyContext>,
    val value: Comparable<*>
)
