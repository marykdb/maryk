package maryk.core.protobuf

import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.protobuf.WireType.LENGTH_DELIMITED

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
    if (wireType == LENGTH_DELIMITED) {
        cacheGetter.nextLengthFromCache().writeVarBytes(writer)
    }
}
