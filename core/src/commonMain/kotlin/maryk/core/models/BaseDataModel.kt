package maryk.core.models

import maryk.core.definitions.MarykPrimitive
import maryk.core.exceptions.DefNotFoundException
import maryk.core.extensions.bytes.initUIntByVar
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.HasDefaultValueDefinition
import maryk.core.properties.definitions.IsListDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsUsableInMultiType
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.enum.TypeEnum
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.HasEmbeddedPropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.ListAnyItemReference
import maryk.core.properties.references.ListAnyValuePropertyReference
import maryk.core.properties.references.decodeStorageIndex
import maryk.lib.exceptions.ParseException

/**
 * The base class for all DataModels
 * Contains all property definitions and ways to retrieve them by plain or byte encoded name or index.
 */
abstract class BaseDataModel<DO : Any> : IsTypedDataModel<DO> {
    override fun iterator() = _allProperties.iterator()

    private val _allProperties: MutableList<IsDefinitionWrapper<Any, Any, IsPropertyContext, DO>> =
        mutableListOf()

    protected val indexToDefinition = mutableMapOf<UInt, IsDefinitionWrapper<Any, Any, IsPropertyContext, DO>>()
    protected val nameToDefinition =
        mutableMapOf<String, IsDefinitionWrapper<Any, Any, IsPropertyContext, DO>>()

    override fun equals(other: Any?): Boolean =
        other is BaseDataModel<*> && nameToDefinition.keys == other.nameToDefinition.keys

    override fun hashCode(): Int {
        return nameToDefinition.keys.hashCode()
    }

    override val allWithDefaults by lazy {
        _allProperties.filter {
            val def = it.definition
            def is HasDefaultValueDefinition<*> && def.default != null
        }
    }

    // Implementation of Collection
    override val size get() = _allProperties.size

    override fun contains(element: IsDefinitionWrapper<Any, Any, IsPropertyContext, DO>) =
        this._allProperties.contains(element)

    override fun containsAll(elements: Collection<IsDefinitionWrapper<Any, Any, IsPropertyContext, DO>>) =
        this._allProperties.containsAll(elements)

    override fun isEmpty() = this._allProperties.isEmpty()

    override operator fun get(name: String) = nameToDefinition[name]

    override operator fun get(index: UInt) = indexToDefinition[index]

    /** Helper for definition maps for multi types. Add enum/usableInMultiType [pair] to map */
    fun <E : TypeEnum<*>> definitionMap(vararg pair: Pair<E, IsUsableInMultiType<*, IsPropertyContext>>) =
        mapOf(*pair)

    /** Add a single property definition wrapper */
    override fun addSingle(propertyDefinitionWrapper: IsDefinitionWrapper<out Any, *, *, DO>) {
        @Suppress("UNCHECKED_CAST")
        _allProperties.add(propertyDefinitionWrapper as IsDefinitionWrapper<Any, Any, IsPropertyContext, DO>)

        require(propertyDefinitionWrapper.index.toInt() in (0..Short.MAX_VALUE)) { "${propertyDefinitionWrapper.index} for ${propertyDefinitionWrapper.name} is outside range $(0..Short.MAX_VALUE)" }
        require(indexToDefinition[propertyDefinitionWrapper.index] == null) { "Duplicate index ${propertyDefinitionWrapper.index} for ${propertyDefinitionWrapper.name} and ${indexToDefinition[propertyDefinitionWrapper.index]?.name}" }
        indexToDefinition[propertyDefinitionWrapper.index] = propertyDefinitionWrapper

        val addName = { name: String ->
            if (nameToDefinition.containsKey(name)) {
                throw ParseException("Model already has a definition for $name")
            }
            nameToDefinition[name] = propertyDefinitionWrapper
        }

        propertyDefinitionWrapper.name.let(addName)
        propertyDefinitionWrapper.alternativeNames?.forEach(addName)
    }

    /** Get PropertyReference by [referenceName] */
    final override fun getPropertyReferenceByName(
        referenceName: String,
        context: IsPropertyContext?
    ): IsPropertyReference<*, IsPropertyDefinition<*>, *> {
        val names = referenceName.split(".")

        var propertyReference: AnyPropertyReference? = null
        for (name in names) {
            if (name.isEmpty()) {
                throw ParseException("Property reference `$referenceName` contains an empty path segment")
            }
            propertyReference = when (propertyReference) {
                null -> this[name]?.ref(propertyReference)
                is HasEmbeddedPropertyReference<*> -> propertyReference.getEmbedded(name, context)
                else -> throw DefNotFoundException("Illegal $referenceName, ${propertyReference.completeName} does not contain embedded property definitions for $name")
            } ?: throw DefNotFoundException("Property reference «$referenceName» does not exist")
        }

        val resolved = propertyReference ?: throw DefNotFoundException("Property reference «$referenceName» does not exist")

        return wrapListReferenceIfNeeded(resolved)
    }

    /** Get PropertyReference by bytes from [reader] with [length] */
    final override fun getPropertyReferenceByBytes(
        length: Int,
        reader: () -> Byte,
        context: IsPropertyContext?
    ): IsPropertyReference<*, IsPropertyDefinition<*>, *> {
        var readLength = 0

        val lengthReader = {
            readLength++
            reader()
        }

        var propertyReference: AnyPropertyReference? = null
        while (readLength < length) {
            propertyReference = when (propertyReference) {
                null -> {
                    val index = initUIntByVar(lengthReader)
                    this[index]?.ref()
                }
                is HasEmbeddedPropertyReference<*> -> propertyReference.getEmbeddedRef(lengthReader, context)
                else -> throw DefNotFoundException("More property references found on property that cannot have any ")
            } ?: throw DefNotFoundException("Property reference does not exist")
        }

        val resolved = propertyReference ?: throw DefNotFoundException("Property reference does not exist")

        return wrapListReferenceIfNeeded(resolved)
    }

    /** Get PropertyReference by storage bytes from [reader] with [length] */
    final override fun getPropertyReferenceByStorageBytes(
        length: Int,
        reader: () -> Byte,
        context: IsPropertyContext?
    ): IsPropertyReference<*, IsPropertyDefinition<*>, *> {
        var readLength = 0
        val lengthReader = {
            readLength++
            reader()
        }

        val resolved = decodeStorageIndex(lengthReader) { index, referenceType ->
            val propertyReference = this[index]?.ref()
            when {
                propertyReference == null -> throw DefNotFoundException("Property reference does not exist")
                readLength >= length -> wrapListReferenceIfNeeded(propertyReference)
                propertyReference is HasEmbeddedPropertyReference<*> -> propertyReference.getEmbeddedStorageRef(
                    lengthReader,
                    context,
                    referenceType
                ) { readLength >= length }
                else -> throw DefNotFoundException("More property references found on property that cannot have any: $propertyReference")
            }
        }
        return wrapListReferenceIfNeeded(resolved)
    }

    private fun wrapListReferenceIfNeeded(reference: AnyPropertyReference): AnyPropertyReference {
        if (reference.propertyDefinition is IsListDefinition<*, *>) return reference
        if (reference is ListAnyValuePropertyReference<*>) return reference

        return if (reference.unwrap(null).any { it is ListAnyItemReference<*, *> }) {
            @Suppress("UNCHECKED_CAST")
            ListAnyValuePropertyReference(
                reference as IsPropertyReference<Any, IsPropertyDefinition<Any>, *>
            )
        } else {
            reference
        }
    }

    override fun getAllDependencies(dependencySet: MutableList<MarykPrimitive>) {
        for (property in _allProperties) {
            property.definition.getAllDependencies(dependencySet)
        }
    }
}
