package maryk.core.protobuf

import maryk.core.extensions.bytes.calculateVarByteLength

/**
 * Calculate key and content length for ProtoBuf encoding
 * Pass [wireType], [index] and [contentLengthCalculator] to calculate length
 * Values will be cached in [cacher]
 */
internal fun calculateKeyAndContentLength(
    wireType: WireType,
    index: UInt,
    cacher: WriteCacheWriter,
    contentLengthCalculator: () -> Int
): Int {
    var totalByteLength = 0
    totalByteLength += ProtoBuf.calculateKeyLength(index)

    if (wireType == WireType.LENGTH_DELIMITED) {
        // Take care length container is first cached before value is calculated
        // Otherwise byte lengths contained by value could be cached before
        // This way order is maintained
        val container = ByteLengthContainer()
        cacher.addLengthToCache(container)

        // calculate field length
        contentLengthCalculator().let {
            container.length = it
            totalByteLength += it
            totalByteLength += it.calculateVarByteLength()
        }
    } else {
        // calculate field length
        totalByteLength += contentLengthCalculator()
    }
    return totalByteLength
}
