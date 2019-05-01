package maryk.core.properties.references

import maryk.core.extensions.bytes.initUIntByVarWithExtraInfo

/** Decode Storage index from [reader] and then creates object with [objectCreator] */
fun <T : Any?> decodeStorageIndex(reader: () -> Byte, objectCreator: (UInt, ReferenceType) -> T): T {
    return initUIntByVarWithExtraInfo(reader) { index, byte ->
        objectCreator(index, referenceStorageTypeOf(byte))
    }
}
