package maryk.core.protobuf

import maryk.core.properties.IsPropertyContext

/**
 * Cache to store calculated lengths and contexts in for later ProtoBuf writing phase
 */
class WriteCache : WriteCacheReader, WriteCacheWriter {
    private val lengths = mutableListOf<ByteLengthContainer>()
    private var lengthCacheIndex = 0

    private val contexts = mutableListOf<IsPropertyContext>()
    private var contextCacheIndex = 0

    override fun addLengthToCache(item: ByteLengthContainer) {
        lengths += item
    }

    override fun <CX : IsPropertyContext> addContextToCache(item: CX) {
        contexts += item
    }

    override fun nextLengthFromCache() = lengths[lengthCacheIndex++].length
    override fun nextContextFromCache() = contexts[contextCacheIndex++]
}

interface WriteCacheWriter {
    fun addLengthToCache(item: ByteLengthContainer)
    fun <CX : IsPropertyContext> addContextToCache(item: CX)
}

interface WriteCacheReader {
    fun nextLengthFromCache(): Int
    fun nextContextFromCache(): IsPropertyContext
}
