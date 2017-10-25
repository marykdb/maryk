package maryk.core.properties

import maryk.core.protobuf.ByteLengthContainer

/** Collects bytes into a byteArray and enables to read them afterwards*/
class ByteCollectorWithLengthCacher : ByteCollector() {
    private val cache = mutableListOf<ByteLengthContainer>()
    private var cacheIndex = 0

    fun addToCache(item: ByteLengthContainer) {
        cache += item
    }

    fun nextLengthFromCache() = cache[cacheIndex++].length

    override fun reset() {
        super.reset()
        cache.clear()
        cacheIndex = 0
    }
}