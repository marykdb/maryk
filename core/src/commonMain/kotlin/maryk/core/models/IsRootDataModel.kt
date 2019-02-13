package maryk.core.models

import maryk.core.exceptions.DefNotFoundException
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.IsPropertyDefinitions
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.key.IsIndexable
import maryk.core.properties.definitions.key.Multiple
import maryk.core.properties.definitions.key.Reversed
import maryk.core.properties.definitions.key.TypeId
import maryk.core.properties.definitions.key.UUIDKey
import maryk.core.properties.graph.IsPropRefGraphNode
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.references.ValueWithFixedBytesPropertyReference
import maryk.core.properties.types.Key

interface IsRootValuesDataModel<P: PropertyDefinitions> : IsRootDataModel<P>, IsValuesDataModel<P>

interface IsRootDataModel<P: IsPropertyDefinitions> : IsNamedDataModel<P> {
    val keyDefinition: IsIndexable

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

internal fun checkKeyDefinition(keyDefinition: IsIndexable) {
    when(keyDefinition) {
        is Multiple ->
            keyDefinition.references.forEach { checkKeyDefinition(it) }
        is ValueWithFixedBytesPropertyReference<*, *, *, *> ->
            checkKeyDefinition(keyDefinition.propertyDefinition.name, keyDefinition.propertyDefinition.definition as IsValueDefinition<*, *>)
        is TypeId<*> ->
            checkKeyDefinition(keyDefinition.reference.propertyDefinition.name, keyDefinition.reference.propertyDefinition.definition)
        is Reversed<*> -> {
            val reference = keyDefinition.reference as ValueWithFixedBytesPropertyReference<*, *, *, *>
            checkKeyDefinition(reference.propertyDefinition.name, reference.propertyDefinition.definition)
        }
        is UUIDKey -> {} // Ignore
        else -> throw Exception("Unknown key definition type: $keyDefinition")
    }
}

private fun checkKeyDefinition(name: String, it: IsPropertyDefinition<*>) {
    require(it.required) { "Definition of $name should be required" }
    require(it.final) { "Definition of $name should be final" }
}
