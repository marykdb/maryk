package maryk.core.properties.definitions.index

import maryk.core.exceptions.InvalidDefinitionException
import maryk.core.exceptions.TypeException
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.references.IsFixedBytesPropertyReference
import maryk.core.properties.references.ValueWithFixedBytesPropertyReference
import maryk.core.properties.references.ValueWithFlexBytesPropertyReference

/** Checks [keyDefinition] and calculates key size */
internal fun checkKeyDefinitionAndCountBytes(keyDefinition: IsIndexable): Int {
    return when (keyDefinition) {
        is Multiple -> {
            var keyCount = 0
            keyDefinition.references.forEach {
                keyCount += checkKeyDefinitionAndCountBytes(it)
            }
            keyCount
        }
        is ValueWithFixedBytesPropertyReference<*, *, *, *> -> {
            checkKeyDefinition(
                keyDefinition.name,
                keyDefinition.propertyDefinition.definition as IsValueDefinition<*, *>
            )
            keyDefinition.byteSize
        }
        is IsFixedBytesPropertyReference<*> -> keyDefinition.byteSize
        is ValueWithFlexBytesPropertyReference<*, *, *, *> ->
            throw InvalidDefinitionException("Definition should have a fixed amount of bytes for a key")
        is Reversed<*> ->
            checkKeyDefinitionAndCountBytes(keyDefinition.reference)
        is UUIDKey -> UUIDKey.byteSize
        else -> throw TypeException("Unknown key IsIndexable type: $keyDefinition")
    }
}

private fun checkKeyDefinition(name: String, it: IsPropertyDefinition<*>) {
    require(it.required) { "Definition of $name should be required for a key" }
    require(it.final) { "Definition of $name should be final for a key" }
}
