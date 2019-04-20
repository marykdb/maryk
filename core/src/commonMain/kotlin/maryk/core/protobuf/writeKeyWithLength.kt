package maryk.core.protobuf

import maryk.core.extensions.bytes.writeVarBytes

/**
 * Write ProtoBuf key with length if necessary for [wireType]
 * Pass [index] and [cacheGetter] for values to write to [writer]
 */
fun writeKeyWithLength(
    wireType: WireType,
    index: UInt,
    writer: (byte: Byte) -> Unit,
    cacheGetter: WriteCacheReader
) {
    ProtoBuf.writeKey(index, wireType, writer)
    if (wireType == WireType.LENGTH_DELIMITED) {
        cacheGetter.nextLengthFromCache().writeVarBytes(writer)
    }
}
