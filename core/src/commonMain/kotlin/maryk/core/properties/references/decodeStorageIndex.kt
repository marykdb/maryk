package maryk.core.properties.references

import maryk.core.extensions.bytes.initUIntByVar
import maryk.core.extensions.bytes.initUIntByVarWithExtraInfo

/** Decode Storage index from [reader] and then creates object with [objectCreator] */
fun <T : Any?> decodeStorageIndex(reader: () -> Byte, objectCreator: (UInt, CompleteReferenceType) -> T): T {
    var firstByte: Byte? = reader()

    return when (referenceStorageTypeOf(firstByte!!)) {
        ReferenceType.SPECIAL -> {
            objectCreator(initUIntByVar(reader), completeReferenceTypeOf(firstByte))
        }
        else -> {
            val withFirstByteReader = {
                firstByte?.also { firstByte = null } ?: reader()
            }

            initUIntByVarWithExtraInfo(withFirstByteReader) { index, byte ->
                objectCreator(index, completeReferenceTypeOf(byte))
            }
        }
    }
}
