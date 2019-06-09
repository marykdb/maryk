package maryk.core.aggregations

import maryk.core.properties.references.IsPropertyReference

typealias ValueByPropertyReference<T> = (IsPropertyReference<T, *, *>) -> T?
