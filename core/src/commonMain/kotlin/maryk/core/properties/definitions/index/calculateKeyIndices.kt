package maryk.core.properties.definitions.index

import maryk.core.exceptions.InvalidDefinitionException
import maryk.core.exceptions.TypeException
import maryk.core.properties.definitions.IsFixedBytesEncodable

/** Add indices for [keyDefinition] to int array. Also account for the 1 sized separator */
internal fun calculateKeyIndices(keyDefinition: IsIndexable): IntArray {
    var index = 0
    return when (keyDefinition) {
        is Multiple -> keyDefinition.references.map { def ->
            index.also {
                val propDef: IsFixedBytesEncodable<*> = when (def) {
                    is IsFixedBytesEncodable<*> -> def
                    is Reversed<*> ->
                        if (def.reference is IsFixedBytesEncodable<*>) {
                            def.reference
                        } else throw InvalidDefinitionException("Key cannot contain flex bytes encodables")
                    else -> throw TypeException("Unknown key encodable")
                }
                index += propDef.byteSize
            }
        }.toIntArray()
        else -> intArrayOf(0)
    }
}
