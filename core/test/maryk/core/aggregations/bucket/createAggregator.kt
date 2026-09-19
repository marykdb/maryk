package maryk.core.aggregations.bucket

import maryk.core.aggregations.ValueByPropertyReference
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.values.Values

fun createAggregator(valuesToAggregate: Values<*>): ValueByPropertyReference<*> {
        return {
            @Suppress("UNCHECKED_CAST")
            valuesToAggregate[it as IsPropertyReference<Any, IsPropertyDefinition<Any>, *>]
        }
    }
