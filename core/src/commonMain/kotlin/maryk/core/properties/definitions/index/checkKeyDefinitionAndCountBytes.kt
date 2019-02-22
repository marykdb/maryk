package maryk.core.properties.definitions.index

import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.references.ValueWithFixedBytesPropertyReference
import maryk.core.properties.references.ValueWithFlexBytesPropertyReference

/** Checks [keyDefinition] and calculates key size */
internal fun checkKeyDefinitionAndCountBytes(keyDefinition: IsIndexable): Int {
    return when(keyDefinition) {
        is Multiple -> {
            var keyCount = keyDefinition.references.size - 1 // Start with adding size of separators
            keyDefinition.references.forEach {
                keyCount += checkKeyDefinitionAndCountBytes(it)
            }
            keyCount
        }
        is ValueWithFixedBytesPropertyReference<*, *, *, *> -> {
            checkKeyDefinition(keyDefinition.name, keyDefinition.propertyDefinition.definition as IsValueDefinition<*, *>)
            keyDefinition.byteSize
        }
        is ValueWithFlexBytesPropertyReference<*, *, *, *> -> throw Exception("Definition should have a fixed amount of bytes for a key")
        is TypeId<*> -> {
            checkKeyDefinition(keyDefinition.reference.name, keyDefinition.reference.propertyDefinition.definition)
            keyDefinition.byteSize
        }
        is Reversed<*> -> {
            return checkKeyDefinitionAndCountBytes(keyDefinition.reference)
        }
        is UUIDKey -> UUIDKey.byteSize
        else -> throw Exception("Unknown key definition type: $keyDefinition")
    }
}

private fun checkKeyDefinition(name: String, it: IsPropertyDefinition<*>) {
    require(it.required) { "Definition of $name should be required for a key" }
    require(it.final) { "Definition of $name should be final for a key" }
}
