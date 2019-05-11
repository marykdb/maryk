package maryk.core.properties

import maryk.core.exceptions.DefNotFoundException
import maryk.core.extensions.bytes.initUIntByVar
import maryk.core.models.IsValuesDataModel
import maryk.core.properties.definitions.IsContextualEncodable
import maryk.core.properties.definitions.IsEmbeddedValuesDefinition
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSerializableFixedBytesEncodable
import maryk.core.properties.definitions.IsSerializableFlexBytesEncodable
import maryk.core.properties.definitions.IsUsableInMultiType
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.definitions.wrapper.ContextualDefinitionWrapper
import maryk.core.properties.definitions.wrapper.EmbeddedValuesDefinitionWrapper
import maryk.core.properties.definitions.wrapper.FixedBytesDefinitionWrapper
import maryk.core.properties.definitions.wrapper.FlexBytesDefinitionWrapper
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.definitions.wrapper.ListDefinitionWrapper
import maryk.core.properties.definitions.wrapper.MapDefinitionWrapper
import maryk.core.properties.definitions.wrapper.MultiTypeDefinitionWrapper
import maryk.core.properties.definitions.wrapper.SetDefinitionWrapper
import maryk.core.properties.enum.TypeEnum
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.HasEmbeddedPropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.decodeStorageIndex
import maryk.core.properties.types.TypedValue
import maryk.core.values.IsValueItems
import maryk.core.values.MutableValueItems
import maryk.core.values.ValueItem
import maryk.lib.exceptions.ParseException

