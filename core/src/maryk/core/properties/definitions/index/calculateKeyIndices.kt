package maryk.core.properties.definitions.index

import maryk.core.exceptions.InvalidDefinitionException
import maryk.core.exceptions.TypeException
import maryk.core.properties.definitions.IsFixedStorageBytesEncodable

/** Add indexes for [keyDefinition] to int array. Also account for the 1 sized separator */
internal fun calculateKeyIndices(keyDefinition: IsIndexable): IntArray {
    var index = 0
    return when (keyDefinition) {
        is Multiple -> keyDefinition.references.map { def ->
            index.also {
                val propDef: IsFixedStorageBytesEncodable<*> = when (def) {
                    is IsFixedStorageBytesEncodable<*> -> def
                    is Normalize ->
                        if (def.reference is IsFixedStorageBytesEncodable<*>) {
                            def.reference
                        } else throw InvalidDefinitionException("Key cannot contain flex bytes encodables")
                    is Split,
                    is AnyOf -> throw InvalidDefinitionException("Key cannot contain split or anyOf indexables")
                    is Reversed<*> ->
                        if (def.reference is IsFixedStorageBytesEncodable<*>) {
                            def.reference
                        } else throw InvalidDefinitionException("Key cannot contain flex bytes encodables")
                    is GeoHash -> object : IsFixedStorageBytesEncodable<ByteArray> {
                        override val byteSize = def.byteSize
                        override fun calculateStorageByteLength(value: ByteArray) = byteSize
                        override fun readStorageBytes(length: Int, reader: () -> Byte) =
                            ByteArray(length) { reader() }
                        override fun writeStorageBytes(value: ByteArray, writer: (Byte) -> Unit) =
                            value.forEach(writer)
                    }
                    else -> throw TypeException("Unknown key encodable")
                }
                index += propDef.byteSize
            }
        }.toIntArray()
        else -> intArrayOf(0)
    }
}
