package maryk.datastore.foundationdb.processors.helpers

import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.datastore.shared.readStoredValue as decodeStoredValue

internal fun readStoredValue(definition: IsPropertyDefinition<out Any>?, bytes: ByteArray, offset: Int, length: Int): Any? =
    decodeStoredValue(definition, bytes, offset, length)