abstract class AbstractPropertyDefinitions<DO : Any> :
    IsPropertyDefinitions,
    Collection<IsDefinitionWrapper<Any, Any, IsPropertyContext, DO>> {
    override fun iterator() = _allProperties.iterator()

    private val _allProperties: MutableList<IsDefinitionWrapper<Any, Any, IsPropertyContext, DO>> =
        mutableListOf()

    protected val indexToDefinition = mutableMapOf<UInt, IsDefinitionWrapper<Any, Any, IsPropertyContext, DO>>()
    protected val nameToDefinition =
        mutableMapOf<String, IsDefinitionWrapper<Any, Any, IsPropertyContext, DO>>()

    // Implementation of Collection
    override val size = _allProperties.size

    override fun contains(element: IsDefinitionWrapper<Any, Any, IsPropertyContext, DO>) =
        this._allProperties.contains(element)

    override fun containsAll(elements: Collection<IsDefinitionWrapper<Any, Any, IsPropertyContext, DO>>) =
        this._allProperties.containsAll(elements)

    override fun isEmpty() = this._allProperties.isEmpty()

    /** Get the definition with a property [name] */
    operator fun get(name: String) = nameToDefinition[name]

    /** Get the definition with a property [index] */
    operator fun get(index: UInt) = indexToDefinition[index]

    /** Converts a list of optional [pairs] to values */
    fun mapNonNulls(vararg pairs: ValueItem?): IsValueItems =
        MutableValueItems().also { items ->
            for (it in pairs) {
                if (it != null) items += it
            }
        }

    /** Helper for definition maps for multi types. Add enum/usableInMultiType [pair] to map */
    fun <E : TypeEnum<*>> definitionMap(vararg pair: Pair<E, IsUsableInMultiType<*, IsPropertyContext>>) =
        mapOf(*pair)

    /** Add flex bytes encodable property [definition] with [name] and [index] */
    fun <T : Any, CX : IsPropertyContext, D : IsSerializableFlexBytesEncodable<T, CX>> add(
        index: UInt,
        name: String,
        definition: D,
        alternativeNames: Set<String>? = null
    ) = FlexBytesDefinitionWrapper<T, T, CX, D, Any>(index, name, definition, alternativeNames).apply {
        addSingle(this)
    }

    /** Add flex bytes encodable property [definition] with [name] and [index] */
    fun <T : Any, CX : IsPropertyContext, D : IsContextualEncodable<T, CX>> add(
        index: UInt,
        name: String,
        definition: D,
        alternativeNames: Set<String>? = null
    ) = ContextualDefinitionWrapper<T, T, CX, D, Any>(index, name, definition, alternativeNames).apply {
        addSingle(this)
    }

    /** Add fixed bytes encodable property [definition] with [name] and [index] */
    fun <T : Any, CX : IsPropertyContext, D : IsSerializableFixedBytesEncodable<T, CX>> add(
        index: UInt,
        name: String,
        definition: D,
        alternativeNames: Set<String>? = null
    ) = FixedBytesDefinitionWrapper<T, T, CX, D, Any>(index, name, definition, alternativeNames).apply {
        addSingle(this)
    }

    /** Add list property [definition] with [name] and [index] */
    fun <T : Any, CX : IsPropertyContext> add(
        index: UInt,
        name: String,
        definition: ListDefinition<T, CX>,
        alternativeNames: Set<String>? = null
    ) = ListDefinitionWrapper<T, T, CX, Any>(index, name, definition, alternativeNames).apply {
        addSingle(this)
    }

    /** Add set property [definition] with [name] and [index] */
    fun <T : Any, CX : IsPropertyContext> add(
        index: UInt,
        name: String,
        definition: SetDefinition<T, CX>,
        alternativeNames: Set<String>? = null
    ) = SetDefinitionWrapper<T, CX, Any>(index, name, definition, alternativeNames).apply {
        addSingle(this)
    }

    /** Add map property [definition] with [name] and [index] */
    fun <K : Any, V : Any, CX : IsPropertyContext> add(
        index: UInt,
        name: String,
        definition: MapDefinition<K, V, CX>,
        alternativeNames: Set<String>? = null
    ) = MapDefinitionWrapper<K, V, Map<K, V>, CX, Any>(index, name, definition, alternativeNames).apply {
        addSingle(this)
    }

    /** Add multi type property [definition] with [name] and [index] */
    fun <E : TypeEnum<T>, T: Any, CX : IsPropertyContext> add(
        index: UInt,
        name: String,
        definition: IsMultiTypeDefinition<E, T, CX>,
        alternativeNames: Set<String>? = null
    ) = MultiTypeDefinitionWrapper<E, T, TypedValue<E, T>, CX, Any>(index, name, definition, alternativeNames).apply {
        addSingle(this)
    }

    /** Add embedded object property [definition] with [name] and [index] */
    fun <DM : IsValuesDataModel<P>, P : PropertyDefinitions, CX : IsPropertyContext> add(
        index: UInt,
        name: String,
        definition: IsEmbeddedValuesDefinition<DM, P, CX>,
        alternativeNames: Set<String>? = null
    ) = EmbeddedValuesDefinitionWrapper(index, name, definition, alternativeNames).apply {
        addSingle(this)
    }

    /** Add a single property definition wrapper */
    fun addSingle(propertyDefinitionWrapper: IsDefinitionWrapper<out Any, *, *, DO>) {
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
            propertyReference = when (propertyReference) {
                null -> this[name]?.ref(propertyReference)
                is HasEmbeddedPropertyReference<*> -> propertyReference.getEmbedded(name, context)
                else -> throw DefNotFoundException("Illegal $referenceName, ${propertyReference.completeName} does not contain embedded property definitions for $name")
            } ?: throw DefNotFoundException("Property reference «$referenceName» does not exist")
        }

        return propertyReference ?: throw DefNotFoundException("Property reference «$referenceName» does not exist")
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

        return propertyReference ?: throw DefNotFoundException("Property reference does not exist")
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

        return decodeStorageIndex(lengthReader) { index, referenceType ->
            val propertyReference = this[index]?.ref()
            when {
                propertyReference == null -> throw DefNotFoundException("Property reference does not exist")
                readLength >= length -> propertyReference
                propertyReference is HasEmbeddedPropertyReference<*> -> propertyReference.getEmbeddedStorageRef(
                    lengthReader,
                    context,
                    referenceType
                ) { readLength >= length }
                else -> throw DefNotFoundException("More property references found on property that cannot have any: $propertyReference")
            }
        }
    }
}
