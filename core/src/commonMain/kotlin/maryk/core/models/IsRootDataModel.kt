package maryk.core.models

import maryk.core.exceptions.DefNotFoundException
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.IsPropertyDefinitions
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.definitions.index.Reversed
import maryk.core.properties.definitions.index.TypeId
import maryk.core.properties.definitions.index.UUIDKey
import maryk.core.properties.graph.IsPropRefGraphNode
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.references.ValueWithFixedBytesPropertyReference
import maryk.core.properties.references.ValueWithFlexBytesPropertyReference
import maryk.core.properties.types.Key

interface IsRootValuesDataModel<P: PropertyDefinitions> : IsRootDataModel<P>, IsValuesDataModel<P>

interface IsRootDataModel<P: IsPropertyDefinitions> : IsNamedDataModel<P> {
    val keyDefinition: IsIndexable
    val indices: List<IsIndexable>?

    val keyByteSize: Int
    val keyIndices: IntArray

    /** Get Key by [base64] bytes as string representation */
    fun key(base64: String): Key<*>

    /** Get Key by byte [reader] */
    fun key(reader: () -> Byte): Key<*>

    /** Get Key by [bytes] array */
    fun key(bytes: ByteArray): Key<*>

    /**
     * Create Property reference graph with list of graphables that are generated with [runner] on Properties
     * The graphables are sorted after generation so the RootPropRefGraph can be processed quicker.
     */
    fun graph(
        runner: P.() -> List<IsPropRefGraphNode<P>>
    ) = RootPropRefGraph(runner(this.properties).sortedBy { it.index })

    /** Get PropertyReference by [referenceName] */
    fun getPropertyReferenceByName(referenceName: String, context: IsPropertyContext? = null) = try {
        this.properties.getPropertyReferenceByName(referenceName, context)
    } catch (e: DefNotFoundException) {
        throw DefNotFoundException("Model ${this.name}: ${e.message}")
    }

    /** Get PropertyReference by bytes by reading the [reader] until [length] is reached. */
    fun getPropertyReferenceByBytes(length: Int, reader: () -> Byte, context: IsPropertyContext? = null) = try {
        this.properties.getPropertyReferenceByBytes(length, reader, context)
    } catch (e: DefNotFoundException) {
        throw DefNotFoundException("Model ${this.name}: ${e.message}")
    }

    /** Get PropertyReference by bytes by reading the storage bytes [reader] until [length] is reached. */
    fun getPropertyReferenceByStorageBytes(length: Int, reader: () -> Byte, context: IsPropertyContext? = null) = try {
        this.properties.getPropertyReferenceByStorageBytes(length, reader, context)
    } catch (e: DefNotFoundException) {
        throw DefNotFoundException("Model ${this.name}: ${e.message}")
    }
}

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
