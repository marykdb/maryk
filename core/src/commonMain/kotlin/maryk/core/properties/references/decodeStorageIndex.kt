package maryk.core.properties.references

import maryk.core.extensions.bytes.initIntByVar
import maryk.core.extensions.bytes.initIntByVarWithExtraInfo

/** Decode Storage index from [reader] and then creates object with [objectCreator] */
fun <T : Any?> decodeStorageIndex(reader: () -> Byte, objectCreator: (Int, CompleteReferenceType) -> T): T {
    var firstByte: Byte? = reader()

    return when (referenceStorageTypeOf(firstByte!!)) {
        ReferenceType.SPECIAL -> {
            objectCreator(initIntByVar(reader), completeReferenceTypeOf(firstByte))
        }
        else -> {
            val withFirstByteReader = {
                firstByte?.also { firstByte = null } ?: reader()
            }

            initIntByVarWithExtraInfo(withFirstByteReader) { index, byte ->
                objectCreator(index, completeReferenceTypeOf(byte))
            }
        }
    }
}
