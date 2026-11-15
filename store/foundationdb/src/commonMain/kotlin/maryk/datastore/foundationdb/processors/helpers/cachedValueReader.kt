package maryk.datastore.foundationdb.processors.helpers

import maryk.core.properties.references.IsPropertyReferenceForCache
import maryk.core.properties.types.Key
import maryk.datastore.shared.Cache

/** Share one callback implementation across Get and Scan value decoding. */
internal fun Cache.cachedValueReader(
    dbIndex: UInt,
    key: Key<*>,
): (IsPropertyReferenceForCache<*, *>, ULong, () -> Any?) -> Any? = { reference, version, reader ->
    readValue(dbIndex, key, reference, version, reader)
}
