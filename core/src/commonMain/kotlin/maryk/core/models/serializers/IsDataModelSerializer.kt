package maryk.core.models.serializers

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.IsPropertyDefinitions
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.core.values.IsValues

interface IsDataModelSerializer<V: IsValues<DM>, DM: IsPropertyDefinitions, CX: IsPropertyContext>: IsJsonSerializer<V, CX> {
    /**
     * Calculates the byte length for the DataObject contained in [values]
     * The [cacher] caches any values needed to write later.
     * Optionally pass a [context] to write more complex properties which depend on other properties
     */
    fun calculateProtoBufLength(values: V, cacher: WriteCacheWriter, context: CX? = null): Int

    /**
     * Write a ProtoBuf from a [values] with values to [writer] and get
     * possible cached values from [cacheGetter]
     * Optionally pass a [context] to write more complex properties which depend on other properties
     */
    fun writeProtoBuf(values: V, cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit, context: CX? = null)

    /**
     * Read ProtoBuf bytes from [reader] until [length] to a Map of values
     * Optionally pass a [context] to read more complex properties which depend on other properties
     */
    fun readProtoBuf(length: Int, reader: () -> Byte, context: CX? = null): V
}
