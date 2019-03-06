package maryk.core.processors.datastore

import maryk.core.extensions.bytes.initIntByVar

/** Find byte index by walking [indexable] until [partIndex] match */
fun findByteIndexByPartIndex(partIndex: Int, indexable: ByteArray): Int {
    var currentIndex = 0
    for (it in 0 until partIndex) {
        currentIndex += initIntByVar { indexable[currentIndex] } + 1
    }
    return currentIndex + 1
}

/** Find byte index and size by walking [indexable] until [partIndex] match */
fun findByteIndexAndSizeByPartIndex(partIndex: Int, indexable: ByteArray): Pair<Int, Int> {
    var currentIndex = 0
    var lastSize = initIntByVar { indexable[0] }
    for (it in 0 until partIndex) {
        currentIndex += lastSize + 1
        lastSize = initIntByVar { indexable[currentIndex] }
    }
    return Pair(currentIndex + 1, lastSize)
}
