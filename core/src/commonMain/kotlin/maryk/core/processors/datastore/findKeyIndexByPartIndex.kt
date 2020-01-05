package maryk.core.processors.datastore

import maryk.core.extensions.bytes.SIGN_BYTE
import maryk.core.extensions.bytes.initIntByVar
import maryk.lib.exceptions.ParseException

/** Find byte index by walking [indexable] until [partIndex] match */
fun findByteIndexByPartIndex(partIndex: Int, indexable: ByteArray, keySize: Int): Int {
    if (partIndex == 0) {
        return 0
    }

    var indexForPart = 0
    var partCount = 0
    var index = indexable.lastIndex - keySize

    while (partIndex > partCount) {
        if (indexable[index] == SIGN_BYTE) {
            throw ParseException("Expected no sign byte at the end of var int")
        }

        // Continue until start of var int is found
        while (indexable[index - 1] == SIGN_BYTE) {
            index--
        }

        // Read part size and add it +1 for separator
        indexForPart += initIntByVar { indexable[index] } + 1

        index-- // lower index for search of next var int
        partCount++ // found another part index so higher count
    }
    return indexForPart
}

/** Find byte index and size by walking [indexable] until [partIndex] match */
fun findByteIndexAndSizeByPartIndex(partIndex: Int, indexable: ByteArray, keySize: Int): Pair<Int, Int> {
    var indexForPart = 0
    var partCount = 0
    var index = indexable.lastIndex - keySize
    var lastSize: Int = -1

    while (partIndex >= partCount) {
        if (indexable[index] == SIGN_BYTE) {
            throw ParseException("Expected no sign byte at end of var int")
        }

        // Continue until start of var int is found
        while (indexable[index - 1] == SIGN_BYTE) {
            index--
        }

        indexForPart += lastSize + 1
        // Read part size and add it +1 for separator
        lastSize = initIntByVar { indexable[index] }

        index-- // lower index for search of next var int
        partCount++ // found another part index so higher count
    }
    return Pair(indexForPart, lastSize)
}
