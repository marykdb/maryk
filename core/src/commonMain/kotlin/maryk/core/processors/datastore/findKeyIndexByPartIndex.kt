package maryk.core.processors.datastore

import maryk.core.extensions.bytes.SIGN_BYTE
import maryk.core.extensions.bytes.initIntByVar
import maryk.lib.exceptions.ParseException

fun findByteIndexAndSizeByPartIndex(partIndex: Int, indexable: ByteArray, keySize: Int): Pair<Int, Int> {
    var indexForPart = 0
    var partCount = 0
    var index = indexable.lastIndex - keySize
    var lastSize = -1

    while (partIndex >= partCount) {
        if (indexable[index] == SIGN_BYTE) {
            throw ParseException("Expected no sign byte at end of var int")
        }

        // Continue until start of var int is found
        while (indexable[index - 1] == SIGN_BYTE) {
            index--
        }

        if (partCount > 0) {
            indexForPart += lastSize + 1
        }
        lastSize = initIntByVar { indexable[index] }

        index-- // lower index for search of next var int
        partCount++ // found another part index so higher count
    }

    return indexForPart to lastSize
}

fun findByteIndexByPartIndex(partIndex: Int, indexable: ByteArray, keySize: Int): Int {
    if (partIndex == 0) return 0
    return findByteIndexAndSizeByPartIndex(partIndex, indexable, keySize).first
}
