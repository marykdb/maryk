package maryk.core.properties

import maryk.core.protobuf.ByteSizeContainer

/** Collects bytes into a byteArray and enables to read them afterwards*/
class ByteCollectorWithSizeCacher : ByteCollector() {
    private val cache = mutableListOf<ByteSizeContainer>()
    private var cacheIndex = 0

    fun addToCache(item: ByteSizeContainer) {
        cache += item
    }

    fun nextSizeFromCache() = cache[cacheIndex++].size

    override fun reset() {
        super.reset()
        cache.clear()
        cacheIndex = 0
    }
}