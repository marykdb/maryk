package maryk.core.aggregations.metric

import maryk.core.properties.definitions.IsArithmeticDefinition
import maryk.core.properties.definitions.IsPropertyDefinition

internal fun <T : Comparable<T>> getArithmeticDefinition(
    definition: IsPropertyDefinition<T>,
    aggregation: String,
): IsArithmeticDefinition<T> {
    require(definition is IsArithmeticDefinition<*>) {
        "$aggregation requires an arithmetic property definition, got ${definition::class.simpleName}"
    }
    @Suppress("UNCHECKED_CAST")
    return definition as IsArithmeticDefinition<T>
}
