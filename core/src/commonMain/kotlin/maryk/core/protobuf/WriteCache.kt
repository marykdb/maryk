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

/** Sizing-only calculations retain neither lengths nor contexts and cannot be used for writing. */
object LengthOnlyWriteCache : WriteCacheWriter {
    override fun addLengthToCache(item: ByteLengthContainer) = Unit
    override fun <CX : IsPropertyContext> addContextToCache(item: CX) = Unit
}

/** Reserve a parent's slot before sizing children, unless no write will follow. */
internal fun WriteCacheWriter.reserveLength(): ByteLengthContainer? =
    if (this === LengthOnlyWriteCache) null else ByteLengthContainer().also { addLengthToCache(it) }

interface WriteCacheWriter {
    fun addLengthToCache(item: ByteLengthContainer)
    fun <CX : IsPropertyContext> addContextToCache(item: CX)
}

interface WriteCacheReader {
    fun nextLengthFromCache(): Int
    fun nextContextFromCache(): IsPropertyContext
}
