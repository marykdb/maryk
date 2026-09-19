package maryk.core.processors.datastore

import maryk.core.extensions.bytes.initIntByVar
import maryk.lib.exceptions.ParseException

internal fun readQualifierLength(
    qualifierReader: (Int) -> Byte,
    qualifierLength: Int,
    offsetGetter: () -> Int,
    offsetSetter: (Int) -> Unit,
    subject: String
): Int {
    val length = initIntByVar {
        val offset = offsetGetter()
        if (offset >= qualifierLength) {
            throw ParseException("Missing $subject length in storage qualifier")
        }
        offsetSetter(offset + 1)
        qualifierReader(offset)
    }

    if (length < 0) {
        throw ParseException("Negative $subject length in storage qualifier")
    }

    val offset = offsetGetter()
    if (length > qualifierLength - offset) {
        throw ParseException("$subject length exceeds storage qualifier")
    }
    return length
}
