package maryk.core.properties

import maryk.core.protobuf.ByteLengthContainer
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import kotlin.test.fail

class WriteCacheFailer : WriteCacheReader, WriteCacheWriter {
    override fun addLengthToCache(item: ByteLengthContainer) = fail("Should not call")

    override fun <CX : IsPropertyContext> addContextToCache(item: CX) = fail("Should not call")

    override fun nextLengthFromCache() = fail("Should not call")

    override fun nextContextFromCache() = fail("Should not call")
}